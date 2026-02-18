package ai.teraunit.core.api;

import ai.teraunit.core.provisioning.ReaperService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/heartbeat")
public class HeartbeatController {

    private final ReaperService reaper;

    // INJECTION: Wire the Reaper, not Redis directly.
    public HeartbeatController(ReaperService reaper) {
        this.reaper = reaper;
    }

    /**
     * PROTOCOL 6: THE PULSE
     * Updates the Immutable Ledger (PostgreSQL).
     */
    @PostMapping
    public void pulse(@RequestBody Map<String, String> payload) {
        String instanceId = payload.get("id");
        if (instanceId == null) return;

        System.out.println("[PULSE] Received from: " + instanceId);

        // UPDATE THE LEDGER OF TRUTH
        reaper.registerHeartbeat(instanceId);
    }
}
