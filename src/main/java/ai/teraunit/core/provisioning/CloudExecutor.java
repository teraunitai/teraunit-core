package ai.teraunit.core.provisioning;

import ai.teraunit.core.api.LaunchRequest;
import ai.teraunit.core.common.ProviderName;
import ai.teraunit.core.security.TokenUtil;
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

    public String provision(LaunchRequest request,
            String decryptedApiKey,
            String heartbeatId,
            String heartbeatToken) {
        String cleanKey = TokenUtil.sanitizeApiKey(decryptedApiKey);
        return switch (request.provider()) {
            case LAMBDA -> launchLambda(request, cleanKey, heartbeatId, heartbeatToken);
            case RUNPOD -> launchRunPod(request, cleanKey, heartbeatId, heartbeatToken);
            case VAST -> launchVast(request, cleanKey, heartbeatId, heartbeatToken);
        };
    }

    public void terminate(String instanceId, ProviderName provider, String apiKey) {
        System.out.println("⚡ TERMINATING: " + instanceId + " on " + provider);
        String cleanKey = TokenUtil.sanitizeApiKey(apiKey);
        try {
            switch (provider) {
                case LAMBDA -> terminateLambda(instanceId, cleanKey);
                case RUNPOD -> terminateRunPod(instanceId, cleanKey);
                case VAST -> terminateVast(instanceId, cleanKey);
            }
        } catch (Exception e) {
            System.err.println("FAILED TO KILL " + instanceId + ": " + e.getMessage());
        }
    }

    // --- LAUNCHERS ---

    private String launchLambda(LaunchRequest request, String key, String heartbeatId, String heartbeatToken) {
        try {
            String endpoint = "https://cloud.lambda.ai/api/v1/instance-operations/launch";

            String cleanSshKeyName = TokenUtil.sanitizeHumanIdentifier(request.sshKeyName());

            // cloud-init user_data is supported by Lambda Cloud API
            String userData = generateCloudInitUserData(heartbeatId, heartbeatToken);

            // Lambda injection is tricky via API. We use the basic payload for MVP.
            Map<String, Object> payload = Map.of(
                    "region_name", request.region(),
                    "instance_type_name", request.instanceType(),
                    "ssh_key_names", new String[] { cleanSshKeyName },
                    "quantity", 1,
                    "name", "teraunit-worker-" + System.currentTimeMillis(),
                    "user_data", userData);

            var response = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + key)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("data")) {
                throw new RuntimeException("Invalid response from Lambda launch.");
            }
            Map data = (Map) response.get("data");
            java.util.List instanceIds = (java.util.List) data.get("instance_ids");
            return "LAMBDA::" + instanceIds.get(0);
        } catch (Exception e) {
            throw new RuntimeException("LAMBDA_FAIL: " + e.getMessage());
        }
    }

    private String launchRunPod(LaunchRequest request, String key, String heartbeatId, String heartbeatToken) {
        try {
            // TITANIUM FIX: BASE64 ENCODING
            // We encode the script to avoid GraphQL syntax errors with quotes/backslashes
            String rawScript = generateHeartbeatScript(heartbeatId, heartbeatToken);
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
                    .header("Authorization", "Bearer " + key)
                    .body(Map.of("query", query))
                    .retrieve()
                    .body(Map.class);

            if (response.containsKey("errors"))
                throw new RuntimeException("API_ERR: " + response.get("errors"));
            Map data = (Map) response.get("data");
            Map pod = (Map) data.get("podFindAndDeployOnDemand");

            return "RUNPOD::" + pod.get("id");

        } catch (Exception e) {
            throw new RuntimeException("RUNPOD_FAIL: " + e.getMessage());
        }
    }

    private String launchVast(LaunchRequest request, String key, String heartbeatId, String heartbeatToken) {
        try {
            String offerId = request.instanceType() == null ? null : request.instanceType().trim();
            if (offerId == null || offerId.isBlank() || "null".equalsIgnoreCase(offerId) || !offerId.matches("\\d+")) {
                throw new RuntimeException(
                        "Missing/invalid Vast offer id (instanceType). Select a valid Vast offer from the pricing list.");
            }

            String endpoint = "https://console.vast.ai/api/v0/asks/" + offerId + "/";

            // Vast handles JSON body escaping well, so we pass raw script
            Map<String, Object> payload = Map.of(
                    "id", offerId,
                    "client_id", "me",
                    "image", "pytorch/pytorch:2.0.1-cuda11.7-cudnn8-devel",
                    "onstart", generateHeartbeatScript(heartbeatId, heartbeatToken));

            var response = restClient.put()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + key)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new RuntimeException("Empty response from Vast.");
            }

            if (Boolean.TRUE.equals(response.get("success"))) {
                return "VAST::" + response.get("new_contract");
            }
            throw new RuntimeException("VAST_ERR: " + response);
        } catch (Exception e) {
            throw new RuntimeException("VAST_FAIL: " + e.getMessage());
        }
    }

    // --- TERMINATORS ---

    private void terminateLambda(String id, String key) {
        restClient.post()
                .uri("https://cloud.lambda.ai/api/v1/instance-operations/terminate")
                .header("Authorization", "Bearer " + key)
                .body(Map.of("instance_ids", new String[] { id }))
                .retrieve()
                .toBodilessEntity();
    }

    private void terminateRunPod(String id, String key) {
        String query = String.format("mutation { podTerminate(input: { podId: \"%s\" }) }", id);
        restClient.post()
                .uri("https://api.runpod.io/graphql")
                .header("Authorization", "Bearer " + key)
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

    // TITANIUM POLYFILL: Works immediately without external binaries
    private String generateHeartbeatScript(String heartbeatId, String heartbeatToken) {
        return String.format(
                """
                        #!/bin/bash
                        HEARTBEAT_ID=\"%s\"
                        HEARTBEAT_TOKEN=\"%s\"
                        SERVER=\"%s\"

                        # Zombie Kill Switch: If we cannot contact the server for >300s, halt.
                        # Note: on container-based providers this may not power off the host.
                        DEATH_TIMEOUT=300
                        LAST_OK=$(date +%%s)

                        # 2. PERFORMANCE
                        swapoff -a

                        # 3. THE PULSE
                        while true; do
                            if curl --silent --show-error --fail --max-time 10 -X POST \"$SERVER\" \\
                                -H \"Content-Type: application/json\" \\
                                -H \"X-Tera-Heartbeat-Token: $HEARTBEAT_TOKEN\" \\
                                -d \"{\\\"id\\\":\\\"$HEARTBEAT_ID\\\",\\\"status\\\":\\\"alive\\\"}\"; then
                                LAST_OK=$(date +%%s)
                            fi

                            NOW=$(date +%%s)
                            if [ $((NOW - LAST_OK)) -gt $DEATH_TIMEOUT ]; then
                                echo \"[TERA] Connection lost > ${DEATH_TIMEOUT}s. Executing kill switch.\" >&2
                                (shutdown -h now 2>/dev/null || systemctl poweroff -i 2>/dev/null || poweroff -f 2>/dev/null || halt -f 2>/dev/null) || true
                                exit 1
                            fi

                            sleep 60
                        done &
                        """,
                heartbeatId, heartbeatToken, this.callbackUrl);
    }

    private String generateCloudInitUserData(String heartbeatId, String heartbeatToken) {
        // Minimal cloud-init (plain text) to run a background heartbeat loop.
        // The callback URL must be reachable from the instance.
        String script = generateHeartbeatScript(heartbeatId, heartbeatToken);

        // NOTE: build the YAML with explicit indentation to avoid formatter damage.
        StringBuilder sb = new StringBuilder();
        sb.append("#cloud-config\n");
        sb.append("write_files:\n");
        sb.append("  - path: /usr/local/bin/tera-heartbeat.sh\n");
        sb.append("    permissions: '0755'\n");
        sb.append("    owner: root:root\n");
        sb.append("    content: |\n");
        sb.append(indentForCloudInit(script, 6)).append("\n");
        sb.append("runcmd:\n");
        sb.append("  - [ bash, -lc, \"/usr/local/bin/tera-heartbeat.sh\" ]\n");
        return sb.toString();
    }

    private static String indentForCloudInit(String content, int spaces) {
        String prefix = " ".repeat(Math.max(0, spaces));
        return content.lines().map(l -> prefix + l).reduce((a, b) -> a + "\n" + b).orElse(prefix);
    }

}
