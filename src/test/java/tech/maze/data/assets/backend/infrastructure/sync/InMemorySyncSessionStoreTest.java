package tech.maze.data.assets.backend.infrastructure.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.maze.data.assets.backend.AppProperties;
import tech.maze.data.assets.backend.domain.models.SyncAssetsResult;
import tech.maze.dtos.assets.enums.SyncStatus;

class InMemorySyncSessionStoreTest {
  private AppProperties appProperties;
  private InMemorySyncSessionStore store;

  @BeforeEach
  void setUp() {
    appProperties = new AppProperties();
    appProperties.getSync().setExpectedDataProviders(List.of("dp-a", "dp-b"));
    appProperties.getSync().setTimeout(Duration.ofMinutes(1));
    store = new InMemorySyncSessionStore(appProperties);
  }

  @Test
  void claimTimeoutIsSingleWinner() {
    store.create("corr-1", Instant.now().plusSeconds(60));
    // One provider finished; sync still waiting on dp-b.
    assertThat(store.recordPage("corr-1", "dp-a", 0, true, 1)).isEmpty();

    Optional<SyncAssetsResult> first = store.claimTimeout("corr-1");
    Optional<SyncAssetsResult> second = store.claimTimeout("corr-1");

    assertThat(first).isPresent();
    assertThat(first.get().status()).isEqualTo(SyncStatus.PARTIAL);
    assertThat(first.get().incompleteProviders()).containsExactly("dp-b");
    assertThat(first.get().skipped()).isEqualTo(1);
    assertThat(second).isEmpty();
  }

  @Test
  void timeoutWithNoProvidersIsTimeoutStatus() {
    store.create("corr-1", Instant.now().minusSeconds(1));
    Optional<SyncAssetsResult> result = store.claimTimeout("corr-1");
    assertThat(result).isPresent();
    assertThat(result.get().status()).isEqualTo(SyncStatus.TIMEOUT);
    assertThat(result.get().incompleteProviders()).containsExactly("dp-a", "dp-b");
  }

  @Test
  void findExpiredListsOnlyOpenPastDeadline() {
    store.create("open", Instant.now().minusSeconds(5));
    store.create("future", Instant.now().plusSeconds(60));
    assertThat(store.findExpiredCorrelationIds(Instant.now())).containsExactly("open");
  }

  @Test
  void outOfOrderPagesCompleteWhenContiguous() {
    store.create("corr-1", Instant.now().plusSeconds(60));
    assertThat(store.recordPage("corr-1", "dp-a", 1, true, 0)).isEmpty();
    assertThat(store.recordPage("corr-1", "dp-b", 0, true, 0)).isEmpty();
    assertThat(store.recordPage("corr-1", "dp-a", 0, false, 0)).isPresent()
        .get()
        .extracting(SyncAssetsResult::status)
        .isEqualTo(SyncStatus.COMPLETED);
  }
}
