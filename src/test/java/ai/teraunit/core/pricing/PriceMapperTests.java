package ai.teraunit.core.pricing;

import ai.teraunit.core.common.ProviderName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceMapperTests {

    @Test
    void lambdaMapping_acceptsDataAsList() {
        PriceMapper mapper = new PriceMapper();

        Map<String, Object> payload = Map.of(
                "data", List.of(
                        Map.of(
                                "instance_type", Map.of(
                                        "name", "gpu_1x_a10",
                                        "price_cents_per_hour", 1234),
                                "regions_with_capacity_available", List.of(
                                        Map.of("name", "us-west"),
                                        Map.of("name", "us-east")))));

        List<GpuOffer> offers = mapper.mapToOffers(ProviderName.LAMBDA, payload);
        assertEquals(2, offers.size());
        assertEquals("us-west", offers.get(0).region());
        assertEquals("us-east", offers.get(1).region());
    }

    @Test
    void lambdaMapping_acceptsDataAsMap() {
        PriceMapper mapper = new PriceMapper();

        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "gpu_1x_a10", Map.of(
                                "instance_type", Map.of(
                                        "name", "gpu_1x_a10",
                                        "price_cents_per_hour", 1234),
                                "regions_with_capacity_available", List.of(
                                        "us-west"))));

        List<GpuOffer> offers = mapper.mapToOffers(ProviderName.LAMBDA, payload);
        assertEquals(1, offers.size());
        assertEquals("us-west", offers.getFirst().region());
    }

    @Test
    void lambdaMapping_acceptsRegionsAsMap() {
        PriceMapper mapper = new PriceMapper();

        Map<String, Object> payload = Map.of(
                "data", List.of(
                        Map.of(
                                "instance_type", Map.of(
                                        "name", "gpu_1x_a10",
                                        "price_cents_per_hour", 1234),
                                "regions_with_capacity_available", Map.of(
                                        "us-west", Map.of("name", "us-west"),
                                        "us-east", Map.of("name", "us-east")))));

        List<GpuOffer> offers = mapper.mapToOffers(ProviderName.LAMBDA, payload);
        assertEquals(2, offers.size());
        assertEquals("us-west", offers.get(0).region());
        assertEquals("us-east", offers.get(1).region());
    }

    @Test
    void lambdaMapping_acceptsRegionsAsString() {
        PriceMapper mapper = new PriceMapper();

        Map<String, Object> payload = Map.of(
                "data", List.of(
                        Map.of(
                                "instance_type", Map.of(
                                        "name", "gpu_1x_a10",
                                        "price_cents_per_hour", 1234),
                                "regions_with_capacity_available", "us-west")));

        List<GpuOffer> offers = mapper.mapToOffers(ProviderName.LAMBDA, payload);
        assertEquals(1, offers.size());
        assertEquals("us-west", offers.getFirst().region());
    }

    @Test
    void lambdaMapping_acceptsRegionsAsListOfStrings() {
        PriceMapper mapper = new PriceMapper();

        Map<String, Object> payload = Map.of(
                "data", List.of(
                        Map.of(
                                "instance_type", Map.of(
                                        "name", "gpu_1x_a10",
                                        "price_cents_per_hour", 1234),
                                "regions_with_capacity_available", List.of(
                                        "us-west",
                                        "us-east"))));

        List<GpuOffer> offers = mapper.mapToOffers(ProviderName.LAMBDA, payload);
        assertEquals(2, offers.size());
        assertEquals("us-west", offers.get(0).region());
        assertEquals("us-east", offers.get(1).region());
    }

    @Test
    void runpodMapping_mapsGpuTypesFromGraphQlResponse() {
        PriceMapper mapper = new PriceMapper();

        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "gpuTypes", List.of(
                                Map.of(
                                        "id", "NVIDIA_A100",
                                        "displayName", "NVIDIA A100",
                                        "communityPrice", 2.25),
                                Map.of(
                                        "id", "unknown",
                                        "displayName", "Unknown",
                                        "communityPrice", 1.00))));

        List<GpuOffer> offers = mapper.mapToOffers(ProviderName.RUNPOD, payload);
        assertEquals(1, offers.size());
        assertEquals("RUNPOD", offers.getFirst().provider());
        assertEquals("NVIDIA_A100", offers.getFirst().launchId());
        assertEquals("GLOBAL", offers.getFirst().region());
        assertTrue(offers.getFirst().pricePerHour() > 0.0);
    }

    @Test
    void vastMapping_mapsOffersAndUsesNumericId() {
        PriceMapper mapper = new PriceMapper();

        Map<String, Object> payload = Map.of(
                "offers", List.of(
                        Map.of(
                                "dph_total", 0.49,
                                "id", 123456,
                                "gpu_name", "RTX 3090",
                                "geolocation", "US"),
                        Map.of(
                                "dph_total", 0.02,
                                "id", "not-a-number",
                                "gpu_name", "RTX 4090",
                                "geolocation", "US")));

        List<GpuOffer> offers = mapper.mapToOffers(ProviderName.VAST, payload);
        assertEquals(1, offers.size());
        assertEquals("VAST", offers.getFirst().provider());
        assertEquals("123456", offers.getFirst().launchId());
        assertEquals("US", offers.getFirst().region());
    }
}
