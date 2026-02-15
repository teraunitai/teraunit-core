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
            // Vast.ai industrial marketplace endpoint
            String endpoint = "https://console.vast.ai";

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    // FIX: Use the simple string URI. RestClient handles the URL.
                    .uri(endpoint)
                    .header("Accept", "application/json")
                    // VAST uses Bearer Token for the API Key
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                publisher.publishEvent(new GpuPriceScrapedEvent(ProviderName.VAST, response));
                System.out.println("[TeraUnit-Pulse] Vast.ai marketplace scraped successfully.");
            }
        } catch (Exception e) {
            System.err.println("[TeraUnit-Error] Vast Scrape Failed: " + e.getMessage());
        }
    }

}
