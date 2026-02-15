package ai.teraunit.core.api;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
public class PricingController {

    private final RedisTemplate<String, Object> redis;

    public PricingController(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    @GetMapping("/v1/pricing")
    public Map<String, Object> getPricing() {
        Map<String, Object> allPricing = new HashMap<>();
        allPricing.put("lambda", redis.opsForValue().get("CLEAN_OFFERS:LAMBDA"));
        allPricing.put("runpod", redis.opsForValue().get("CLEAN_OFFERS:RUNPOD"));
        allPricing.put("vast", redis.opsForValue().get("CLEAN_OFFERS:VAST"));
        return allPricing;
    }
}
