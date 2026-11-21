package tech.maze.data.assets.backend.infrastructure.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(SyncRedisConfiguration.class)
@TestPropertySource(properties = "maze.sync.session-store=memory")
class SyncRedisConfigurationDisabledTest {

  @Autowired
  ApplicationContext applicationContext;

  @Test
  void doesNotRegisterRedisBeansWhenSessionStoreIsMemory() {
    assertThat(applicationContext.getBeanNamesForType(StringRedisTemplate.class)).isEmpty();
  }
}
