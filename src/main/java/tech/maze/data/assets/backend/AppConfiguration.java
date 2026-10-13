package tech.maze.data.assets.backend;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tech.maze.commons.CommonsConfiguration;

/**
 * Configuration class for the application.
 */
@Configuration
@Import({CommonsConfiguration.class})
@ComponentScan("tech.maze.data.assets.backend")
@EnableConfigurationProperties(AppProperties.class)
public class AppConfiguration {

}
