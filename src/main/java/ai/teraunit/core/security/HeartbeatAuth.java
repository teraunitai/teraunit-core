package ai.teraunit.core.security;

import ai.teraunit.core.repository.InstanceEntity;
import ai.teraunit.core.repository.InstanceRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HeartbeatAuth {

    private final InstanceRepository repository;
    private final boolean allowUnauthenticatedWhenMissingToken;

    public HeartbeatAuth(InstanceRepository repository,
            @Value("${teraunit.heartbeat.allow-unauthenticated:false}") boolean allowUnauthenticatedWhenMissingToken) {
        this.repository = repository;
        this.allowUnauthenticatedWhenMissingToken = allowUnauthenticatedWhenMissingToken;
    }

    public void requireValidHeartbeat(HttpServletRequest request, String heartbeatId) {
        InstanceEntity entity = repository.findByHeartbeatId(heartbeatId);
        if (entity == null || !entity.isActive()) {
            throw new SecurityException("HEARTBEAT_UNKNOWN_INSTANCE");
        }

        String expectedHash = entity.getHeartbeatTokenSha256();
        if (expectedHash == null || expectedHash.isBlank()) {
            if (allowUnauthenticatedWhenMissingToken) {
                return;
            }
            throw new SecurityException("HEARTBEAT_TOKEN_NOT_CONFIGURED");
        }

        String providedToken = request.getHeader("X-Tera-Heartbeat-Token");
        if (providedToken == null || providedToken.isBlank()) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                providedToken = auth.substring(7).trim();
            }
        }

        String providedHash = TokenUtil.sha256Hex((providedToken == null) ? "" : providedToken.trim());
        if (!TokenUtil.constantTimeEquals(expectedHash, providedHash)) {
            throw new SecurityException("HEARTBEAT_TOKEN_INVALID");
        }
    }
}
