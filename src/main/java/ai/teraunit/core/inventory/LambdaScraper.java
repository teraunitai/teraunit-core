package ai.teraunit.core.inventory;

import ai.teraunit.core.common.ProviderName;
import ai.teraunit.core.pricing.GpuOffer;
import ai.teraunit.core.pricing.PriceMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LambdaScraper implements GpuProviderScraper {

    private final RestClient restClient;
    private final RedisTemplate<String, Object> redis; // Added DB Access
    private final PriceMapper priceMapper; // Added Mapper Access

    @Value("${LAMBDA_API_KEY:missing_key}")
    private String apiKey;

    @Value("${TERA_DEBUG_LAMBDA:false}")
    private boolean debugLambda;

    public LambdaScraper(RestClient restClient,
            RedisTemplate<String, Object> redis,
            PriceMapper priceMapper) {
        this.restClient = restClient;
        this.redis = redis;
        this.priceMapper = priceMapper;
    }

    @Override
    @Scheduled(fixedRate = 60000)
    public void scrape() {
        try {
            String cleanKey = ai.teraunit.core.security.TokenUtil.sanitizeApiKey(apiKey);

            String endpoint = "https://cloud.lambda.ai/api/v1/instance-types";

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + cleanKey)
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                if (debugLambda) {
                    System.out.println("[LAMBDA-DEBUG] " + summarizeLambdaResponse(response));
                }

                // 1. Use the Mapper we just fixed
                List<GpuOffer> offers = priceMapper.mapToOffers(ProviderName.LAMBDA, response);

                if (debugLambda) {
                    System.out.println("[LAMBDA-DEBUG] mappedOffers=" + offers.size());
                }

                // 2. Save DIRECTLY to Redis (Fixing the "Depleted" issue)
                if (!offers.isEmpty()) {
                    redis.opsForValue().set("CLEAN_OFFERS:LAMBDA", offers);
                    System.out.println("[TeraUnit-Pulse] Lambda Updated: " + offers.size() + " units online.");
                }
            }

        } catch (Exception e) {
            System.err.println("[TeraUnit-Warn] Lambda Scrape Retry: " + e.getMessage());
        }
    }

    private static String summarizeLambdaResponse(Map<String, Object> response) {
        try {
            Object dataObj = response.get("data");

            List<?> entries;
            String dataShape;
            if (dataObj instanceof Map<?, ?> dataMap) {
                entries = new ArrayList<>(dataMap.values());
                dataShape = "map";
            } else if (dataObj instanceof List<?> dataList) {
                entries = dataList;
                dataShape = "list";
            } else {
                entries = List.of();
                dataShape = (dataObj == null) ? "null" : dataObj.getClass().getSimpleName();
            }

            int rawEntries = entries.size();
            int withName = 0;
            int withPrice = 0;
            int withRegionsNonEmpty = 0;
            int totalRegions = 0;

            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> itemAny)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) itemAny;

                Object typeObj = item.get("instance_type");
                @SuppressWarnings("unchecked")
                Map<String, Object> typeInfo = (typeObj instanceof Map<?, ?> m) ? (Map<String, Object>) m : item;

                Object nameObj = typeInfo.get("name");
                String name = (nameObj == null) ? null : String.valueOf(nameObj);
                if (name != null && !name.isBlank()) {
                    withName++;
                }

                if (typeInfo.get("price_cents_per_hour") instanceof Number) {
                    withPrice++;
                }

                Object regionsObj = item.get("regions_with_capacity_available");
                if (regionsObj == null) {
                    regionsObj = typeInfo.get("regions_with_capacity_available");
                }

                int regionCount = 0;
                if (regionsObj instanceof List<?> l) {
                    regionCount = l.size();
                } else if (regionsObj instanceof Map<?, ?> m) {
                    regionCount = m.size();
                } else if (regionsObj instanceof String s) {
                    regionCount = s.isBlank() ? 0 : 1;
                }

                if (regionCount > 0) {
                    withRegionsNonEmpty++;
                    totalRegions += regionCount;
                }
            }

            return "dataShape=" + dataShape +
                    " rawEntries=" + rawEntries +
                    " withName=" + withName +
                    " withPrice=" + withPrice +
                    " withRegionsNonEmpty=" + withRegionsNonEmpty +
                    " totalRegions=" + totalRegions;
        } catch (Exception e) {
            String msg = e.getMessage();
            return "summaryFailed=" + ((msg == null) ? e.getClass().getSimpleName() : msg);
        }
    }
}
