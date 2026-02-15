package ai.teraunit.core.inventory;

import ai.teraunit.core.common.GpuPriceScrapedEvent;
import ai.teraunit.core.common.ProviderName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Component
public class VastScraper implements GpuProviderScraper {

    private final RestClient restClient;
    private final ApplicationEventPublisher publisher;

    @Value("${VAST_API_KEY:missing_key}")
    private String apiKey;

    public VastScraper(RestClient restClient, ApplicationEventPublisher publisher) {
        this.restClient = restClient;
        this.publisher = publisher;
    }

    @Override
    @Scheduled(fixedRate = 60000)
    public void scrape() {
        try {
            // FIXED: Use the API endpoint, not the UI URL
            String endpoint = "https://console.vast.ai/api/v0/bundles/";

            // Filter: Verified hosts, On-Demand (Rentable), Available
            Map<String, Object> query = Map.of(
                    "verified", Map.of("eq", true),
                    "type", "on-demand",
                    "rentable", Map.of("eq", true)
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(query)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("offers")) {
                publisher.publishEvent(
                        new GpuPriceScrapedEvent(ProviderName.VAST, response)
                );
                System.out.println("[TeraUnit-Pulse] Vast.ai API scraped successfully.");
            }
        } catch (Exception e) {
            System.err.println("[TeraUnit-Error] Vast Scrape Failed: " + e.getMessage());
        }
    }
}