package ai.teraunit.core.pricing;

import ai.teraunit.core.common.ProviderName;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@SuppressWarnings("unchecked")
public class PriceMapper {

    public List<GpuOffer> mapToOffers(ProviderName provider, Map<String, Object> rawData) {
        List<GpuOffer> offers = new ArrayList<>();
        try {
            if (provider == ProviderName.LAMBDA) {
                Map<String, Object> data = (Map<String, Object>) rawData.get("data");
                if (data != null) {
                    data.forEach((name, details) -> {
                        Map<String, Object> specs = (Map<String, Object>) details;
                        Object priceObj = specs.get("price_cents");
                        if (priceObj != null) {
                            double price = ((Number) priceObj).doubleValue() / 100.0;
                            // FILTER: Ignore zero-price or empty names
                            if (price > 0.01) {
                                offers.add(GpuOffer.builder()
                                        .provider("LAMBDA")
                                        .gpuModel(name.toUpperCase())
                                        .pricePerHour(price)
                                        .isAvailable(true)
                                        .build());
                            }
                        }
                    });
                }
            } else if (provider == ProviderName.RUNPOD) {
                Map<String, Object> data = (Map<String, Object>) rawData.get("data");
                if (data != null) {
                    List<Map<String, Object>> gpuTypes = (List<Map<String, Object>>) data.get("gpuTypes");
                    for (var gpu : gpuTypes) {
                        Object priceObj = gpu.get("communityPrice");
                        String modelId = (String) gpu.get("id");

                        if (priceObj != null && modelId != null) {
                            double price = ((Number) priceObj).doubleValue();

                            // FILTER: The "Trash" Collector
                            // Rejects "unknown", nulls, and $0.00 items
                            if (price > 0.001 && !modelId.equalsIgnoreCase("unknown")) {
                                offers.add(GpuOffer.builder()
                                        .provider("RUNPOD")
                                        .gpuModel(modelId)
                                        .pricePerHour(price)
                                        .isAvailable(true)
                                        .build());
                            }
                        }
                    }
                }
            } else if (provider == ProviderName.VAST) {
                List<Map<String, Object>> vastOffers = (List<Map<String, Object>>) rawData.get("offers");
                if (vastOffers != null) {
                    for (var offer : vastOffers) {
                        Double price = ((Number) offer.get("dph_total")).doubleValue();
                        // FILTER: Basic sanity check for Vast
                        if (price > 0.01) {
                            offers.add(GpuOffer.builder()
                                    .provider("VAST")
                                    .gpuModel((String) offer.get("gpu_name"))
                                    .pricePerHour(price)
                                    .region((String) offer.get("geolocation"))
                                    .isAvailable(true)
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[TeraUnit-Mapper-Error] " + provider + ": " + e.getMessage());
            e.printStackTrace();
        }
        return offers;
    }
}