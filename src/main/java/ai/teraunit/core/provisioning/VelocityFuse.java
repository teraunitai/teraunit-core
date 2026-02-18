package ai.teraunit.core.provisioning;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class VelocityFuse {

    private final RedisTemplate<String, Object> redis;

    // STRICT LIMIT: 5 Instances per Hour for the public
    private static final int HOURLY_LAUNCH_LIMIT = 5;

    // PRODUCTION WHITELIST: Localhost + Your Office IP (Optional)
    private static final List<String> ADMIN_IPS = List.of("127.0.0.1", "0:0:0:0:0:0:0:1");

    public VelocityFuse(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public void check(String clientIp) {
        if (clientIp == null) clientIp = "UNKNOWN";

        // 1. BYPASS: Admins are immune to the fuse
        if (ADMIN_IPS.contains(clientIp)) {
            System.out.println("[FUSE] Admin Bypass for: " + clientIp);
            return;
        }

        // 2. CHECK: Public Traffic
        String key = "RATE_LIMIT:" + clientIp;
        Integer count = (Integer) redis.opsForValue().get(key);

        if (count == null) {
            redis.opsForValue().set(key, 1, Duration.ofHours(1));
        } else {
            if (count >= HOURLY_LAUNCH_LIMIT) {
                throw new SecurityException("VELOCITY_FUSE_TRIPPED: Limit " + HOURLY_LAUNCH_LIMIT + "/hr exceeded.");
            }
            redis.opsForValue().increment(key);
        }
    }
}
