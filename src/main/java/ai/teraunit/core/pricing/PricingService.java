package ai.teraunit.core.pricing;

import ai.teraunit.core.common.GpuPriceScrapedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class PricingService {

    private final RedisTemplate<String, Object> redis;

    public PricingService(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    @Async // Runs on a Virtual Thread, keeping the Scraper free to ping other APIs
    @EventListener
    public void handleScrapedEvent(GpuPriceScrapedEvent event) {
        // Phase 1: Verify the pulse
        String redisKey = "RAW_PRICE:" + event.provider();
        redis.opsForValue().set(redisKey, event.rawData(), Duration.ofSeconds(70));

        System.out.println("[TeraUnit-Pulse] Updated Redis for " + event.provider());
    }
}
