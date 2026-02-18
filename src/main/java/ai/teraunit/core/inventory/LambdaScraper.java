package ai.teraunit.core.inventory;

import ai.teraunit.core.common.ProviderName;
import ai.teraunit.core.pricing.GpuOffer;
import ai.teraunit.core.pricing.PriceMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class LambdaScraper implements GpuProviderScraper {

    private final RestClient restClient;
    private final RedisTemplate<String, Object> redis; // Added DB Access
    private final PriceMapper priceMapper; // Added Mapper Access

    @Value("${LAMBDA_API_KEY:missing_key}")
    private String apiKey;

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
                // 1. Use the Mapper we just fixed
                List<GpuOffer> offers = priceMapper.mapToOffers(ProviderName.LAMBDA, response);

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
}
