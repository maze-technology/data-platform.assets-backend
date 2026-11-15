package tech.maze.data.assets.backend.domain.ports.in;

import java.util.Optional;
import tech.maze.data.assets.backend.domain.models.SyncAssetsResult;

/**
 * Orchestrates SyncAssets: fan-out FetchAssetsRequest and correlate responses.
 */
public interface SyncAssetsUseCase {
  /**
   * Starts a sync session and fans out FetchAssetsRequest on the fixed topic.
   *
   * @param correlationId correlation id shared by fetch responses
   * @return true when the fan-out was published
   */
  boolean startSync(String correlationId);

  /**
   * Records a FetchAssetsResponse chunk for an in-flight sync.
   *
   * @param correlationId correlation id from the CloudEvent
   * @param dataProvider provider identifier that produced the chunk
   * @param pageIndex zero-based page index within the provider stream
   * @param skipped skipped count from the chunk
   * @param last whether this chunk is the provider's last page
   * @return finished result when the sync completes or times out on this call
   */
  Optional<SyncAssetsResult> onFetchResponse(
      String correlationId,
      String dataProvider,
      int pageIndex,
      long skipped,
      boolean last
  );
}
