package ai.teraunit.core.provisioning;

import ai.teraunit.core.common.ProviderName;
import ai.teraunit.core.security.KeyVaultService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ai.teraunit.core.repository.InstanceEntity;
import ai.teraunit.core.repository.InstanceRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReaperService {

    private final InstanceRepository repository; // NEW: The Truth
    private final CloudExecutor executor;
    private final KeyVaultService vault;

    public ReaperService(InstanceRepository repository, CloudExecutor executor, KeyVaultService vault) {
        this.repository = repository;
        this.executor = executor;
        this.vault = vault;
    }

    @Scheduled(fixedRate = 60000) // Run every minute
    public void reap() {
        // 1. Define "Dead" (No heartbeat for 5 minutes)
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);

        // 2. SQL Query for Zombies (Efficient)
        List<InstanceEntity> zombies = repository.findZombies(cutoff);

        for (InstanceEntity zombie : zombies) {
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
    }

    // Called by ProvisioningService
    public void registerBirth(String instanceId, ProviderName provider, String encryptedKey) {
        InstanceEntity entity = new InstanceEntity(instanceId, provider, encryptedKey);
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
        repository.save(entity);
    }

    // Called by HeartbeatController
    public void registerHeartbeat(String heartbeatId) {
        InstanceEntity entity = repository.findByHeartbeatId(heartbeatId);
        if (entity != null && entity.isActive()) {
            entity.heartbeat();
            repository.save(entity);
        }
    }
}
