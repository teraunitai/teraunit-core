package ai.teraunit.core.api;

import ai.teraunit.core.provisioning.CloudExecutor;
import ai.teraunit.core.repository.InstanceEntity;
import ai.teraunit.core.repository.InstanceRepository;
import ai.teraunit.core.security.ControlAuth;
import ai.teraunit.core.security.KeyVaultService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/v1/instances")
public class InstancesController {

    private final InstanceRepository repository;
    private final CloudExecutor executor;
    private final KeyVaultService vault;
    private final ControlAuth controlAuth;

    public InstancesController(InstanceRepository repository,
            CloudExecutor executor,
            KeyVaultService vault,
            ControlAuth controlAuth) {
        this.repository = repository;
        this.executor = executor;
        this.vault = vault;
        this.controlAuth = controlAuth;
    }

    @GetMapping
    public List<InstanceSummary> listActive(HttpServletRequest request) {
        controlAuth.requireControlToken(request);

        return repository.findActive().stream()
                .map(i -> new InstanceSummary(
                        i.getProvider().name(),
                        i.getInstanceId(),
                        i.getHeartbeatId(),
                        i.getStartTime(),
                        i.getLastHeartbeat(),
                        i.getExpiresAt()))
                .toList();
    }

    @PostMapping("/terminate")
    public String terminate(@RequestBody TerminateRequest body, HttpServletRequest request) {
        controlAuth.requireControlToken(request);

        String heartbeatId = body == null ? null : safeTrim(body.heartbeatId());
        String instanceId = body == null ? null : safeTrim(body.instanceId());

        if ((heartbeatId == null || heartbeatId.isBlank()) && (instanceId == null || instanceId.isBlank())) {
            return "ERROR: heartbeatId OR instanceId REQUIRED";
        }

        InstanceEntity entity = null;
        if (heartbeatId != null && !heartbeatId.isBlank()) {
            entity = repository.findByHeartbeatId(heartbeatId);
        }
        if (entity == null && instanceId != null && !instanceId.isBlank()) {
            entity = repository.findByInstanceId(instanceId);
        }

        if (entity == null) {
            return "NOT_FOUND";
        }
        if (!entity.isActive()) {
            return "ALREADY_INACTIVE";
        }

        try {
            String realKey = vault.decrypt(entity.getEncryptedApiKey());
            executor.terminate(entity.getInstanceId(), entity.getProvider(), realKey);

            entity.kill();
            repository.save(entity);

            return "TERMINATED: " + entity.getProvider().name() + "::" + entity.getInstanceId();
        } catch (Exception e) {
            System.err.println("[TERMINATE-FAIL] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return "ERROR: TERMINATION FAILED (See Console)";
        }
    }

    private static String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    public record TerminateRequest(String heartbeatId, String instanceId) {
    }

    public record InstanceSummary(
            String provider,
            String instanceId,
            String heartbeatId,
            Instant startTime,
            Instant lastHeartbeat,
            Instant expiresAt) {
    }
}
