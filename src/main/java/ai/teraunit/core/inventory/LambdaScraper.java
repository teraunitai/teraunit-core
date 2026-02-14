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
public class LambdaScraper implements GpuProviderScraper {

    private final RestClient restClient;
    private final ApplicationEventPublisher publisher;

    @Value("${LAMBDA_API_KEY:missing_key}")
    private String apiKey;

    public LambdaScraper(RestClient restClient, ApplicationEventPublisher publisher) {
        this.restClient = restClient;
        this.publisher = publisher;
    }

    @Override
    @Scheduled(fixedRate = 60000) // The "Heartbeat"
    public void scrape() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("https://cloud.lambdalabs.com")
                    .header("Authorization", "Basic " + apiKey)
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                publisher.publishEvent(new GpuPriceScrapedEvent(ProviderName.LAMBDA, response));
            }
        } catch (Exception e) {
            // In Phase 1, we just log. In Phase 3, this triggers a Failover.
            System.err.println("[TeraUnit-Error] Lambda Scrape Failed: " + e.getMessage());
        }
    }
}
