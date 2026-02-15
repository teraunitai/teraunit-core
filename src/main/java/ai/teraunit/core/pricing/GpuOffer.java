package ai.teraunit.core.pricing;

import lombok.Builder;
import java.io.Serializable;

@Builder
public record GpuOffer(
        String provider,
        String gpuModel,
        double pricePerHour,
        String region,
        boolean isAvailable
) implements Serializable {}
