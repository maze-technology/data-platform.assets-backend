package tech.maze.data.assets.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The main entry point of the Spring Boot application.
 *
 * <p>Redis auto-configuration is excluded by default; enable it via
 * {@code maze.sync.session-store=redis} ({@code SyncRedisConfiguration}).
 */
@SpringBootApplication(exclude = {
    DataRedisAutoConfiguration.class,
    DataRedisReactiveAutoConfiguration.class,
    DataRedisRepositoriesAutoConfiguration.class
})
@EnableScheduling
public class App {
  /**
   * The entry point of the Spring Boot application.
   *
   * @param args command-line arguments passed to the application
   */
  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }
}
