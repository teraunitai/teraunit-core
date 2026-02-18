package ai.teraunit.core.api;

import ai.teraunit.core.pricing.GpuOffer;
import java.util.List;

/**
 * THE MARKET MENU
 * Standard JSON response for the Frontend/CLI.
 */
public record PricingResponse(
        String provider,
        List<GpuOffer> offers,
        int count,
        long timestamp
) {
    // Canonical Constructor for easy initialization
    public PricingResponse(String provider, List<GpuOffer> offers) {
        this(provider, offers, offers.size(), System.currentTimeMillis());
    }
}
