package ai.teraunit.core.api;

import ai.teraunit.core.provisioning.ReaperService;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

@RestController
@RequestMapping("/v1/heartbeat")
public class HeartbeatController {

    private final ReaperService reaper;
    private final ai.teraunit.core.security.HeartbeatAuth heartbeatAuth;

    // INJECTION: Wire the Reaper, not Redis directly.
    public HeartbeatController(ReaperService reaper,
            ai.teraunit.core.security.HeartbeatAuth heartbeatAuth) {
        this.reaper = reaper;
        this.heartbeatAuth = heartbeatAuth;
    }

    /**
     * PROTOCOL 6: THE PULSE
     * Updates the Immutable Ledger (PostgreSQL).
     */
    @PostMapping
    public void pulse(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String heartbeatId = payload.get("id");
        if (heartbeatId == null || heartbeatId.isBlank())
            return;

        heartbeatAuth.requireValidHeartbeat(request, heartbeatId);

        System.out.println("[PULSE] Received from: " + heartbeatId);

        // UPDATE THE LEDGER OF TRUTH (bound to heartbeatId)
        reaper.registerHeartbeat(heartbeatId);
    }
}
