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
public class RunPodScraper implements GpuProviderScraper {

    private final RestClient restClient;
    private final RedisTemplate<String, Object> redis;
    private final PriceMapper priceMapper;

    @Value("${RUNPOD_API_KEY:EMBEDDED_READ_ONLY}")
    private String apiKey;

    public RunPodScraper(RestClient restClient,
                         RedisTemplate<String, Object> redis,
                         PriceMapper priceMapper) {
        this.restClient = restClient;
        this.redis = redis;
        this.priceMapper = priceMapper;
    }

    @Override
    @Scheduled(fixedRate = 30000)
    public void scrape() {
        try {
            // GraphQL query for community and secure cloud prices
            String query = """
                {
                  gpuTypes {
                    id
                    displayName
                    communityPrice
                    securePrice
                  }
                }
                """;

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("https://api.runpod.io/graphql")
                    .header("Authorization", apiKey)
                    .body(Map.of("query", query))
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                // 1. STANDARDIZE (Map to GpuOffer)
                List<GpuOffer> offers = priceMapper.mapToOffers(ProviderName.RUNPOD, response);

                // 2. PERSIST (Write to Redis Vault)
                if (!offers.isEmpty()) {
                    redis.opsForValue().set("CLEAN_OFFERS:RUNPOD", offers);
                    System.out.println("[TeraUnit-Pulse] RunPod Updated: " + offers.size() + " SKUs online.");
                }
            }
        } catch (Exception e) {
            System.err.println("[TeraUnit-Warn] RunPod Scrape Failed: " + e.getMessage());
        }
    }
}
