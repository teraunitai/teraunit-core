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
                        // FIX: Defensive check for null price
                        Object priceObj = specs.get("price_cents");
                        if (priceObj != null) {
                            offers.add(GpuOffer.builder()
                                    .provider("LAMBDA")
                                    .gpuModel(name.toUpperCase())
                                    .pricePerHour(((Number) priceObj).doubleValue() / 100.0)
                                    .isAvailable(true)
                                    .build());
                        }
                    });
                }
            } else if (provider == ProviderName.RUNPOD) {
                Map<String, Object> data = (Map<String, Object>) rawData.get("data");
                if (data != null) {
                    List<Map<String, Object>> gpuTypes = (List<Map<String, Object>>) data.get("gpuTypes");
                    for (var gpu : gpuTypes) {
                        Object priceObj = gpu.get("communityPrice");
                        if (priceObj != null) {
                            offers.add(GpuOffer.builder()
                                    .provider("RUNPOD")
                                    .gpuModel((String) gpu.get("id"))
                                    .pricePerHour(((Number) priceObj).doubleValue())
                                    .isAvailable(true)
                                    .build());
                        }
                    }
                }
            } else if (provider == ProviderName.VAST) {
                List<Map<String, Object>> vastOffers = (List<Map<String, Object>>) rawData.get("offers");
                if (vastOffers != null) {
                    for (var offer : vastOffers) {
                        offers.add(GpuOffer.builder()
                                .provider("VAST")
                                .gpuModel((String) offer.get("gpu_name"))
                                .pricePerHour(((Number) offer.get("dph_total")).doubleValue())
                                .region((String) offer.get("geolocation"))
                                .isAvailable(true)
                                .build());
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