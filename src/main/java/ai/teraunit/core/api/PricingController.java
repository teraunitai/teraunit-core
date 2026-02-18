package ai.teraunit.core.api;

import ai.teraunit.core.common.ProviderName;
import ai.teraunit.core.pricing.GpuOffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/v1/pricing")
public class PricingController {

    private final RedisTemplate<String, Object> redis;

    /**
     * MEMORY BUFFER: Prevents UI flicker during scraper updates.
     * Acts as a shock absorber for transient API/Redis failures.
     */
    private final Map<ProviderName, CachedOffers> lastKnownGood = new ConcurrentHashMap<>();

    // Safety Limit: If data is older than 5 mins, admit defeat and show empty.
    private static final Duration MAX_STALE = Duration.ofMinutes(5);

    public PricingController(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    @GetMapping
    public Map<String, List<GpuOffer>> getGlobalIndex() {
        Map<String, List<GpuOffer>> index = new HashMap<>();

        // Fetch all providers with fallback protection
        index.put("lambda", safeGet(ProviderName.LAMBDA));
        index.put("runpod", safeGet(ProviderName.RUNPOD));
        index.put("vast", safeGet(ProviderName.VAST));

        return index;
    }

    @SuppressWarnings("unchecked")
    private List<GpuOffer> safeGet(ProviderName provider) {
        try {
            String key = "CLEAN_OFFERS:" + provider.name();
            Object value = redis.opsForValue().get(key);

            // 1. If Redis is empty, trigger Fallback immediately
            if (value == null) {
                return fallback(provider);
            }

            List<GpuOffer> offers = (List<GpuOffer>) value;

            // 2. If valid data found, update the "Last Known Good" cache
            // We don't cache empty lists to prevent overwriting good data with a bad scrape
            if (offers != null && !offers.isEmpty()) {
                lastKnownGood.put(provider, new CachedOffers(List.copyOf(offers), Instant.now()));
            }

            // 3. Return live data if available, otherwise fallback
            return (offers != null && !offers.isEmpty()) ? offers : fallback(provider);

        } catch (Exception e) {
            // 4. On any Redis/Network error, suppress exception and use cache
            System.err.println("[Pricing] Read Error for " + provider + ": " + e.getMessage());
            return fallback(provider);
        }
    }

    private List<GpuOffer> fallback(ProviderName provider) {
        CachedOffers cached = lastKnownGood.get(provider);

        // If no cache, or cache is too old -> Return Empty (Truth)
        if (cached == null || Duration.between(cached.at(), Instant.now()).compareTo(MAX_STALE) > 0) {
            return Collections.emptyList();
        }

        // Return Stale Data (Stability)
        return cached.offers();
    }

    // Immutable Cache Wrapper
    private record CachedOffers(List<GpuOffer> offers, Instant at) {}
}
