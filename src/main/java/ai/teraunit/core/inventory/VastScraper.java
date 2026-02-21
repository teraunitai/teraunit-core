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
import java.util.Set;
import java.util.TreeSet;

@Component
public class VastScraper implements GpuProviderScraper {

    private final RestClient restClient;
    private final RedisTemplate<String, Object> redis; // CHANGED: Redis instead of Publisher
    private final PriceMapper priceMapper;

    @Value("${VAST_API_KEY:missing_key}")
    private String apiKey;

    @Value("${TERA_DEBUG_VAST:false}")
    private boolean debugVast;

    public VastScraper(RestClient restClient,
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
            String endpoint = "https://console.vast.ai/api/v0/bundles/";

            // Filter: Verified hosts, On-Demand (Rentable), Available
            Map<String, Object> query = Map.of(
                    "verified", Map.of("eq", true),
                    "type", "on-demand",
                    "rentable", Map.of("eq", true)
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(query)
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                if (debugVast) {
                    debugPrintResponseMeta(response);
                }

                // 1. STANDARDIZE
                List<GpuOffer> offers = priceMapper.mapToOffers(ProviderName.VAST, response);

                // 2. PERSIST (The Missing Link)
                if (!offers.isEmpty()) {
                    redis.opsForValue().set("CLEAN_OFFERS:VAST", offers);
                    System.out.println("[TeraUnit-Pulse] Vast Updated: " + offers.size() + " units online.");
                }
            }
        } catch (Exception e) {
            System.err.println("[TeraUnit-Warn] Vast Scrape Failed: " + e.getMessage());
        }
    }

    private static void debugPrintResponseMeta(Map<String, Object> response) {
        try {
            Set<String> keys = new TreeSet<>();
            keys.addAll(response.keySet());

            Object offersObj = response.get("offers");
            Integer offersCount = null;
            if (offersObj instanceof List<?> l) {
                offersCount = l.size();
            }

            Map<String, Object> pagination = Map.of(
                    "total", response.get("total"),
                    "count", response.get("count"),
                    "limit", response.get("limit"),
                    "offset", response.get("offset"),
                    "next", response.get("next"),
                    "previous", response.get("previous"),
                    "page", response.get("page"),
                    "page_size", response.get("page_size"));

            System.out.println("[VAST-DEBUG] keys=" + keys +
                    " offersType=" + (offersObj == null ? "null" : offersObj.getClass().getSimpleName()) +
                    " offersCount=" + offersCount +
                    " pagination=" + pagination);
        } catch (Exception e) {
            System.err.println("[VAST-DEBUG] meta logging failed: " + e.getMessage());
        }
    }
}
