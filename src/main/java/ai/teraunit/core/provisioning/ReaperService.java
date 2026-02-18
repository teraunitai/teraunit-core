package ai.teraunit.core.provisioning;

import ai.teraunit.core.common.ProviderName;
import ai.teraunit.core.security.KeyVaultService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ai.teraunit.core.repository.InstanceEntity;
import ai.teraunit.core.repository.InstanceRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReaperService {

    private final InstanceRepository repository; // NEW: The Truth
    private final CloudExecutor executor;
    private final KeyVaultService vault;

    // Hard stop lease to prevent accidental long-running spend.
    // Set to 0 to disable.
    private final long maxRuntimeMinutes;

    public ReaperService(InstanceRepository repository,
            CloudExecutor executor,
            KeyVaultService vault,
            @Value("${teraunit.instance.max-runtime-minutes:0}") long maxRuntimeMinutes) {
        this.repository = repository;
        this.executor = executor;
        this.vault = vault;
        this.maxRuntimeMinutes = maxRuntimeMinutes;
    }

    @Scheduled(fixedRate = 60000) // Run every minute
    public void reap() {
        // 1. Define "Dead" (No heartbeat for 5 minutes)
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);

        // 1b. Define "Expired" (Past max runtime lease)
        Instant now = Instant.now();

        // 2. SQL Query for Zombies (Efficient)
        List<InstanceEntity> zombies = repository.findZombies(cutoff);

        // 2b. SQL Query for Expired leases
        List<InstanceEntity> expired = repository.findExpired(now);

        Set<String> processed = new HashSet<>();

        for (InstanceEntity zombie : zombies) {
            processed.add(zombie.getInstanceId());
            System.out.println("💀 ZOMBIE DETECTED: " + zombie.getInstanceId());
            try {
                // 3. Decrypt the Key
                String realKey = vault.decrypt(zombie.getEncryptedApiKey());

                // 4. Execute Kill Order
                executor.terminate(zombie.getInstanceId(), zombie.getProvider(), realKey);

                // 5. Mark Dead in Ledger
                zombie.kill();
                repository.save(zombie);

                System.out.println("💀 RIP: " + zombie.getInstanceId() + " terminated.");
            } catch (Exception e) {
                System.err.println("FAILED TO REAP " + zombie.getInstanceId() + ": " + e.getMessage());
            }
        }

        for (InstanceEntity entity : expired) {
            if (processed.contains(entity.getInstanceId())) {
                continue;
            }

            System.out.println("⏳ LEASE EXPIRED: " + entity.getInstanceId());
            try {
                String realKey = vault.decrypt(entity.getEncryptedApiKey());
                executor.terminate(entity.getInstanceId(), entity.getProvider(), realKey);

                entity.kill();
                repository.save(entity);

                System.out.println("💀 RIP: " + entity.getInstanceId() + " terminated (lease expired).");
            } catch (Exception e) {
                System.err.println("FAILED TO REAP " + entity.getInstanceId() + " (lease expired): " + e.getMessage());
            }
        }
    }

    private Instant computeExpiresAt(Instant startTime) {
        if (maxRuntimeMinutes <= 0) {
            return null;
        }
        Instant base = startTime != null ? startTime : Instant.now();
        return base.plus(maxRuntimeMinutes, ChronoUnit.MINUTES);
    }

    // Called by ProvisioningService
    public void registerBirth(String instanceId, ProviderName provider, String encryptedKey) {
        InstanceEntity entity = new InstanceEntity(instanceId, provider, encryptedKey);
        entity.setExpiresAt(computeExpiresAt(entity.getStartTime()));
        repository.save(entity);
    }

    // Phase-5: bind heartbeats to a stable heartbeatId + token hash
    public void registerBirth(String instanceId,
            String heartbeatId,
            String heartbeatTokenSha256,
            ProviderName provider,
            String encryptedKey) {
        InstanceEntity entity = new InstanceEntity(instanceId, heartbeatId, heartbeatTokenSha256, provider,
                encryptedKey);
        entity.setExpiresAt(computeExpiresAt(entity.getStartTime()));
        repository.save(entity);
    }

    // Called by HeartbeatController
    public void registerHeartbeat(String heartbeatId) {
        InstanceEntity entity = repository.findByHeartbeatId(heartbeatId);
        if (entity != null && entity.isActive()) {
            if (entity.getExpiresAt() == null) {
                entity.setExpiresAt(computeExpiresAt(entity.getStartTime()));
            }
            entity.heartbeat();
            repository.save(entity);
        }
    }
}
