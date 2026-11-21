package tech.maze.data.assets.backend.infrastructure.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Provides Redis/Valkey beans when {@code maze.sync.session-store=redis}.
 *
 * <p>{@link org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration} stays
 * excluded from {@code App} so memory mode never needs a Redis host. Re-importing that
 * auto-configuration via {@code @ImportAutoConfiguration} does not undo
 * {@code @SpringBootApplication(exclude=…)}, so beans are defined explicitly here from
 * {@code spring.data.redis.*} (including {@code SPRING_DATA_REDIS_*} env bindings).
 */
@Configuration
@ConditionalOnProperty(prefix = "maze.sync", name = "session-store", havingValue = "redis")
public class SyncRedisConfiguration {

  @Bean
  @ConditionalOnMissingBean(RedisConnectionFactory.class)
  LettuceConnectionFactory redisConnectionFactory(
      @Value("${spring.data.redis.host:localhost}") String host,
      @Value("${spring.data.redis.port:6379}") int port,
      @Value("${spring.data.redis.database:0}") int database,
      @Value("${spring.data.redis.username:}") String username,
      @Value("${spring.data.redis.password:}") String password
  ) {
    final RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
    config.setDatabase(database);
    if (username != null && !username.isBlank()) {
      config.setUsername(username);
    }
    if (password != null && !password.isBlank()) {
      config.setPassword(RedisPassword.of(password));
    }
    return new LettuceConnectionFactory(config);
  }

  @Bean
  @ConditionalOnMissingBean(StringRedisTemplate.class)
  StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
    return new StringRedisTemplate(connectionFactory);
  }
}
