package ai.teraunit.core.pricing;

import lombok.Builder;
import java.io.Serializable;

@Builder
public record GpuOffer(
        String provider,
        String gpuModel,    // Display Name (e.g., "NVIDIA A100")
        String launchId,    // The API Code (e.g., "gpu_1x_a100" or "118293")
        double pricePerHour,
        String region,
        boolean isAvailable
) implements Serializable {}
