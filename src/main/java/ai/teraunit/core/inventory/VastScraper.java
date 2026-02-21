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

            // Vast appears to return a fixed-size slice (often 64) with no pagination
            // metadata.
            // Drive pagination from the request side (limit/offset) and stop safely if the
            // API ignores it.
            final int limit = 256;
            final int maxPages = 10;

            if (debugVast) {
                System.out.println("[VAST-DEBUG] paginationConfig={limit=" + limit + ", maxPages=" + maxPages + "}");
            }

            Map<String, GpuOffer> offersById = new LinkedHashMap<>();
            int offset = 0;
            int pagesFetched = 0;
            for (int page = 0; page < maxPages; page++) {
                Map<String, Object> query = buildQuery();
                String pagedEndpoint = endpoint + "?limit=" + limit + "&offset=" + offset;

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restClient.post()
                    .uri(pagedEndpoint)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .body(query)
                        .retrieve()
                        .body(Map.class);

                if (response == null) {
                    break;
                }

                if (debugVast && page == 0) {
                    debugPrintResponseMeta(response);
                }

                pagesFetched++;

                int before = offersById.size();
                List<GpuOffer> pageOffers = priceMapper.mapToOffers(ProviderName.VAST, response);
                for (GpuOffer offer : pageOffers) {
                    if (offer == null || offer.launchId() == null || offer.launchId().isBlank()) {
                        continue;
                    }
                    offersById.putIfAbsent(offer.launchId(), offer);
                }

                Integer responseOffersCount = getOffersCount(response);
                int added = offersById.size() - before;

                // Stop conditions:
                // - API returns no offers
                // - API is ignoring limit/offset (no new IDs added)
                // - API returned less than a full page (likely last page)
                if (responseOffersCount == null || responseOffersCount == 0) {
                    break;
                }
                if (added == 0) {
                    break;
                }
                if (responseOffersCount < limit) {
                    break;
                }

                offset += limit;
            }

            if (debugVast) {
                System.out.println("[VAST-DEBUG] paginationSummary={pagesFetched=" + pagesFetched + ", limit=" + limit
                        + ", maxPages=" + maxPages + ", uniqueOffers=" + offersById.size() + "}");
            }

            List<GpuOffer> offers = new ArrayList<>(offersById.values());

            if (!offers.isEmpty()) {
                redis.opsForValue().set("CLEAN_OFFERS:VAST", offers);
                System.out.println("[TeraUnit-Pulse] Vast Updated: " + offers.size() + " units online.");
            }
        } catch (Exception e) {
            System.err.println("[TeraUnit-Warn] Vast Scrape Failed: " + e.getMessage());
        }
    }

    private static Map<String, Object> buildQuery() {
        LinkedHashMap<String, Object> query = new LinkedHashMap<>();

        // Filter: Verified hosts, On-Demand (Rentable), Available
        query.put("verified", Map.of("eq", true));
        query.put("type", "on-demand");
        query.put("rentable", Map.of("eq", true));

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
