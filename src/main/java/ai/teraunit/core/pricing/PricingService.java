package ai.teraunit.core.pricing;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PricingService {

    // IN-MEMORY CACHE (The "Menu")
    private final Map<String, List<Map<String, Object>>> priceCache = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, List<Map<String, Object>>> getPrices() {
        return priceCache;
    }

    // REFRESH EVERY 30 SECONDS
    @Scheduled(fixedRate = 30000)
    public void scrapePrices() {
        scrapeRunPod();
        // scrapeVast(); // Add later
    }

    private void scrapeRunPod() {
        String url = "https://api.runpod.io/graphql";

        // THE FIX: ADD HEADERS TO LOOK LIKE A HUMAN
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        headers.set("api-key", "EMBEDDED_READ_ONLY"); // Sometimes needed, even if public

        // GraphQL Query asking for: ID, Name, Price, Stock
        String query = "{ \"query\": \"query GpuTypes { gpuTypes { id displayName memoryInGb communityPrice securePrice } }\" }";

        try {
            HttpEntity<String> entity = new HttpEntity<>(query, headers);
            Map response = restTemplate.postForObject(url, entity, Map.class);

            if (response != null && response.containsKey("data")) {
                Map data = (Map) response.get("data");
                List<Map> gpus = (List<Map>) data.get("gpuTypes");

                List<Map<String, Object>> offers = new ArrayList<>();

                for (Map gpu : gpus) {
                    // Convert to TeraUnit Standard Format
                    Map<String, Object> offer = new HashMap<>();
                    offer.put("provider", "RUNPOD");
                    offer.put("gpuModel", gpu.get("displayName"));
                    offer.put("vram", gpu.get("memoryInGb"));

                    // Price Logic: Community is cheaper, Secure is safer. We show Community for now.
                    // Handle null prices safely
                    Double price = 0.0;
                    if (gpu.get("communityPrice") != null) {
                        price = Double.valueOf(gpu.get("communityPrice").toString());
                    } else if (gpu.get("securePrice") != null) {
                        price = Double.valueOf(gpu.get("securePrice").toString());
                    }

                    offer.put("pricePerHour", price);
                    // Store the ID needed for Launch
                    offer.put("launchId", gpu.get("id"));
                    offer.put("region", "GLOBAL"); // RunPod aggregates global stock in this view

                    offers.add(offer);
                }

                // UPDATE CACHE
                priceCache.put("runpod", offers);
                System.out.println("[Pricing] RunPod Sync: " + offers.size() + " SKUs found.");
            }
        } catch (Exception e) {
            // If it fails, keep old cache so UI doesn't break
            System.err.println("[TeraUnit-Error] RunPod Scrape Failed: " + e.getMessage());
            // Print simplified error to avoid console spam
        }
    }
}
