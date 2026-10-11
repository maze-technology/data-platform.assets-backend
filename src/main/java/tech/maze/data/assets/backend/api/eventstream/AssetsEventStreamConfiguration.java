package tech.maze.data.assets.backend.api.eventstream;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Value;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.maze.commons.eventstream.EventSender;
import tech.maze.commons.eventstream.MazeEventProperties;
import tech.maze.data.assets.backend.domain.models.SyncAssetsResult;
import tech.maze.data.assets.backend.domain.ports.in.SyncAssetsUseCase;
import tech.maze.dtos.assets.events.EventTypes;
import tech.maze.dtos.assets.payloads.FetchAssetsResponse;
import tech.maze.dtos.assets.payloads.SyncAssetsResponse;

/**
 * Event stream configuration for assets Sync/Fetch orchestration.
 *
 * <p>Flow: SyncAssetsRequest → fan-out FetchAssetsRequest on a fixed topic →
 * correlate FetchAssetsResponse by {@code correlationid} → SyncAssetsResponse.
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@EnableConfigurationProperties(MazeEventProperties.class)
@Slf4j
public class AssetsEventStreamConfiguration {
  EventSender eventSender;
  ObjectProvider<MeterRegistry> meterRegistryProvider;
  SyncAssetsUseCase syncAssetsUseCase;

  /**
   * Handles SyncAssetsRequest: starts correlation and fans out FetchAssetsRequest.
   *
   * @return a consumer for CloudEvents
   */
  @Bean
  public Consumer<CloudEvent> syncAssetsRequestConsumer() {
    return event -> {
      if (!EventTypes.SYNC_ASSETS_REQUEST.equals(event.getType())) {
        log.warn(
            "Skipping event type {} (expected {})",
            event.getType(),
            EventTypes.SYNC_ASSETS_REQUEST
        );
        return;
      }

      final String correlationId = Optional.ofNullable(eventSender.resolveCorrelationId(event))
          .filter(id -> !id.isBlank())
          .orElseGet(() -> event.getId() == null ? UUID.randomUUID().toString() : event.getId());
      final String replyTo = eventSender.resolveReplyTo(event);

      final boolean started = syncAssetsUseCase.startSync(correlationId, replyTo);
      if (!started) {
        incrementFailure(EventTypes.SYNC_ASSETS_REQUEST);
      }
    };
  }

  /**
   * Handles FetchAssetsResponse fan-in for in-flight sync sessions.
   *
   * @return a consumer for CloudEvents
   */
  @Bean
  public Consumer<CloudEvent> fetchAssetsResponseConsumer() {
    return event -> {
      if (!EventTypes.FETCH_ASSETS_RESPONSE.equals(event.getType())) {
        log.warn(
            "Skipping event type {} (expected {})",
            event.getType(),
            EventTypes.FETCH_ASSETS_RESPONSE
        );
        return;
      }

      final FetchAssetsResponse response;
      try {
        response = parseFetchResponse(event);
      } catch (InvalidProtocolBufferException ex) {
        log.error("Failed to parse FetchAssetsResponse for event {}", event.getId(), ex);
        incrementFailure(EventTypes.FETCH_ASSETS_RESPONSE);
        return;
      }

      final String correlationId = eventSender.resolveCorrelationId(event);
      final String dataProvider = extractDataProvider(response);
      final Optional<SyncAssetsResult> completed = syncAssetsUseCase.onFetchResponse(
          correlationId,
          dataProvider,
          response.getSkipped(),
          response.getIsLast()
      );

      completed.ifPresent(result -> {
        final String replyTo = result.replyTo();
        if (replyTo == null || replyTo.isBlank()) {
          log.info(
              "Sync {} completed with skipped={} (no replyto)",
              correlationId,
              result.skipped()
          );
          return;
        }

        final SyncAssetsResponse syncResponse = SyncAssetsResponse.newBuilder()
            .setSkipped(result.skipped())
            .build();
        final boolean sent = eventSender.send(
            replyTo,
            syncResponse,
            EventTypes.SYNC_ASSETS_RESPONSE
        );
        if (!sent) {
          log.error("Failed to dispatch SyncAssetsResponse for correlation {}", correlationId);
          incrementFailure(EventTypes.SYNC_ASSETS_RESPONSE);
        }
      });
    };
  }

  private FetchAssetsResponse parseFetchResponse(CloudEvent event)
      throws InvalidProtocolBufferException {
    final CloudEventData data = event.getData();
    if (data == null) {
      return FetchAssetsResponse.getDefaultInstance();
    }
    return FetchAssetsResponse.parseFrom(data.toBytes());
  }

  private String extractDataProvider(FetchAssetsResponse response) {
    if (response.hasDataProvider()) {
      final Value value = response.getDataProvider();
      if (value.hasStringValue()) {
        return value.getStringValue();
      }
      if (value.hasNumberValue()) {
        return Double.toString(value.getNumberValue());
      }
    }
    if (response.hasAssetMetaDatas() && response.getAssetMetaDatas().hasDataProviderId()) {
      final Value value = response.getAssetMetaDatas().getDataProviderId();
      if (value.hasStringValue()) {
        return value.getStringValue();
      }
    }
    return "unknown";
  }

  private void incrementFailure(String eventType) {
    final MeterRegistry registry = meterRegistryProvider.getIfAvailable();
    if (registry != null) {
      registry.counter("maze.events.reply.failed", "eventType", eventType).increment();
    }
  }
}
