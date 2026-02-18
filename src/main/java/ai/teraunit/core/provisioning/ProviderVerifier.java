package ai.teraunit.core.provisioning;

import ai.teraunit.core.api.LaunchRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

@Service
public class ProviderVerifier {

    private final RestClient restClient;

    public ProviderVerifier(RestClient restClient) {
        this.restClient = restClient;
    }

    public boolean verify(LaunchRequest request, String decryptedKey) {
        // SECURITY PATCH: NEVER log the full key. Only log the last 4 chars or a hash.
        String mask = (decryptedKey != null && decryptedKey.length() > 4)
                ? "..." + decryptedKey.substring(decryptedKey.length() - 4)
                : "INVALID";

        System.out.println(">> VERIFYING: " + request.provider() + " | KeyID: " + mask);

        try {
            return switch (request.provider()) {
                case LAMBDA -> verifyLambda(decryptedKey);
                case RUNPOD -> verifyRunPod(decryptedKey);
                case VAST -> verifyVast(decryptedKey);
            };
        } catch (Exception e) {
            System.err.println("!! VERIFICATION FAILED !!");
            System.err.println("Provider: " + request.provider());
            // SECURITY PATCH: Do not print e.getMessage() if it might contain the key from the URL/Header dump
            System.err.println("Error Type: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean verifyLambda(String key) {
        String auth = "Basic " + Base64.getEncoder().encodeToString((key + ":").getBytes());
        restClient.get()
                .uri("https://cloud.lambdalabs.com/api/v1/user/info")
                .header("Authorization", auth)
                .retrieve()
                .toBodilessEntity();
        return true;
    }

    private boolean verifyRunPod(String key) {
        String query = "{ myself { id } }";
        Map response = restClient.post()
                .uri("https://api.runpod.io/graphql")
                .header("Authorization", key) // RunPod API Key usually passed directly
                .body(Map.of("query", query))
                .retrieve()
                .body(Map.class);

        if (response != null && response.containsKey("errors")) {
            throw new RuntimeException("RunPod Refusal");
        }
        return true;
    }

    private boolean verifyVast(String key) {
        Map response = restClient.get()
                .uri("https://console.vast.ai/api/v0/users/current/")
                .header("Authorization", "Bearer " + key)
                .retrieve()
                .body(Map.class);

        if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
            throw new RuntimeException("Vast Refusal");
        }
        return true;
    }
}
