package tech.maze.data.assets.backend.api.eventstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.Value;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import tech.maze.commons.eventstream.EventSender;
import tech.maze.data.assets.backend.domain.models.SyncAssetsResult;
import tech.maze.data.assets.backend.domain.ports.in.SyncAssetsUseCase;
import tech.maze.dtos.assets.events.EventTypes;
import tech.maze.dtos.assets.payloads.FetchAssetsResponse;

@ExtendWith(MockitoExtension.class)
class AssetsEventStreamConfigurationTest {
  @Mock
  private EventSender eventSender;

  @Mock
  private ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider;

  @Mock
  private SyncAssetsUseCase syncAssetsUseCase;

  private AssetsEventStreamConfiguration configuration;

  @BeforeEach
  void setUp() {
    configuration = new AssetsEventStreamConfiguration(
        eventSender,
        meterRegistryProvider,
        syncAssetsUseCase
    );
  }

  @Test
  void skipsUnexpectedTypeForFetchResponseConsumer() {
    Consumer<CloudEvent> consumer = configuration.fetchAssetsResponseConsumer();
    CloudEvent event = CloudEventBuilder.v1()
        .withId("evt-1")
        .withSource(URI.create("urn:test"))
        .withType("unexpected")
        .withData(BytesCloudEventData.wrap(new byte[] {1}))
        .build();

    consumer.accept(event);

    verify(syncAssetsUseCase, never()).onFetchResponse(any(), any(), any(Long.class), any(Boolean.class));
  }

  @Test
  void startsSyncOnSyncAssetsRequest() {
    Consumer<CloudEvent> consumer = configuration.syncAssetsRequestConsumer();
    CloudEvent event = CloudEventBuilder.v1()
        .withId("evt-sync")
        .withSource(URI.create("urn:test"))
        .withType(EventTypes.SYNC_ASSETS_REQUEST)
        .withExtension(EventSender.CORRELATION_ID_EXTENSION, "corr-1")
        .withData(BytesCloudEventData.wrap(com.google.protobuf.Empty.getDefaultInstance().toByteArray()))
        .build();

    when(eventSender.resolveCorrelationId(event)).thenReturn("corr-1");
    when(eventSender.resolveReplyTo(event)).thenReturn("sync-reply");
    when(syncAssetsUseCase.startSync("corr-1", "sync-reply")).thenReturn(true);

    consumer.accept(event);

    verify(syncAssetsUseCase).startSync("corr-1", "sync-reply");
  }

  @Test
  void recordsMetricWhenSyncStartFails() {
    Consumer<CloudEvent> consumer = configuration.syncAssetsRequestConsumer();
    CloudEvent event = CloudEventBuilder.v1()
        .withId("evt-sync")
        .withSource(URI.create("urn:test"))
        .withType(EventTypes.SYNC_ASSETS_REQUEST)
        .withData(BytesCloudEventData.wrap(com.google.protobuf.Empty.getDefaultInstance().toByteArray()))
        .build();

    when(eventSender.resolveCorrelationId(event)).thenReturn("corr-2");
    when(eventSender.resolveReplyTo(event)).thenReturn("sync-reply");
    when(syncAssetsUseCase.startSync("corr-2", "sync-reply")).thenReturn(false);

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    when(meterRegistryProvider.getIfAvailable()).thenReturn(registry);

    consumer.accept(event);

    assertEquals(
        1.0,
        registry.counter(
            "maze.events.reply.failed",
            "eventType",
            EventTypes.SYNC_ASSETS_REQUEST
        ).count()
    );
  }

  @Test
  void dispatchesSyncAssetsResponseWhenFanInCompletes() {
    Consumer<CloudEvent> consumer = configuration.fetchAssetsResponseConsumer();
    FetchAssetsResponse payload = FetchAssetsResponse.newBuilder()
        .setDataProvider(Value.newBuilder().setStringValue("provider-a").build())
        .setIsLast(true)
        .setSkipped(3)
        .build();

    CloudEvent event = CloudEventBuilder.v1()
        .withId("evt-fetch-resp")
        .withSource(URI.create("urn:test"))
        .withType(EventTypes.FETCH_ASSETS_RESPONSE)
        .withExtension(EventSender.CORRELATION_ID_EXTENSION, "corr-3")
        .withData(BytesCloudEventData.wrap(payload.toByteArray()))
        .build();

    when(eventSender.resolveCorrelationId(event)).thenReturn("corr-3");
    when(syncAssetsUseCase.onFetchResponse("corr-3", "provider-a", 3L, true))
        .thenReturn(Optional.of(new SyncAssetsResult(3L, "sync-reply")));
    when(eventSender.send(eq("sync-reply"), any(), eq(EventTypes.SYNC_ASSETS_RESPONSE)))
        .thenReturn(true);

    consumer.accept(event);

    verify(eventSender).send(eq("sync-reply"), any(), eq(EventTypes.SYNC_ASSETS_RESPONSE));
  }
}
