package ai.teraunit.core.provisioning;

import ai.teraunit.core.api.LaunchRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class ProviderVerifier {

    private final RestClient restClient;

    public ProviderVerifier(RestClient restClient) {
        this.restClient = restClient;
    }

    public boolean verify(LaunchRequest request, String apiKey) {
        // SANITIZATION PROTOCOL: Remove invisible spaces/newlines
        String cleanKey = ai.teraunit.core.security.TokenUtil.sanitizeApiKey(apiKey);

        System.out.println(
                ">> VERIFYING: " + request.provider() + " | KeyID: " + mask(cleanKey) + " | Len: " + cleanKey.length());

        try {
            return switch (request.provider()) {
                case LAMBDA -> verifyLambda(cleanKey, request.sshKeyName());
                case RUNPOD -> verifyRunPod(cleanKey);
                case VAST -> verifyVast(cleanKey);
            };
        } catch (Exception e) {
            System.err.println("!! VERIFICATION FAILED !!");
            System.err.println("Provider: " + request.provider());
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }

    // --- PROVIDER CHECKS ---

    @SuppressWarnings("unchecked")
    private boolean verifyLambda(String key, String sshKeyName) {
        try {
            // 1. List Keys
            Map<String, Object> response = restClient.get()
                    .uri("https://cloud.lambda.ai/api/v1/ssh-keys")
                    .header("Authorization", "Bearer " + key)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("data")) {
                throw new RuntimeException("Lambda returned invalid SSH Key data.");
            }

            // 2. Filter by Name
            List<Map<String, Object>> keys = (List<Map<String, Object>>) response.get("data");

            boolean found = keys.stream()
                    .anyMatch(k -> sshKeyName.equals(k.get("name")));

            if (!found) {
                // List available keys to help user debug
                List<String> keyNames = keys.stream()
                        .map(k -> (String) k.get("name"))
                        .toList();
                throw new RuntimeException("SSH Key '" + sshKeyName + "' not found. Your keys: " + keyNames);
            }

            return true;

        } catch (HttpClientErrorException.Unauthorized e) {
            throw new RuntimeException("Invalid API Key (401). Check for trailing spaces.");
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Lambda API Endpoint Not Found (Check URL).");
        }
    }

    private boolean verifyRunPod(String key) {
        String query = "{ user { id } }";
        Map response = restClient.post()
                .uri("https://api.runpod.io/graphql")
                .header("Authorization", key)
                .body(Map.of("query", query))
                .retrieve()
                .body(Map.class);
        if (response != null && response.containsKey("errors")) {
            throw new RuntimeException("RunPod Auth Failed: " + response.get("errors"));
        }
        return true;
    }

    private boolean verifyVast(String key) {
        try {
            restClient.get()
                    .uri("https://console.vast.ai/api/v0/users/current/")
                    .header("Authorization", "Bearer " + key)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Vast Auth Failed: " + e.getStatusCode());
        }
    }

    private String mask(String key) {
        if (key == null || key.length() < 5)
            return "????";
        return "..." + key.substring(key.length() - 4);
    }
}
