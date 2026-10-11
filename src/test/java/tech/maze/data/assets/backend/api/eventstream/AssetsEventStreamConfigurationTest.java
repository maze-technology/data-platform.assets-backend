package tech.maze.data.assets.backend.api.eventstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import tech.maze.dtos.assets.payloads.SyncAssetsResponse;

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
  void skipsUnexpectedTypeForSyncConsumer() {
    Consumer<CloudEvent> consumer = configuration.syncAssetsRequestConsumer();
    CloudEvent event = CloudEventBuilder.v1()
        .withId("evt-1")
        .withSource(URI.create("urn:test"))
        .withType("unexpected")
        .withData(BytesCloudEventData.wrap(new byte[] {1}))
        .build();

    consumer.accept(event);

    verify(syncAssetsUseCase, never()).startSync(anyString(), any());
  }

  @Test
  void startsSyncWhenReplyToPresent() {
    Consumer<CloudEvent> consumer = configuration.syncAssetsRequestConsumer();
    CloudEvent event = CloudEventBuilder.v1()
        .withId("evt-sync")
        .withSource(URI.create("urn:test"))
        .withType(EventTypes.SYNC_ASSETS_REQUEST)
        .withData(BytesCloudEventData.wrap(new byte[0]))
        .build();

    when(eventSender.resolveCorrelationId(event)).thenReturn("corr-1");
    when(eventSender.resolveReplyTo(event)).thenReturn("reply-topic");
    when(syncAssetsUseCase.startSync("corr-1", "reply-topic")).thenReturn(true);

    consumer.accept(event);

    verify(syncAssetsUseCase).startSync("corr-1", "reply-topic");
  }

  @Test
  void completesSyncAndRepliesOnFetchFanIn() {
    Consumer<CloudEvent> consumer = configuration.fetchAssetsResponseConsumer();
    FetchAssetsResponse response = FetchAssetsResponse.newBuilder()
        .setIsLast(true)
        .setSkipped(2)
        .setDataProvider(Value.newBuilder().setStringValue("dp-1").build())
        .build();
    CloudEvent event = CloudEventBuilder.v1()
        .withId("evt-fetch")
        .withSource(URI.create("urn:test"))
        .withType(EventTypes.FETCH_ASSETS_RESPONSE)
        .withData(BytesCloudEventData.wrap(response.toByteArray()))
        .build();

    when(eventSender.resolveCorrelationId(event)).thenReturn("corr-1");
    when(syncAssetsUseCase.onFetchResponse("corr-1", "dp-1", 2L, true))
        .thenReturn(Optional.of(new SyncAssetsResult(2L, "reply-topic")));
    when(eventSender.send(eq("reply-topic"), any(SyncAssetsResponse.class), eq(EventTypes.SYNC_ASSETS_RESPONSE)))
        .thenReturn(true);

    consumer.accept(event);

    verify(eventSender).send(eq("reply-topic"), any(SyncAssetsResponse.class), eq(EventTypes.SYNC_ASSETS_RESPONSE));
  }

  @Test
  void recordsMetricWhenSyncStartFails() {
    Consumer<CloudEvent> consumer = configuration.syncAssetsRequestConsumer();
    CloudEvent event = CloudEventBuilder.v1()
        .withId("evt-sync")
        .withSource(URI.create("urn:test"))
        .withType(EventTypes.SYNC_ASSETS_REQUEST)
        .withData(BytesCloudEventData.wrap(new byte[0]))
        .build();

    when(eventSender.resolveCorrelationId(event)).thenReturn(null);
    when(eventSender.resolveReplyTo(event)).thenReturn("reply-topic");
    when(syncAssetsUseCase.startSync(anyString(), eq("reply-topic"))).thenReturn(false);

    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    when(meterRegistryProvider.getIfAvailable()).thenReturn(registry);

    consumer.accept(event);

    assertEquals(
        1.0,
        registry.counter("maze.events.reply.failed", "eventType", EventTypes.SYNC_ASSETS_REQUEST).count()
    );
    verify(syncAssetsUseCase, never()).onFetchResponse(anyString(), anyString(), anyLong(), anyBoolean());
  }
}
