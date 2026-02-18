package ai.teraunit.core.pricing;

import java.io.Serializable;

public record GpuOffer(
        String provider,
        String gpuModel,    // e.g. "NVIDIA A100"
        String launchId,    // e.g. "gpu_1x_a100"
        double pricePerHour,
        String region,
        boolean isAvailable
) implements Serializable {}
