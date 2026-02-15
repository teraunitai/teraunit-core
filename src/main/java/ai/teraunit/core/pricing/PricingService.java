package ai.teraunit.core.pricing;

import ai.teraunit.core.common.GpuPriceScrapedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;

@Service
public class PricingService {
    private final RedisTemplate<String, Object> redis;
    private final PriceMapper mapper;

    public PricingService(RedisTemplate<String, Object> redis, PriceMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Async
    @EventListener
    public void handleScrapedEvent(GpuPriceScrapedEvent event) {
        // FIX: Clean the data BEFORE saving.
        List<GpuOffer> cleanOffers = mapper.mapToOffers(event.provider(), event.rawData());

        String redisKey = "CLEAN_OFFERS:" + event.provider();
        redis.opsForValue().set(redisKey, cleanOffers, Duration.ofSeconds(120));

        System.out.println("[TeraUnit-Pulse] Normalized " + cleanOffers.size() + " offers for " + event.provider());
    }
}
