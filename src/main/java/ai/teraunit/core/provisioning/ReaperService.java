package ai.teraunit.core.provisioning;

import ai.teraunit.core.common.ProviderName;
import ai.teraunit.core.security.KeyVaultService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ReaperService {

    private final RedisTemplate<String, Object> redis;
    private final CloudExecutor executor;
    private final KeyVaultService vault;

    // LEDGER KEY: Stores "PROVIDER::INSTANCE_ID::ENCRYPTED_KEY_REF"
    private static final String LEDGER = "ACTIVE_DEPLOYMENTS";

    public ReaperService(RedisTemplate<String, Object> redis, CloudExecutor executor, KeyVaultService vault) {
        this.redis = redis;
        this.executor = executor;
        this.vault = vault;
    }

    /**
     * THE GRIM REAPER
     * Runs every 60 seconds.
     * Checks if any active deployment has lost its heartbeat for > 5 mins.
     */
    @Scheduled(fixedRate = 60000)
    public void reap() {
        Set<Object> deployments = redis.opsForSet().members(LEDGER);
        if (deployments == null) return;

        for (Object obj : deployments) {
            String record = (String) obj;
            // Format: PROVIDER::INSTANCE_ID::ENCRYPTED_KEY
            String[] parts = record.split("::");

            if (parts.length != 3) continue;

            String providerStr = parts[0];
            String instanceId = parts[1];
            String encryptedKey = parts[2];

            // Check Heartbeat
            String heartbeatKey = "HEARTBEAT:" + instanceId;
            if (!Boolean.TRUE.equals(redis.hasKey(heartbeatKey))) {
                System.out.println("💀 ZOMBIE DETECTED: " + instanceId + ". KILLING...");

                try {
                    // 1. Kill on Cloud
                    ProviderName provider = ProviderName.valueOf(providerStr);
                    String realKey = vault.decrypt(encryptedKey);
                    executor.terminate(instanceId, provider, realKey);

                    // 2. Remove from Ledger
                    redis.opsForSet().remove(LEDGER, record);
                    System.out.println("💀 RIP: " + instanceId + " removed from ledger.");

                } catch (Exception e) {
                    System.err.println("FAILED TO REAP " + instanceId + ": " + e.getMessage());
                }
            }
        }
    }

    // Helper to register new births
    public void registerBirth(String compositeId, String provider, String encryptedKey) {
        // compositeId comes from Executor: "LAMBDA::12345"
        // We need to parse it.
        String[] parts = compositeId.split("::");
        String realId = parts[1];

        String record = provider + "::" + realId + "::" + encryptedKey;
        redis.opsForSet().add(LEDGER, record);

        // Seed the first heartbeat immediately so it doesn't die instantly
        redis.opsForValue().set("HEARTBEAT:" + realId, "BORN", java.time.Duration.ofSeconds(300));
    }
}
