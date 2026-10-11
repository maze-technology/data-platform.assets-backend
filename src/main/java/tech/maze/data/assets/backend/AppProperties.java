package tech.maze.data.assets.backend;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the application.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties(prefix = AppProperties.PREFIX)
public class AppProperties {
  public static final String PREFIX = "maze";

  String name;
  int assetsPerPage = 50;

  /**
   * Sync/fetch fan-out configuration.
   */
  Sync sync = new Sync();

  /**
   * Sync/fetch properties.
   */
  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class Sync {
    /**
     * Fixed Kafka topic for FetchAssetsRequest fan-out to data providers.
     */
    String fetchRequestTopic = "assets.fetch.request";

    /**
     * Fixed Kafka topic where data providers publish FetchAssetsResponse.
     */
    String fetchResponseTopic = "assets.fetch.response";

    /**
     * Expected data-provider identifiers that must each emit {@code isLast=true}
     * before a SyncAssetsRequest is considered complete. When empty, the first
     * distinct provider that reports {@code isLast} completes the sync (dev mode).
     */
    List<String> expectedDataProviders = new ArrayList<>();

    /**
     * Maximum time to wait for fan-in before failing the sync session.
     */
    Duration timeout = Duration.ofMinutes(5);
  }
}
