package ai.teraunit.core.provisioning;

import org.springframework.stereotype.Component;

@Component
public class EgressGuard {

    // 2026 Standard: $0.09/GB out from AWS/GCP
    private static final double EGRESS_COST_PER_GB = 0.09;

    /**
     * PROTOCOL 10: EGRESS GUARD
     * Returns TRUE if the move is profitable.
     * Returns FALSE if data fees consume the savings.
     */
    public boolean isSafeToMove(double targetGpuPrice, double currentGpuPrice, int datasetSizeGb) {
        double savingsPerHour = currentGpuPrice - targetGpuPrice;

        // If the target is more expensive, obviously don't move.
        if (savingsPerHour <= 0) return false;

        double moveCost = datasetSizeGb * EGRESS_COST_PER_GB;

        // Breakeven: How many hours must we train to pay off the move?
        double breakEvenHours = moveCost / savingsPerHour;

        // RULE: If it takes > 24 hours to break even, reject the orchestration.
        // We deal in spot instances; reliability < 24h is risky.
        return breakEvenHours < 24.0;
    }
}
