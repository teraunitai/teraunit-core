package ai.teraunit.core.common;

import java.util.Map;

/**
 * The "Pulse" of TeraUnit.
 * Immutable record for high-concurrency event processing.
 */
public record GpuPriceScrapedEvent(
        ProviderName provider,
        Map<String, Object> rawData
) {}
