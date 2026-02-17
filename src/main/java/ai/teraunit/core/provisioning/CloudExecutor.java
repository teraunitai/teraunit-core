package ai.teraunit.core.provisioning;

import ai.teraunit.core.api.LaunchRequest;
import ai.teraunit.core.common.ProviderName;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

@Service
public class CloudExecutor {

    private final RestClient restClient;

    // PHASE 3: THE AGENT SEED
    // This script is injected into the GPU instance via "user_data".
    // It runs automatically on boot to phone home.
    private static final String AGENT_STARTUP_SCRIPT = """
            #!/bin/bash
            echo "[TeraUnit] Agent Initializing..."
            # In Phase 4, this will download the binary.
            # For now, it proves we have control.
            curl -X POST https://api.teraunit.ai/v1/heartbeat -d '{"status":"alive"}'
            """;

    public CloudExecutor(RestClient restClient) {
        this.restClient = restClient;
    }

    public String provision(LaunchRequest request, String decryptedApiKey) {
        return switch (request.provider()) {
            case LAMBDA -> launchLambda(request, decryptedApiKey);
            case RUNPOD -> launchRunPod(request, decryptedApiKey);
            case VAST -> launchVast(request, decryptedApiKey);
        };
    }

    private String launchLambda(LaunchRequest request, String key) {
        try {
            // The Official Lambda Labs Launch Endpoint
            String endpoint = "https://cloud.lambdalabs.com/api/v1/instance-operations/launch";
            String auth = "Basic " + Base64.getEncoder().encodeToString((key + ":").getBytes());

            // Payload: Defines WHAT to buy
            Map<String, Object> payload = Map.of(
                    "region_name", request.region(),
                    "instance_type_name", request.instanceType(),
                    "ssh_key_names", new String[]{request.sshKeyName()},
                    "quantity", 1,
                    "name", "teraunit-worker-" + System.currentTimeMillis()
            );

            // EXECUTE ORDER
            var response = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", auth)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            // Return the Instance ID (The Receipt)
            return "SUCCESS: Lambda Launch Initiated. Data: " + response;

        } catch (Exception e) {
            // If this fails, it means you are out of money or quota
            throw new RuntimeException("LAMBDA_LAUNCH_FAILED: " + e.getMessage());
        }
    }

    private String launchRunPod(LaunchRequest request, String key) {
        return "SUCCESS: RunPod Launch Simulated (Integration Pending)";
    }

    private String launchVast(LaunchRequest request, String key) {
        return "SUCCESS: Vast Launch Simulated";
    }
}
