package tech.maze.data.assets.backend.domain.models;

import java.util.List;
import tech.maze.dtos.assets.enums.SyncStatus;

/**
 * Result of a finished SyncAssets fan-in (completed, timed out, or partial).
 *
 * @param skipped total skipped assets across data providers
 * @param status completion status
 * @param incompleteProviders providers that did not finish (TIMEOUT/PARTIAL)
 */
public record SyncAssetsResult(
    long skipped,
    SyncStatus status,
    List<String> incompleteProviders
) {
  /**
   * Convenience for a fully completed sync.
   *
   * @param skipped total skipped
   * @return completed result
   */
  public static SyncAssetsResult completed(long skipped) {
    return new SyncAssetsResult(skipped, SyncStatus.COMPLETED, List.of());
  }
}
