package tech.maze.data.assets.backend.domain.models;

/**
 * Result of a completed SyncAssets fan-in.
 *
 * @param skipped total skipped assets across data providers
 * @param replyTo destination for SyncAssetsResponse
 */
public record SyncAssetsResult(long skipped, String replyTo) {}
