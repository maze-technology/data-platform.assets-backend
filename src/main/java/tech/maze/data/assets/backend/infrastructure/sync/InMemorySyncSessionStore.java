package tech.maze.data.assets.backend.infrastructure.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tech.maze.data.assets.backend.AppProperties;
import tech.maze.data.assets.backend.domain.models.SyncAssetsResult;
import tech.maze.data.assets.backend.domain.ports.out.SyncSessionStore;
import tech.maze.dtos.assets.enums.SyncStatus;

/**
 * Process-local sync session store with contiguous pageIndex + isLast barrier.
 */
@Component
@ConditionalOnProperty(
    prefix = "maze.sync",
    name = "session-store",
    havingValue = "memory",
    matchIfMissing = true
)
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class InMemorySyncSessionStore implements SyncSessionStore {
  AppProperties appProperties;

  Map<String, Session> sessions = new ConcurrentHashMap<>();

  @Override
  public void create(String correlationId, Instant deadline) {
    sessions.put(correlationId, new Session(deadline));
  }

  @Override
  public Optional<SyncAssetsResult> recordPage(
      String correlationId,
      String dataProvider,
      int pageIndex,
      boolean last,
      long skipped
  ) {
    final Session session = sessions.get(correlationId);
    if (session == null || session.done.get()) {
      return Optional.empty();
    }

    if (Instant.now().isAfter(session.deadline)) {
      return claimTimeout(correlationId);
    }

    if (skipped > 0) {
      session.skipped.addAndGet(skipped);
    }

    if (dataProvider != null && !dataProvider.isBlank()) {
      final ProviderProgress progress = session.providers.computeIfAbsent(
          dataProvider,
          ignored -> new ProviderProgress()
      );
      progress.pages.add(pageIndex);
      if (last) {
        progress.lastIndex = Math.max(progress.lastIndex, pageIndex);
      }
    }

    if (!isComplete(session)) {
      return Optional.empty();
    }
    return tryClaim(correlationId, session, false);
  }

  @Override
  public List<String> findExpiredCorrelationIds(Instant now) {
    final List<String> expired = new ArrayList<>();
    for (Map.Entry<String, Session> entry : sessions.entrySet()) {
      final Session session = entry.getValue();
      if (!session.done.get() && !now.isBefore(session.deadline)) {
        expired.add(entry.getKey());
      }
    }
    return expired;
  }

  @Override
  public Optional<SyncAssetsResult> claimTimeout(String correlationId) {
    final Session session = sessions.get(correlationId);
    if (session == null) {
      return Optional.empty();
    }
    return tryClaim(correlationId, session, true);
  }

  private Optional<SyncAssetsResult> tryClaim(
      String correlationId,
      Session session,
      boolean timedOut
  ) {
    if (!session.done.compareAndSet(false, true)) {
      return Optional.empty();
    }
    final SyncAssetsResult result = timedOut
        ? buildTimeoutResult(session)
        : SyncAssetsResult.completed(session.skipped.get());
    sessions.remove(correlationId);
    return Optional.of(result);
  }

  private SyncAssetsResult buildTimeoutResult(Session session) {
    final List<String> incomplete = incompleteProviders(session);
    final boolean anyComplete = completedProviders(session).size() > 0;
    final SyncStatus status = anyComplete ? SyncStatus.PARTIAL : SyncStatus.TIMEOUT;
    return new SyncAssetsResult(session.skipped.get(), status, incomplete);
  }

  private boolean isComplete(Session session) {
    final var expected = appProperties.getSync().getExpectedDataProviders();
    final Set<String> completed = completedProviders(session);
    if (expected == null || expected.isEmpty()) {
      return !completed.isEmpty();
    }
    return completed.containsAll(expected);
  }

  private Set<String> completedProviders(Session session) {
    final Set<String> completed = new HashSet<>();
    for (Map.Entry<String, ProviderProgress> entry : session.providers.entrySet()) {
      if (isProviderComplete(entry.getValue())) {
        completed.add(entry.getKey());
      }
    }
    return completed;
  }

  private List<String> incompleteProviders(Session session) {
    final var expected = appProperties.getSync().getExpectedDataProviders();
    final Set<String> completed = completedProviders(session);
    if (expected == null || expected.isEmpty()) {
      return completed.isEmpty()
          ? List.copyOf(session.providers.keySet())
          : List.of();
    }
    final List<String> incomplete = new ArrayList<>();
    for (String provider : expected) {
      if (!completed.contains(provider)) {
        incomplete.add(provider);
      }
    }
    return List.copyOf(incomplete);
  }

  static boolean isProviderComplete(ProviderProgress progress) {
    if (progress.lastIndex < 0) {
      return false;
    }
    for (int i = 0; i <= progress.lastIndex; i++) {
      if (!progress.pages.contains(i)) {
        return false;
      }
    }
    return true;
  }

  private static final class Session {
    final Instant deadline;
    final AtomicLong skipped = new AtomicLong();
    final AtomicBoolean done = new AtomicBoolean(false);
    final Map<String, ProviderProgress> providers = new ConcurrentHashMap<>();

    Session(Instant deadline) {
      this.deadline = deadline;
    }
  }

  static final class ProviderProgress {
    final Set<Integer> pages = ConcurrentHashMap.newKeySet();
    volatile int lastIndex = -1;
  }
}
