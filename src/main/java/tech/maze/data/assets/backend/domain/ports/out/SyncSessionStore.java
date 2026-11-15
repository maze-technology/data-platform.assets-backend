package tech.maze.data.assets.backend.domain.ports.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import tech.maze.data.assets.backend.domain.models.SyncAssetsResult;

/**
 * Multi-pod-safe sync session state (in-memory or Valkey/Redis).
 */
public interface SyncSessionStore {
  /**
   * Creates a session keyed by correlation id with an absolute deadline.
   *
   * @param correlationId correlation id shared with fetch responses
   * @param deadline when the sync should time out
   */
  void create(String correlationId, Instant deadline);

  /**
   * Records one FetchAssetsResponse page and returns a result when the sync finishes.
   *
   * @param correlationId correlation id
   * @param dataProvider provider key
   * @param pageIndex zero-based page index
   * @param last whether this page is the provider's last page
   * @param skipped skipped count from the chunk
   * @return completed/timeout result when this call claims finish; empty otherwise
   */
  Optional<SyncAssetsResult> recordPage(
      String correlationId,
      String dataProvider,
      int pageIndex,
      boolean last,
      long skipped
  );

  /**
   * Correlation ids whose deadline has passed and are not yet claimed done.
   *
   * @param now current time
   * @return expired correlation ids
   */
  List<String> findExpiredCorrelationIds(Instant now);

  /**
   * Claims a timed-out session and builds the timeout/partial result once.
   *
   * @param correlationId correlation id
   * @return result when this caller claimed the finish; empty if already done or missing
   */
  Optional<SyncAssetsResult> claimTimeout(String correlationId);
}
