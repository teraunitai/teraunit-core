package ai.teraunit.core.provisioning;

import ai.teraunit.core.api.LaunchRequest;
import ai.teraunit.core.common.ProviderName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

@Service
public class CloudExecutor {

    private final RestClient restClient;
    private final String callbackUrl;

    public CloudExecutor(RestClient restClient,
                         @Value("${teraunit.callback-url}") String callbackUrl) {
        this.restClient = restClient;
        this.callbackUrl = callbackUrl;
    }

    public String provision(LaunchRequest request, String decryptedApiKey) {
        return switch (request.provider()) {
            case LAMBDA -> launchLambda(request, decryptedApiKey);
            case RUNPOD -> launchRunPod(request, decryptedApiKey);
            case VAST -> launchVast(request, decryptedApiKey);
        };
    }

    public void terminate(String instanceId, ProviderName provider, String apiKey) {
        System.out.println("⚡ TERMINATING: " + instanceId + " on " + provider);
        try {
            switch (provider) {
                case LAMBDA -> terminateLambda(instanceId, apiKey);
                case RUNPOD -> terminateRunPod(instanceId, apiKey);
                case VAST -> terminateVast(instanceId, apiKey);
            }
        } catch (Exception e) {
            System.err.println("FAILED TO KILL " + instanceId + ": " + e.getMessage());
        }
    }

    // --- LAUNCHERS ---

    private String launchLambda(LaunchRequest request, String key) {
        try {
            String endpoint = "https://cloud.lambdalabs.com/api/v1/instance-operations/launch";
            String auth = "Basic " + Base64.getEncoder().encodeToString((key + ":").getBytes());

            // Lambda injection is tricky via API. We use the basic payload for MVP.
            Map<String, Object> payload = Map.of(
                    "region_name", request.region(),
                    "instance_type_name", request.instanceType(),
                    "ssh_key_names", new String[]{request.sshKeyName()},
                    "quantity", 1,
                    "name", "teraunit-worker-" + System.currentTimeMillis()
            );

            var response = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", auth)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            return "LAMBDA::" + ((java.util.List) response.get("instance_ids")).get(0);
        } catch (Exception e) {
            throw new RuntimeException("LAMBDA_FAIL: " + e.getMessage());
        }
    }

    private String launchRunPod(LaunchRequest request, String key) {
        try {
            // TITANIUM FIX: BASE64 ENCODING
            // We encode the script to avoid GraphQL syntax errors with quotes/backslashes
            String rawScript = generateScript();
            String b64Script = Base64.getEncoder().encodeToString(rawScript.getBytes());

            // The command decodes and runs the script on boot
            String safeCommand = "echo " + b64Script + " | base64 -d | bash";

            String query = """
                mutation {
                  podFindAndDeployOnDemand(
                    input: {
                      cloudType: ALL,
                      gpuCount: 1,
                      volumeInGb: 40,
                      containerDiskInGb: 40,
                      minVcpuCount: 2,
                      minMemoryInGb: 15,
                      gpuTypeId: "%s",
                      name: "teraunit-worker",
                      imageName: "runpod/pytorch:2.0.1-py3.10-cuda11.8.0-devel",
                      dockerArgs: "/bin/bash -c '%s'",
                      env: [{ key: "TERA_MODE", value: "active" }]
                    }
                  ) {
                    id
                  }
                }
                """.formatted(request.instanceType(), safeCommand);

            Map response = restClient.post()
                    .uri("https://api.runpod.io/graphql")
                    .header("Authorization", key)
                    .body(Map.of("query", query))
                    .retrieve()
                    .body(Map.class);

            if (response.containsKey("errors")) throw new RuntimeException("API_ERR: " + response.get("errors"));
            Map data = (Map) response.get("data");
            Map pod = (Map) data.get("podFindAndDeployOnDemand");

            return "RUNPOD::" + pod.get("id");

        } catch (Exception e) {
            throw new RuntimeException("RUNPOD_FAIL: " + e.getMessage());
        }
    }

    private String launchVast(LaunchRequest request, String key) {
        try {
            String endpoint = "https://console.vast.ai/api/v0/asks/" + request.instanceType() + "/";

            // Vast handles JSON body escaping well, so we pass raw script
            Map<String, Object> payload = Map.of(
                    "client_id", "me",
                    "image", "pytorch/pytorch:2.0.1-cuda11.7-cudnn8-devel",
                    "onstart", generateScript()
            );

            var response = restClient.put()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + key)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            if (Boolean.TRUE.equals(response.get("success"))) {
                return "VAST::" + response.get("new_contract");
            }
            throw new RuntimeException("VAST_ERR");
        } catch (Exception e) {
            throw new RuntimeException("VAST_FAIL: " + e.getMessage());
        }
    }

    // --- TERMINATORS ---

    private void terminateLambda(String id, String key) {
        String auth = "Basic " + Base64.getEncoder().encodeToString((key + ":").getBytes());
        restClient.post()
                .uri("https://cloud.lambdalabs.com/api/v1/instance-operations/terminate")
                .header("Authorization", auth)
                .body(Map.of("instance_ids", new String[]{id}))
                .retrieve()
                .toBodilessEntity();
    }

    private void terminateRunPod(String id, String key) {
        String query = String.format("mutation { podTerminate(input: { podId: \"%s\" }) }", id);
        restClient.post()
                .uri("https://api.runpod.io/graphql")
                .header("Authorization", key)
                .body(Map.of("query", query))
                .retrieve()
                .toBodilessEntity();
    }

    private void terminateVast(String id, String key) {
        restClient.delete()
                .uri("https://console.vast.ai/api/v0/instances/" + id + "/")
                .header("Authorization", "Bearer " + key)
                .retrieve()
                .toBodilessEntity();
    }

    // SCRIPT GENERATOR
    private String generateScript() {
        return """
            #!/bin/bash
            echo "[TERAUNIT] 🚀 BOOT SEQUENCE INITIATED..."
            SERVER="%s"
            ID=$(hostname)
            
            # 1. CHECK FOR BINARY (Future Phase)
            if [ -f /root/tera-agent ]; then
                echo "[TERAUNIT] ✅ Binary Found."
                chmod +x /root/tera-agent && /root/tera-agent &
            else
                echo "[TERAUNIT] ⚠️ Engaging Bash Polyfill."
                # 2. BASH POLYFILL (Current Safety Net)
                while true; do
                    curl -X POST "$SERVER" -H "Content-Type: application/json" -d "{\\"id\\":\\"$ID\\", \\"status\\":\\"alive\\"}"
                    sleep 60
                done &
            fi
            """.formatted(this.callbackUrl);
    }
}
