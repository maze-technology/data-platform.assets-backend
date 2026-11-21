package tech.maze.data.assets.backend.infrastructure.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(SyncRedisConfiguration.class)
@TestPropertySource(properties = {
    "maze.sync.session-store=redis",
    "spring.data.redis.host=127.0.0.1",
    "spring.data.redis.port=6379",
    "spring.data.redis.username=default",
    "spring.data.redis.password=secret"
})
class SyncRedisConfigurationTest {

  @Autowired
  ApplicationContext applicationContext;

  @Test
  void providesStringRedisTemplateWhenSessionStoreIsRedis() {
    assertThat(applicationContext.getBean(RedisConnectionFactory.class)).isNotNull();
    assertThat(applicationContext.getBean(StringRedisTemplate.class)).isNotNull();
  }
}
