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
public class RunPodScraper implements GpuProviderScraper {

    private final RestClient restClient;
    private final ApplicationEventPublisher publisher;

    @Value("${RUNPOD_API_KEY:missing_key}")
    private String apiKey;

    public RunPodScraper(RestClient restClient, ApplicationEventPublisher publisher) {
        this.restClient = restClient;
        this.publisher = publisher;
    }

    @Override
    @Scheduled(fixedRate = 60000)
    public void scrape() {
        try {
            String query = """
                {
                  gpuTypes {
                    id
                    displayName
                    communityPrice
                    securePrice
                  }
                }
                """;

            String endpoint = "https://api.runpod.io/graphql";

            var response = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of("query", query))
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("data")) {
                publisher.publishEvent(
                        new GpuPriceScrapedEvent(ProviderName.RUNPOD, response)
                );
                System.out.println("[TeraUnit-Pulse] RunPod pricing scraped successfully.");
            } else {
                System.err.println("[TeraUnit-Warn] RunPod returned null or invalid response.");
            }

        } catch (Exception e) {
            System.err.println("[TeraUnit-Error] RunPod Scrape Failed: " + e.getMessage());
        }
    }
}
