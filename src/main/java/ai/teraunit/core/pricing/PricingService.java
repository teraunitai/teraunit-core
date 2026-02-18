package ai.teraunit.core.pricing;

import ai.teraunit.core.api.PricingResponse;
import ai.teraunit.core.common.ProviderName;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class PricingService {

    private final RedisTemplate<String, Object> redis;

    public PricingService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    /**
     * THE MARKET READER
     * Reads the "Menu" that the Scrapers have written to Redis.
     */
    @SuppressWarnings("unchecked")
    public PricingResponse getOffers(ProviderName provider) {
        // 1. READ from the Vault (Redis), not RAM.
        String key = "CLEAN_OFFERS:" + provider.name();
        List<GpuOffer> offers = (List<GpuOffer>) redis.opsForValue().get(key);

        if (offers == null) {
            offers = Collections.emptyList();
        }

        // 2. RETURN standardized response
        return new PricingResponse(provider.name(), offers);
    }
}
