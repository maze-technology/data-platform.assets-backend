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
   * @param replyTo destination for the eventual SyncAssetsResponse
   * @return true when the fetch request was published
   */
  boolean startSync(String correlationId, String replyTo);

  /**
   * Records a FetchAssetsResponse chunk for an in-flight sync.
   *
   * @param correlationId correlation id
   * @param dataProvider data provider identifier
   * @param skipped skipped count from this chunk
   * @param last whether this is the last chunk from the provider
   * @return completed result when fan-in is done, otherwise empty
   */
  Optional<SyncAssetsResult> onFetchResponse(
      String correlationId,
      String dataProvider,
      long skipped,
      boolean last
  );
}
