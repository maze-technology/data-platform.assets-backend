package tech.maze.data.assets.backend.domain.usecases;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.maze.commons.eventstream.EventSender;
import tech.maze.data.assets.backend.AppProperties;
import tech.maze.data.assets.backend.domain.models.SyncAssetsResult;
import tech.maze.data.assets.backend.domain.ports.in.SyncAssetsUseCase;
import tech.maze.data.assets.backend.domain.ports.out.SyncSessionStore;
import tech.maze.dtos.assets.events.EventTypes;
import tech.maze.dtos.assets.payloads.FetchAssetsRequest;

/**
 * SyncAssets fan-out/fan-in using a shared session store and pageIndex barrier.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class SyncAssetsUseCaseImpl implements SyncAssetsUseCase {
  EventSender eventSender;
  AppProperties appProperties;
  SyncSessionStore syncSessionStore;

  @Override
  public boolean startSync(String correlationId) {
    if (correlationId == null || correlationId.isBlank()) {
      throw new IllegalArgumentException("correlationId is required");
    }

    final Duration timeout = Optional.ofNullable(appProperties.getSync().getTimeout())
        .orElse(Duration.ofMinutes(1));
    syncSessionStore.create(correlationId, Instant.now().plus(timeout));

    final String topic = appProperties.getSync().getFetchRequestTopic();
    final boolean sent = eventSender.send(
        topic,
        FetchAssetsRequest.getDefaultInstance(),
        EventTypes.FETCH_ASSETS_REQUEST,
        Map.of(EventSender.CORRELATION_ID_EXTENSION, correlationId)
    );

    if (!sent) {
      log.error("Failed to fan-out FetchAssetsRequest for correlation {}", correlationId);
    }
    return sent;
  }

  @Override
  public Optional<SyncAssetsResult> onFetchResponse(
      String correlationId,
      String dataProvider,
      int pageIndex,
      long skipped,
      boolean last
  ) {
    if (correlationId == null || correlationId.isBlank()) {
      return Optional.empty();
    }
    return syncSessionStore.recordPage(correlationId, dataProvider, pageIndex, last, skipped);
  }
}
