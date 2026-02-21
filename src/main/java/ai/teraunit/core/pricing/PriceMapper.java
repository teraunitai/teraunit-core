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
                Object dataObj = rawData.get("data");
                if (dataObj != null) {
                    // Lambda responses have been seen with `data` as either a Map keyed by instance
                    // type
                    // or a List of entries. Support both.
                    List<?> entries;
                    if (dataObj instanceof Map<?, ?> dataMap) {
                        entries = new ArrayList<>(dataMap.values());
                    } else if (dataObj instanceof List<?> dataList) {
                        entries = dataList;
                    } else {
                        entries = List.of();
                    }

                    for (Object entry : entries) {
                        try {
                            if (!(entry instanceof Map<?, ?> itemAny)) {
                                continue;
                            }
                            Map<String, Object> item = (Map<String, Object>) itemAny;

                            // Some shapes nest under `instance_type`, others put fields at top level.
                            Object typeObj = item.get("instance_type");
                            Map<String, Object> typeInfo = (typeObj instanceof Map<?, ?> m)
                                    ? (Map<String, Object>) m
                                    : item;

                            String name = (String) typeInfo.get("name");
                            if (name == null || name.isBlank()) {
                                continue;
                            }

                            Object priceObj = typeInfo.get("price_cents_per_hour");
                            if (!(priceObj instanceof Number priceNum)) {
                                continue;
                            }
                            double price = priceNum.doubleValue() / 100.0;

                            Object regionsObj = item.get("regions_with_capacity_available");
                            if (regionsObj == null) {
                                regionsObj = typeInfo.get("regions_with_capacity_available");
                            }

                            List<?> regions;
                            if (regionsObj instanceof List<?> regionList) {
                                regions = regionList;
                            } else if (regionsObj instanceof Map<?, ?> regionMap) {
                                regions = new ArrayList<>(regionMap.entrySet());
                            } else if (regionsObj instanceof String regionString) {
                                regions = List.of(regionString);
                            } else {
                                regions = List.of();
                            }

                            if (regions.isEmpty()) {
                                continue;
                            }

                            for (Object r : regions) {
                                String regionName = null;

                                if (r instanceof Map.Entry<?, ?> regionEntry) {
                                    // If the key looks like a region name, prefer it.
                                    Object k = regionEntry.getKey();
                                    if (k != null) {
                                        regionName = String.valueOf(k);
                                    }
                                    // Otherwise try to read a nested "name" from the value.
                                    if ((regionName == null || regionName.isBlank())
                                            && regionEntry.getValue() instanceof Map<?, ?> vm) {
                                        Object rn = vm.get("name");
                                        regionName = rn == null ? null : String.valueOf(rn);
                                    }
                                } else if (r instanceof Map<?, ?> rm) {
                                    Object rn = rm.get("name");
                                    regionName = rn == null ? null : String.valueOf(rn);
                                } else if (r instanceof String rs) {
                                    regionName = rs;
                                }

                                if (regionName == null || regionName.isBlank()) {
                                    continue;
                                }

                                offers.add(new GpuOffer(
                                        ProviderName.LAMBDA.name(),
                                        name.toUpperCase(),
                                        name,
                                        price,
                                        regionName,
                                        true));
                            }

                        } catch (Exception e) {
                            /* Skip */
                        }
                    }
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
