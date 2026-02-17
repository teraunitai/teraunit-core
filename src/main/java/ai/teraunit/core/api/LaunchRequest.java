package ai.teraunit.core.api;

import ai.teraunit.core.common.ProviderName;

public record LaunchRequest(
        ProviderName provider,
        String apiKey,       // The User's Raw API Key (e.g., "sk_live_...")
        String instanceType, // e.g., "gpu_1x_h100_pcie"
        String region,       // e.g., "us-east-1"
        String sshKeyName    // The user's SSH key name on the cloud provider
) {}
