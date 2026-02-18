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
            // --- 1. LAMBDA (UPDATED FIX) ---
            if (provider == ProviderName.LAMBDA) {
                Map<String, Object> data = (Map<String, Object>) rawData.get("data");
                if (data != null) {
                    data.forEach((key, val) -> {
                        try {
                            Map<String, Object> item = (Map<String, Object>) val;

                            // FIX: Price is now inside "instance_type"
                            Map<String, Object> typeInfo = (Map<String, Object>) item.get("instance_type");
                            if (typeInfo == null) return;

                            String name = (String) typeInfo.get("name");
                            Object priceObj = typeInfo.get("price_cents_per_hour");

                            // FIX: Availability is in "regions_with_capacity_available"
                            List<Map<String, Object>> regions = (List<Map<String, Object>>) item.get("regions_with_capacity_available");

                            if (priceObj != null && regions != null && !regions.isEmpty()) {
                                double price = ((Number) priceObj).doubleValue() / 100.0;

                                // Generate one offer per available region
                                for (Map<String, Object> region : regions) {
                                    String regionName = (String) region.get("name");
                                    offers.add(GpuOffer.builder()
                                            .provider("LAMBDA")
                                            .gpuModel(name.toUpperCase())
                                            .launchId(name)
                                            .pricePerHour(price)
                                            .region(regionName)
                                            .isAvailable(true)
                                            .build());
                                }
                            }
                        } catch (Exception e) {
                            // Skip malformed items
                        }
                    });
                }
            }
            // --- 2. RUNPOD (ORIGINAL - UNTOUCHED) ---
            else if (provider == ProviderName.RUNPOD) {
                Map<String, Object> data = (Map<String, Object>) rawData.get("data");
                if (data != null) {
                    List<Map<String, Object>> gpuTypes = (List<Map<String, Object>>) data.get("gpuTypes");
                    for (var gpu : gpuTypes) {
                        Object priceObj = gpu.get("communityPrice");
                        String id = (String) gpu.get("id");
                        String displayName = (String) gpu.get("displayName");

                        if (priceObj != null && id != null) {
                            double price = ((Number) priceObj).doubleValue();
                            if (price > 0.001 && !id.equalsIgnoreCase("unknown")) {
                                offers.add(GpuOffer.builder()
                                        .provider("RUNPOD")
                                        .gpuModel(displayName)
                                        .launchId(id)
                                        .pricePerHour(price)
                                        .isAvailable(true)
                                        .build());
                            }
                        }
                    }
                }
            }
            // --- 3. VAST (ORIGINAL - UNTOUCHED) ---
            else if (provider == ProviderName.VAST) {
                List<Map<String, Object>> vastOffers = (List<Map<String, Object>>) rawData.get("offers");
                if (vastOffers != null) {
                    for (var offer : vastOffers) {
                        Double price = ((Number) offer.get("dph_total")).doubleValue();
                        Object idObj = offer.get("id");

                        if (price > 0.01 && idObj != null) {
                            String machineId = String.valueOf(idObj);
                            offers.add(GpuOffer.builder()
                                    .provider("VAST")
                                    .gpuModel((String) offer.get("gpu_name"))
                                    .launchId(machineId)
                                    .region((String) offer.get("geolocation"))
                                    .pricePerHour(price)
                                    .isAvailable(true)
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[TeraUnit-Mapper-Error] " + provider + ": " + e.getMessage());
        }
        return offers;
    }
}
