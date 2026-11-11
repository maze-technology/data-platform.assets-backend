package tech.maze.data.assets.backend.domain.models;

/**
 * Result of a completed SyncAssets fan-in.
 *
 * @param skipped total skipped assets across data providers
 */
public record SyncAssetsResult(long skipped) {}
