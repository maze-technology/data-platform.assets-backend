package tech.maze.data.assets.backend.infrastructure.sync;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.maze.data.assets.backend.domain.models.SyncAssetsResult;
import tech.maze.data.assets.backend.domain.ports.out.SyncSessionStore;
import tech.maze.dtos.assets.enums.SyncStatus;

@ExtendWith(MockitoExtension.class)
class SyncTimeoutPollerTest {
  @Mock
  private SyncSessionStore syncSessionStore;
  @Mock
  private SyncAssetsResponsePublisher publisher;
  @InjectMocks
  private SyncTimeoutPoller poller;

  @Test
  void publishesClaimedTimeouts() {
    when(syncSessionStore.findExpiredCorrelationIds(org.mockito.ArgumentMatchers.any(Instant.class)))
        .thenReturn(List.of("corr-1"));
    final SyncAssetsResult result = new SyncAssetsResult(
        0L,
        SyncStatus.TIMEOUT,
        List.of("dp-a")
    );
    when(syncSessionStore.claimTimeout("corr-1")).thenReturn(Optional.of(result));

    poller.pollExpiredSessions();

    verify(publisher).publish(eq("corr-1"), eq(result));
  }

  @Test
  void skipsWhenAnotherPodAlreadyClaimed() {
    when(syncSessionStore.findExpiredCorrelationIds(org.mockito.ArgumentMatchers.any(Instant.class)))
        .thenReturn(List.of("corr-1"));
    when(syncSessionStore.claimTimeout("corr-1")).thenReturn(Optional.empty());

    poller.pollExpiredSessions();

    verifyNoInteractions(publisher);
  }
}
