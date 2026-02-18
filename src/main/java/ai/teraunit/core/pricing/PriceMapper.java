package ai.teraunit.core.pricing;

import ai.teraunit.core.common.ProviderName;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@SuppressWarnings("unchecked")
public class PriceMapper {

    public List<GpuOffer> mapToOffers(ProviderName provider, Map<String, Object> rawData) {
        List<GpuOffer> offers = new ArrayList<>();

        try {
            // ---------------------------------------------------------
            // 1. LAMBDA LABS
            // ---------------------------------------------------------
            if (provider == ProviderName.LAMBDA) {
                Map<String, Object> data = (Map<String, Object>) rawData.get("data");
                if (data != null) {
                    data.forEach((key, val) -> {
                        try {
                            Map<String, Object> item = (Map<String, Object>) val;
                            Map<String, Object> typeInfo = (Map<String, Object>) item.get("instance_type");

                            if (typeInfo == null)
                                return;

                            String name = (String) typeInfo.get("name");
                            Object priceObj = typeInfo.get("price_cents_per_hour");
                            List<Map<String, Object>> regions = (List<Map<String, Object>>) item
                                    .get("regions_with_capacity_available");

                            if (priceObj != null && regions != null && !regions.isEmpty()) {
                                double price = ((Number) priceObj).doubleValue() / 100.0;

                                for (Map<String, Object> region : regions) {
                                    offers.add(new GpuOffer(
                                            ProviderName.LAMBDA.name(),
                                            name.toUpperCase(),
                                            name,
                                            price,
                                            (String) region.get("name"),
                                            true));
                                }
                            }
                        } catch (Exception e) {
                            /* Skip */ }
                    });
                }
            }
            // ---------------------------------------------------------
            // 2. RUNPOD
            // ---------------------------------------------------------
            else if (provider == ProviderName.RUNPOD) {
                Map<String, Object> data = (Map<String, Object>) rawData.get("data");
                if (data != null) {
                    List<Map<String, Object>> gpuTypes = (List<Map<String, Object>>) data.get("gpuTypes");
                    if (gpuTypes != null) {
                        for (var gpu : gpuTypes) {
                            try {
                                Object priceObj = gpu.get("communityPrice");
                                String id = (String) gpu.get("id");
                                String displayName = (String) gpu.get("displayName");

                                if (priceObj != null && id != null) {
                                    double price = ((Number) priceObj).doubleValue();
                                    if (price > 0.001 && !id.equalsIgnoreCase("unknown")) {
                                        offers.add(new GpuOffer(
                                                ProviderName.RUNPOD.name(),
                                                displayName,
                                                id,
                                                price,
                                                "GLOBAL", // RunPod allocates automatically
                                                true));
                                    }
                                }
                            } catch (Exception e) {
                                /* Skip */ }
                        }
                    }
                }
            }
            // ---------------------------------------------------------
            // 3. VAST.AI
            // ---------------------------------------------------------
            else if (provider == ProviderName.VAST) {
                List<Map<String, Object>> vastOffers = (List<Map<String, Object>>) rawData.get("offers");
                if (vastOffers != null) {
                    for (var offer : vastOffers) {
                        try {
                            Object dphObj = offer.get("dph_total");
                            if (!(dphObj instanceof Number dphNum))
                                continue;
                            double price = dphNum.doubleValue();

                            Object idObj = offer.get("id");
                            if (idObj == null || "null".equalsIgnoreCase(String.valueOf(idObj).trim())) {
                                idObj = offer.get("ask_id");
                            }
                            if (idObj == null || "null".equalsIgnoreCase(String.valueOf(idObj).trim())) {
                                idObj = offer.get("askId");
                            }

                            String id = idObj == null ? null : String.valueOf(idObj).trim();
                            if (id == null || id.isBlank() || "null".equalsIgnoreCase(id) || !id.matches("\\d+")) {
                                continue;
                            }

                            if (price > 0.01) {
                                offers.add(new GpuOffer(
                                        ProviderName.VAST.name(),
                                        (String) offer.get("gpu_name"),
                                        id,
                                        price,
                                        (String) offer.get("geolocation"),
                                        true));
                            }
                        } catch (Exception e) {
                            /* Skip */ }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[TeraUnit-Mapper] Critical Failure: " + e.getMessage());
        }
        return offers;
    }
}
