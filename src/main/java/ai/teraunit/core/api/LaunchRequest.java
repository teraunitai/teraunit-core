package ai.teraunit.core.api;

import ai.teraunit.core.common.ProviderName;

/**
 * THE LAUNCH MANIFEST
 *
 * Updated to include critical data points for the Logic Engine.
 * Without 'datasetSizeGb', we cannot calculate Egress.
 * Without 'sourceRegion', we cannot enforce Sovereignty.
 */
public record LaunchRequest(
        // Target Configuration
        ProviderName provider,
        String apiKey,
        String instanceType, // Target GPU (e.g., "gpu_1x_h100_pcie")
        String region,       // Target Region (e.g., "us-east-1")
        String sshKeyName,

        // Logic Engine Inputs (REQUIRED for Orchestration)
        int datasetSizeGb,          // For EgressGuard (Protocol 10)
        String sourceRegion,        // For SovereigntySwitch (Protocol 12)
        double currentGpuHourlyCost // For ROI Calculation
) {}
