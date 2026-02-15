package ai.teraunit.core.api;

import ai.teraunit.core.pricing.GpuOffer;
import lombok.Builder;
import java.util.List;
import java.util.Map;

/**
 * Standardized API Response for Phase 1.
 * Prevents "naked" data returns and allows for future metadata expansion.
 */
@Builder
public record PricingResponse(
        long timestamp,
        String status,
        Map<String, List<GpuOffer>> data
) {}
