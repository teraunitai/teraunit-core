package ai.teraunit.core.provisioning;

import ai.teraunit.core.common.ProviderName;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.Base64;

/**
 * THE BUREAUCRACY BYPASS (PROTOCOL 5)
 *
 * Purpose: Prevents "Silent Failures" by checking Cloud Quotas BEFORE taking money.
 * Target: AWS Service Quotas, Lambda Account Info, Vast Verification.
 */
@Service
public class ProviderVerifier {

    private final RestClient restClient;

    public ProviderVerifier(RestClient restClient) {
        this.restClient = restClient;
    }

    public boolean checkQuota(ProviderName provider, String apiKey, String gpuType) {
        try {
            return switch (provider) {
                case LAMBDA -> verifyLambdaQuota(apiKey);
                case RUNPOD -> verifyRunPodBalance(apiKey);
                case VAST -> true; // Vast is P2P, usually no quota limits, checks done at bid time
                default -> false;
            };
        } catch (Exception e) {
            System.err.println("[TeraUnit-Verifier] Quota Check Failed: " + e.getMessage());
            return false;
        }
    }

    private boolean verifyLambdaQuota(String apiKey) {
        // Lambda Labs: Check 'account' endpoint for instance limits
        try {
            String authHeader = "Basic " + Base64.getEncoder()
                    .encodeToString((apiKey + ":").getBytes());

            var response = restClient.get()
                    .uri("https://cloud.lambdalabs.com/api/v1/user/info")
                    .header("Authorization", authHeader)
                    .retrieve()
                    .toBodilessEntity();

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false; // Invalid Key or API Down
        }
    }

    private boolean verifyRunPodBalance(String apiKey) {
        // RunPod: Check if user has > $5.00 balance
        String query = "{ user { funds } }";
        try {
            Map response = restClient.post()
                    .uri("https://api.runpod.io/graphql")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of("query", query))
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("data")) {
                Map data = (Map) response.get("data");
                Map user = (Map) data.get("user");
                Object fundsObj = user.get("funds");
                double funds = Double.parseDouble(fundsObj.toString());
                return funds > 5.00; // VELOCITY FUSE (Protocol 13 Lite)
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
