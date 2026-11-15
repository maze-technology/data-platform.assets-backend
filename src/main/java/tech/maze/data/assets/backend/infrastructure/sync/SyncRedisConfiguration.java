package tech.maze.data.assets.backend.infrastructure.sync;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Redis/Valkey auto-configuration only when maze.sync.session-store=redis.
 */
@Configuration
@ConditionalOnProperty(prefix = "maze.sync", name = "session-store", havingValue = "redis")
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
public class SyncRedisConfiguration {}
