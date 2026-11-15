package tech.maze.data.assets.backend.infrastructure.sync;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.maze.data.assets.backend.domain.models.SyncAssetsResult;
import tech.maze.data.assets.backend.domain.ports.out.SyncSessionStore;

/**
 * Claims and publishes SyncAssetsResponse for sessions past maze.sync.timeout.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class SyncTimeoutPoller {
  SyncSessionStore syncSessionStore;
  SyncAssetsResponsePublisher publisher;

  /**
   * Scans expired sync deadlines and publishes TIMEOUT/PARTIAL once per session.
   */
  @Scheduled(fixedDelayString = "${maze.sync.timeout-poll-interval:5s}")
  public void pollExpiredSessions() {
    final List<String> expired = syncSessionStore.findExpiredCorrelationIds(Instant.now());
    for (String correlationId : expired) {
      final Optional<SyncAssetsResult> result = syncSessionStore.claimTimeout(correlationId);
      result.ifPresent(finish -> {
        log.warn(
            "Sync session {} finished with {} (incomplete={})",
            correlationId,
            finish.status(),
            finish.incompleteProviders()
        );
        publisher.publish(correlationId, finish);
      });
    }
  }
}
