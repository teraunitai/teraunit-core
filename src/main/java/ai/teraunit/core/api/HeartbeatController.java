package ai.teraunit.core.api;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/v1/heartbeat")
public class HeartbeatController {

    private final RedisTemplate<String, Object> redis;

    public HeartbeatController(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    /**
     * PROTOCOL 6: THE ZOMBIE KILL SWITCH (Receiver)
     * <p>
     * The Agent runs: curl -X POST /heartbeat -d '{"id":"i-123", "status":"alive"}'
     * We store this pulse in Redis with a 300s (5 min) TTL.
     * <p>
     * If the key expires, the "Reaper" (Scheduled Task) assumes the
     * instance is a Zombie and kills it via the Provider API.
     */
    @PostMapping
    public void pulse(@RequestBody Map<String, String> payload) {
        String instanceId = payload.get("id");
        if (instanceId == null) return;

        String key = "HEARTBEAT:" + instanceId;

        // Extend life by 5 minutes
        redis.opsForValue().set(key, "ALIVE", Duration.ofSeconds(300));

        System.out.println("[PULSE] " + instanceId + " is active.");
    }
}
