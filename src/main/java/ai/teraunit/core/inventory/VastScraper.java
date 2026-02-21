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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
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

            // Vast's bundles endpoint appears to return a fixed-size slice (often 64) and
            // does not accept an `offset` parameter for pagination (sending it yields 400:
            // "oplist for key offset is not a valid dict").
            // Keep the scraper stable: make a single request, and optionally request a
            // larger `limit` in the JSON body (Vast will cap as needed).
            final int requestedLimit = 256;

            if (debugVast) {
                System.out.println("[VAST-DEBUG] requestConfig={requestedLimit=" + requestedLimit + "}");
            }

            Map<String, Object> query = buildQuery(requestedLimit);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(query)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return;
            }

            if (debugVast) {
                debugPrintResponseMeta(response);
            }

            List<GpuOffer> offers = priceMapper.mapToOffers(ProviderName.VAST, response);

            if (debugVast) {
                Integer offersCount = getOffersCount(response);
                System.out.println("[VAST-DEBUG] mappedOffers=" + (offers == null ? 0 : offers.size())
                        + " responseOffersCount=" + offersCount);
            }

            if (!offers.isEmpty()) {
                redis.opsForValue().set("CLEAN_OFFERS:VAST", offers);
                System.out.println("[TeraUnit-Pulse] Vast Updated: " + offers.size() + " units online.");
            }
        } catch (Exception e) {
            System.err.println("[TeraUnit-Warn] Vast Scrape Failed: " + e.getMessage());
        }
    }

    private static Map<String, Object> buildQuery(int requestedLimit) {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();

        // Filter: Verified hosts, On-Demand (Rentable), Available
        query.put("verified", Map.of("eq", true));
        query.put("type", "on-demand");
        query.put("rentable", Map.of("eq", true));

        // Vast CLI uses a `limit` key in the request body for search-like endpoints.
        // Empirically, Vast still caps the returned array size (often 64), but including
        // this key is safe and may allow higher counts if/when the API supports it.
        query.put("limit", requestedLimit);

        return query;
    }

    private static Integer getOffersCount(Map<String, Object> response) {
        Object offersObj = response.get("offers");
        if (offersObj instanceof List<?> l) {
            return l.size();
        }
        return null;
    }

    private static void debugPrintResponseMeta(Map<String, Object> response) {
        try {
            Set<String> keys = new TreeSet<>();
            for (Object k : new LinkedHashSet<>(response.keySet())) {
                if (k != null) {
                    keys.add(String.valueOf(k));
                }
            }

            Object offersObj = response.get("offers");
            Integer offersCount = null;
            if (offersObj instanceof List<?> l) {
                offersCount = l.size();
            }

            Map<String, Object> pagination = new LinkedHashMap<>();
            pagination.put("total", response.get("total"));
            pagination.put("count", response.get("count"));
            pagination.put("limit", response.get("limit"));
            pagination.put("offset", response.get("offset"));
            pagination.put("next", response.get("next"));
            pagination.put("previous", response.get("previous"));
            pagination.put("page", response.get("page"));
            pagination.put("page_size", response.get("page_size"));

            Object paginationObj = response.get("pagination");
            Object metaObj = response.get("meta");
            String paginationKeys = (paginationObj instanceof Map<?, ?> pm)
                    ? String.valueOf(
                            new TreeSet<>(pm.keySet().stream().filter(k -> k != null).map(String::valueOf).toList()))
                    : null;
            String metaKeys = (metaObj instanceof Map<?, ?> mm)
                    ? String.valueOf(
                            new TreeSet<>(mm.keySet().stream().filter(k -> k != null).map(String::valueOf).toList()))
                    : null;

            System.out.println("[VAST-DEBUG] keys=" + keys +
                    " offersType=" + (offersObj == null ? "null" : offersObj.getClass().getSimpleName()) +
                    " offersCount=" + offersCount +
                    " pagination=" + pagination +
                    " paginationObjKeys=" + paginationKeys +
                    " metaObjKeys=" + metaKeys);
        } catch (Exception e) {
            String msg = e.getMessage();
            System.err
                    .println("[VAST-DEBUG] meta logging failed: " + (msg == null ? e.getClass().getSimpleName() : msg));
        }
    }
}
