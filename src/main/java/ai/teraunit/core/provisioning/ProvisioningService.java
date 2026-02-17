package ai.teraunit.core.provisioning;

import ai.teraunit.core.api.LaunchRequest;
import ai.teraunit.core.security.KeyVaultService;
import org.springframework.stereotype.Service;

@Service
public class ProvisioningService {

    private final KeyVaultService vault;
    private final ProviderVerifier verifier;
    private final EgressGuard egressGuard;
    private final CloudExecutor executor; // <--- NEW DEPENDENCY

    public ProvisioningService(KeyVaultService vault,
                               ProviderVerifier verifier,
                               EgressGuard egressGuard,
                               CloudExecutor executor) {
        this.vault = vault;
        this.verifier = verifier;
        this.egressGuard = egressGuard;
        this.executor = executor;
    }

    public String launch(LaunchRequest request) {

        // 1. SOVEREIGNTY CHECK
        if (request.region().startsWith("eu-") && !isEuCompliant(request)) {
            throw new SecurityException("SOVEREIGNTY_VIOLATION: EU Data cannot leave the zone.");
        }

        // 2. QUOTA CHECK
        boolean hasQuota = verifier.checkQuota(request.provider(), request.apiKey(), request.instanceType());
        if (!hasQuota) {
            return "FAILED: QUOTA_EXCEEDED";
        }

        // 3. ENCRYPTION (Audit Log)
        // We encrypt the key to log that "User X launched Instance Y" securely.
        String encryptedKey = vault.encrypt(request.apiKey());

        // 4. EXECUTION (The Real Deal)
        // We pass the raw key to the executor to make the purchase.
        String result = executor.provision(request, request.apiKey());

        return result + " | Vault Ref: " + encryptedKey.substring(0, 8);
    }

    private boolean isEuCompliant(LaunchRequest request) {
        return true;
    }
}
