package tech.maze.data.assets.backend.domain.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.maze.commons.eventstream.EventSender;
import tech.maze.data.assets.backend.AppProperties;
import tech.maze.data.assets.backend.domain.models.SyncAssetsResult;
import tech.maze.dtos.assets.events.EventTypes;
import tech.maze.dtos.assets.payloads.FetchAssetsRequest;

@ExtendWith(MockitoExtension.class)
class SyncAssetsUseCaseImplTest {
  @Mock
  private EventSender eventSender;

  private AppProperties appProperties;
  private SyncAssetsUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    appProperties = new AppProperties();
    appProperties.getSync().setExpectedDataProviders(List.of("dp-a", "dp-b"));
    useCase = new SyncAssetsUseCaseImpl(eventSender, appProperties);
  }

  @Test
  void startSyncFansOutFetchRequest() {
    when(eventSender.send(
        eq("assets.fetch.request"),
        any(FetchAssetsRequest.class),
        eq(EventTypes.FETCH_ASSETS_REQUEST),
        anyMap()
    )).thenReturn(true);

    assertThat(useCase.startSync("corr-1", "reply-topic")).isTrue();
    verify(eventSender).send(
        eq("assets.fetch.request"),
        any(FetchAssetsRequest.class),
        eq(EventTypes.FETCH_ASSETS_REQUEST),
        anyMap()
    );
  }

  @Test
  void completesOnlyAfterAllExpectedProvidersReportLast() {
    when(eventSender.send(any(), any(), any(), anyMap())).thenReturn(true);
    useCase.startSync("corr-1", "reply-topic");

    assertThat(useCase.onFetchResponse("corr-1", "dp-a", 1, true)).isEmpty();

    Optional<SyncAssetsResult> result = useCase.onFetchResponse("corr-1", "dp-b", 2, true);
    assertThat(result).isPresent();
    assertThat(result.get().skipped()).isEqualTo(3);
    assertThat(result.get().replyTo()).isEqualTo("reply-topic");
  }
}
