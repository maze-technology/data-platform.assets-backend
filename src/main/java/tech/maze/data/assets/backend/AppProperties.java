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
   * Sync/fetch properties. Topic names are wiring concerns — override via
   * {@code ASSETS_SYNC_RESPONSE_TOPIC} / {@code ASSETS_FETCH_REQUEST_TOPIC} /
   * {@code ASSETS_FETCH_RESPONSE_TOPIC}.
   */
  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class Sync {
    /**
     * Fixed Kafka topic for SyncAssetsResponse publication.
     */
    String responseTopic = "assets.sync.response";

    /**
     * Fixed Kafka topic for FetchAssetsRequest fan-out to data providers.
     */
    String fetchRequestTopic = "assets.fetch.request";

    /**
     * Fixed Kafka topic where data providers publish FetchAssetsResponse.
     */
    String fetchResponseTopic = "assets.fetch.response";

    /**
     * Expected data-provider identifiers that must each finish contiguous
     * {@code pageIndex} pages through {@code isLast} before a sync completes.
     * When empty, the first provider that finishes completes the sync (dev mode).
     */
    List<String> expectedDataProviders = new ArrayList<>();

    /**
     * Maximum time to wait for fan-in before emitting TIMEOUT/PARTIAL.
     */
    Duration timeout = Duration.ofMinutes(1);

    /**
     * How often to scan for expired sync deadlines.
     */
    Duration timeoutPollInterval = Duration.ofSeconds(5);

    /**
     * Session store backend: {@code memory} (default) or {@code redis} (Valkey).
     */
    String sessionStore = "memory";
  }
}
