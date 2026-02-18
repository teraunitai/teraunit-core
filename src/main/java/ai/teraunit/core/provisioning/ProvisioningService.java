package ai.teraunit.core.provisioning;

import ai.teraunit.core.api.LaunchRequest;
import ai.teraunit.core.common.ProviderName;
import ai.teraunit.core.pricing.GpuOffer;
import ai.teraunit.core.security.KeyVaultService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class ProvisioningService {

    private final KeyVaultService vault;
    private final ProviderVerifier verifier;
    private final EgressGuard egressGuard;
    private final CloudExecutor executor;
    private final ReaperService reaper; // PROTOCOL 6: THE EXECUTIONER LINK
    private final RedisTemplate<String, Object> redis;

    public ProvisioningService(KeyVaultService vault,
            ProviderVerifier verifier,
            EgressGuard egressGuard,
            CloudExecutor executor,
            ReaperService reaper,
            RedisTemplate<String, Object> redis) {
        this.vault = vault;
        this.verifier = verifier;
        this.egressGuard = egressGuard;
        this.executor = executor;
        this.reaper = reaper;
        this.redis = redis;
    }

    public String launch(LaunchRequest request) {

        // ---------------------------------------------------------
        // PROTOCOL 12: SOVEREIGNTY SWITCH (GDPR/Compliance)
        // ---------------------------------------------------------
        if (!isSovereigntyCompliant(request.sourceRegion(), request.region())) {
            throw new SecurityException(
                    "SOVEREIGNTY_VIOLATION: Data transfer between EU and Non-EU zones is prohibited.");
        }

        // ---------------------------------------------------------
        // PROTOCOL 5: BUREAUCRACY BYPASS (Quota & Auth)
        // ---------------------------------------------------------
        boolean isVerified = verifier.verify(request, request.apiKey());

        if (!isVerified) {
            return "FAILED: QUOTA_EXCEEDED_OR_AUTH_FAILURE";
        }

        // ---------------------------------------------------------
        // PROTOCOL 10: EGRESS GUARD (Profitability)
        // ---------------------------------------------------------
        double targetPrice = fetchCurrentPrice(request.provider(), request.instanceType());

        // Only check profitability if we have valid cost data
        if (targetPrice > 0 && request.currentGpuHourlyCost() > 0) {
            boolean isProfitable = egressGuard.isSafeToMove(
                    targetPrice,
                    request.currentGpuHourlyCost(),
                    request.datasetSizeGb());

            if (!isProfitable) {
                throw new SecurityException("EGRESS_BLOCK: This move loses money. Stay where you are.");
            }
        }

        // ---------------------------------------------------------
        // EXECUTION & REGISTRATION
        // ---------------------------------------------------------

        // 1. Generate heartbeat identity + token BEFORE launch so we can inject it into
        // boot scripts
        String heartbeatId = UUID.randomUUID().toString();
        String heartbeatToken = ai.teraunit.core.security.TokenUtil.generateToken();
        String heartbeatTokenSha256 = ai.teraunit.core.security.TokenUtil.sha256Hex(heartbeatToken);

        // 2. Execute
        String compositeId = executor.provision(request, request.apiKey(), heartbeatId, heartbeatToken);

        // 3. PROTOCOL 6: REGISTER BIRTH
        String storageKey = vault.encrypt(request.apiKey());

        String[] parts = compositeId.split("::");
        if (parts.length == 2) {
            // Convert "LAMBDA" string to Enum
            ProviderName provider = ProviderName.valueOf(parts[0]);
            String realId = parts[1];

            // Save to DB (bind provider instanceId -> heartbeatId + token hash)
            reaper.registerBirth(realId, heartbeatId, heartbeatTokenSha256, provider, storageKey);
        }
        return "SUCCESS: " + compositeId;
    }

    /**
     * Checks if the Source and Target regions are legally compatible.
     */
    private boolean isSovereigntyCompliant(String source, String target) {
        if (source == null || source.isEmpty())
            return true;
        boolean sourceEu = source.toLowerCase().startsWith("eu-");
        boolean targetEu = target.toLowerCase().startsWith("eu-");
        return sourceEu == targetEu;
    }

    /**
     * Looks up the real-time price from the Scraper Cache.
     */
    @SuppressWarnings("unchecked")
    private double fetchCurrentPrice(ProviderName provider, String instanceType) {
        try {
            List<GpuOffer> offers = (List<GpuOffer>) redis.opsForValue().get("CLEAN_OFFERS:" + provider);
            if (offers == null)
                return 0.0;

            return offers.stream()
                    // Relaxed matching for MVP (contains instead of exact equals)
                    .filter(o -> o.gpuModel().toUpperCase().contains(instanceType.toUpperCase())
                            || instanceType.toUpperCase().contains(o.gpuModel().toUpperCase()))
                    .mapToDouble(GpuOffer::pricePerHour)
                    .min()
                    .orElse(0.0);
        } catch (Exception e) {
            // Log generic error to avoid leaking sensitive context
            System.err.println("[Pricing] Failed to lookup target price.");
            return 0.0;
        }
    }
}
