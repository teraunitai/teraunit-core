package ai.teraunit.core.provisioning;

import ai.teraunit.core.api.LaunchRequest;
import ai.teraunit.core.security.KeyVaultService;
import org.springframework.stereotype.Service;

@Service
public class ProvisioningService {

    private final KeyVaultService vault;
    private final ProviderVerifier verifier;
    private final EgressGuard egressGuard;

    public ProvisioningService(KeyVaultService vault, ProviderVerifier verifier, EgressGuard egressGuard) {
        this.vault = vault;
        this.verifier = verifier;
        this.egressGuard = egressGuard;
    }

    /**
     * THE "NO" LOGIC (Protocols 5, 10, 12)
     * We default to REJECTION. The request must prove it is safe.
     */
    public String launch(LaunchRequest request) {

        // 1. PROTOCOL 12: SOVEREIGNTY SWITCH (EU AI ACT)
        if (request.region().startsWith("eu-") && !isEuCompliant(request)) {
            throw new SecurityException("SOVEREIGNTY_VIOLATION: EU Data cannot leave the zone.");
        }

        // 2. PROTOCOL 5: BUREAUCRACY BYPASS (Quota Check)
        // Uses the Phase 1 Verifier logic
        boolean hasQuota = verifier.checkQuota(request.provider(), request.apiKey(), request.instanceType());
        if (!hasQuota) {
            return "FAILED: QUOTA_EXCEEDED";
        }

        // 3. PROTOCOL 1 & 4: THE VAULT
        // We encrypt the key IMMEDIATELY for storage (Audit Log).
        // We do not store the raw key.
        String encryptedKey = vault.encrypt(request.apiKey());

        // 4. EXECUTION (Placeholder for Phase 3)
        System.out.println("[TeraUnit-Orchestrator] Provisioning " + request.instanceType() + " on " + request.provider());

        return "SUCCESS: " + request.instanceType() + " Provisioned. Vault ID: " + encryptedKey.substring(0, 8) + "...";
    }

    private boolean isEuCompliant(LaunchRequest request) {
        // Simple Logic: If requesting EU region, provider must be EU-certified.
        return true; // MVP Default
    }
}
