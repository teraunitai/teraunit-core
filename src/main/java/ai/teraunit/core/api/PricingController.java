package ai.teraunit.core.api;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PricingController {

    private final RedisTemplate<String, Object> redis;

    public PricingController(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    @GetMapping("/v1/pricing")
    public Map<String, Object> getPricing() {
        return Map.of(
                "lambda", redis.opsForValue().get("RAW_PRICE:LAMBDA"),
                "runpod", redis.opsForValue().get("RAW_PRICE:RUNPOD"),
                "vast", redis.opsForValue().get("RAW_PRICE:VAST")
        );
    }
}