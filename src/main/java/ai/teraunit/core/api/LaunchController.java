package ai.teraunit.core.api;

import ai.teraunit.core.provisioning.ProvisioningService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/launch")
public class LaunchController {

    private final ProvisioningService service;

    public LaunchController(ProvisioningService service) {
        this.service = service;
    }

    @PostMapping
    public String launchInstance(@RequestBody LaunchRequest request) {
        try {
            // This triggers the entire chain:
            // Controller -> ProvisioningService -> Verifier -> Vault -> CloudExecutor
            return service.launch(request);
        } catch (SecurityException e) {
            return "BLOCKED: " + e.getMessage(); // Sovereignty/Safety Block
        } catch (Exception e) {
            return "ERROR: " + e.getMessage(); // API Failure
        }
    }
}
