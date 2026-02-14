package ai.teraunit.core.api;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PricingController {

    private final RedisTemplate<String, Object> redis;

    public PricingController(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    @GetMapping("/v1/pricing")
    public Object getPricing() {
        return redis.opsForValue().get("GPU:PRICE:RAW");
    }
}
