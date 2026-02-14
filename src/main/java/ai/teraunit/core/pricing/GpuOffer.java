package ai.teraunit.core.pricing;

public record GpuOffer(
        String provider,
        String gpuModel,
        double pricePerHour,
        String region,
        double preemptionRate,
        int latencyMs
) {}
