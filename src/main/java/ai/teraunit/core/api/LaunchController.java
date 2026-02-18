package ai.teraunit.core.api;

import ai.teraunit.core.provisioning.ProvisioningService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/launch")
public class LaunchController {

    private final ProvisioningService service;
    private final ai.teraunit.core.security.ControlAuth controlAuth;

    public LaunchController(ProvisioningService service,
            ai.teraunit.core.security.ControlAuth controlAuth) {
        this.service = service;
        this.controlAuth = controlAuth;
    }

    @PostMapping
    public String launchInstance(@RequestBody LaunchRequest request, HttpServletRequest servletRequest) {
        try {
            // TRUST BOUNDARY: Control-plane token required
            controlAuth.requireControlToken(servletRequest);

            // EXECUTE
            return service.launch(request);

        } catch (SecurityException e) {
            // LOGIC ENGINE BLOCKS (Egress, Sovereignty, Fuse)
            return "BLOCKED: " + e.getMessage();

        } catch (Exception e) {
            // PROVIDER ERROR TRANSLATION LAYER
            String msg = e.getMessage().toLowerCase();

            // --- 1. FUNDS ---
            if (msg.contains("balance") || msg.contains("funds") || msg.contains("credit")) {
                return "FAILED: INSUFFICIENT FUNDS (Check Provider Account)";
            }
            // --- 2. QUOTA ---
            if (msg.contains("quota") || msg.contains("limit") || msg.contains("capacity")) {
                return "FAILED: PROVIDER QUOTA EXCEEDED";
            }
            // --- 3. AUTH ---
            if (msg.contains("unauthorized") || msg.contains("401") || msg.contains("403") || msg.contains("auth")) {
                return "FAILED: INVALID API KEY";
            }
            // --- 4. INVENTORY (The Fix for RunPod) ---
            // Catches "SUPPLY_CONSTRAINT", "no longer any instances", "sold out",
            // "unavailable"
            if (msg.contains("supply_constraint") ||
                    msg.contains("no longer") ||
                    msg.contains("unavailable") ||
                    msg.contains("stock") ||
                    msg.contains("sold out")) {
                return "FAILED: SOLD OUT (Race Condition)";
            }

            // Fallback: Log the full nasty error to Console, show simplified version to
            // User
            System.err.println("[LAUNCH-FAIL] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return "ERROR: PROVIDER REJECTED REQUEST (See Console)";
        }
    }
}
