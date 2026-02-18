package ai.teraunit.core.api;

import ai.teraunit.core.provisioning.ProvisioningService;
import ai.teraunit.core.provisioning.VelocityFuse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/launch")
public class LaunchController {

    private final ProvisioningService service;
    private final VelocityFuse fuse;

    public LaunchController(ProvisioningService service, VelocityFuse fuse) {
        this.service = service;
        this.fuse = fuse;
    }

    @PostMapping
    public String launchInstance(@RequestBody LaunchRequest request, HttpServletRequest servletRequest) {
        try {
            // 1. PROTOCOL 13: VELOCITY FUSE
            String ip = servletRequest.getRemoteAddr();
            fuse.check(ip);

            // 2. EXECUTE
            return service.launch(request);

        } catch (SecurityException e) {
            // LOGIC ENGINE BLOCKS (Egress, Sovereignty, Fuse)
            return "BLOCKED: " + e.getMessage();

        } catch (Exception e) {
            // PROVIDER ERROR TRANSLATION LAYER
            String msg = e.getMessage().toLowerCase();

            // Map raw API errors to Human Readable text
            if (msg.contains("balance") || msg.contains("funds") || msg.contains("credit")) {
                return "FAILED: INSUFFICIENT FUNDS (Check Provider Account)";
            }
            if (msg.contains("quota") || msg.contains("limit") || msg.contains("capacity")) {
                return "FAILED: PROVIDER QUOTA EXCEEDED";
            }
            if (msg.contains("unauthorized") || msg.contains("401") || msg.contains("403") || msg.contains("auth")) {
                return "FAILED: INVALID API KEY";
            }
            if (msg.contains("unavailable") || msg.contains("stock") || msg.contains("sold out")) {
                return "FAILED: INSTANCE NO LONGER AVAILABLE";
            }

            // Fallback: Log the full nasty error to Console, show simplified version to User
            System.err.println("[LAUNCH-FAIL] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return "ERROR: PROVIDER REJECTED REQUEST (See Console)";
        }
    }
}
