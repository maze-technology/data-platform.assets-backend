package tech.maze.data.assets.backend.infrastructure.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tech.maze.data.assets.backend.AppProperties;
import tech.maze.data.assets.backend.domain.models.SyncAssetsResult;
import tech.maze.data.assets.backend.domain.ports.out.SyncSessionStore;
import tech.maze.dtos.assets.enums.SyncStatus;

/**
 * Valkey/Redis-backed sync session store for multi-pod fan-in.
 *
 * <p>Keys (TTL = timeout × 2):
 * <ul>
 *   <li>{@code sync:session:{id}} hash — deadlineMs, skipped</li>
 *   <li>{@code sync:session:{id}:prov:{dp}:pages} set — page indices</li>
 *   <li>{@code sync:session:{id}:prov:{dp}:last} string — last page index when isLast seen</li>
 *   <li>{@code sync:deadlines} zset — score = deadline epoch ms</li>
 *   <li>{@code sync:done:{id}} SETNX claim for single finish publish</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "maze.sync", name = "session-store", havingValue = "redis")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class RedisSyncSessionStore implements SyncSessionStore {
  static final String DEADLINES_KEY = "sync:deadlines";

  StringRedisTemplate redis;
  AppProperties appProperties;

  @Override
  public void create(String correlationId, Instant deadline) {
    final String sessionKey = sessionKey(correlationId);
    final long deadlineMs = deadline.toEpochMilli();
    redis.opsForHash().put(sessionKey, "deadlineMs", Long.toString(deadlineMs));
    redis.opsForHash().put(sessionKey, "skipped", "0");
    redis.opsForZSet().add(DEADLINES_KEY, correlationId, deadlineMs);
    expireSession(correlationId);
  }

  @Override
  public Optional<SyncAssetsResult> recordPage(
      String correlationId,
      String dataProvider,
      int pageIndex,
      boolean last,
      long skipped
  ) {
    final String sessionKey = sessionKey(correlationId);
    if (!Boolean.TRUE.equals(redis.hasKey(sessionKey))) {
      return Optional.empty();
    }
    if (isDone(correlationId)) {
      return Optional.empty();
    }

    final Long deadlineMs = readLong(redis.opsForHash().get(sessionKey, "deadlineMs"));
    if (deadlineMs != null && Instant.now().toEpochMilli() > deadlineMs) {
      return claimTimeout(correlationId);
    }

    if (skipped > 0) {
      redis.opsForHash().increment(sessionKey, "skipped", skipped);
    }

    if (dataProvider != null && !dataProvider.isBlank()) {
      redis.opsForSet().add(providersKey(correlationId), dataProvider);
      redis.opsForSet().add(pagesKey(correlationId, dataProvider), Integer.toString(pageIndex));
      if (last) {
        final String lastKey = lastKey(correlationId, dataProvider);
        final Long existing = readLong(redis.opsForValue().get(lastKey));
        final long next = existing == null ? pageIndex : Math.max(existing, pageIndex);
        redis.opsForValue().set(lastKey, Long.toString(next));
      }
      expireSession(correlationId);
      expireKey(pagesKey(correlationId, dataProvider));
      if (last) {
        expireKey(lastKey(correlationId, dataProvider));
      }
    }

    if (!isComplete(correlationId)) {
      return Optional.empty();
    }
    return tryClaim(correlationId, false);
  }

  @Override
  public List<String> findExpiredCorrelationIds(Instant now) {
    final Set<String> members = redis.opsForZSet().rangeByScore(
        DEADLINES_KEY,
        0,
        now.toEpochMilli()
    );
    if (members == null || members.isEmpty()) {
      return List.of();
    }
    final List<String> expired = new ArrayList<>();
    for (String correlationId : members) {
      if (!isDone(correlationId) && Boolean.TRUE.equals(redis.hasKey(sessionKey(correlationId)))) {
        expired.add(correlationId);
      }
    }
    return expired;
  }

  @Override
  public Optional<SyncAssetsResult> claimTimeout(String correlationId) {
    return tryClaim(correlationId, true);
  }

  private Optional<SyncAssetsResult> tryClaim(String correlationId, boolean timedOut) {
    final Boolean claimed = redis.opsForValue().setIfAbsent(
        doneKey(correlationId),
        "1",
        ttl()
    );
    if (!Boolean.TRUE.equals(claimed)) {
      return Optional.empty();
    }

    final String sessionKey = sessionKey(correlationId);
    final long skipped = Optional.ofNullable(
        readLong(redis.opsForHash().get(sessionKey, "skipped"))
    ).orElse(0L);

    final SyncAssetsResult result;
    if (timedOut) {
      final List<String> incomplete = incompleteProviders(correlationId);
      final boolean anyComplete = !completedProviders(correlationId).isEmpty();
      result = new SyncAssetsResult(
          skipped,
          anyComplete ? SyncStatus.PARTIAL : SyncStatus.TIMEOUT,
          incomplete
      );
    } else {
      result = SyncAssetsResult.completed(skipped);
    }

    cleanup(correlationId);
    return Optional.of(result);
  }

  private void cleanup(String correlationId) {
    redis.opsForZSet().remove(DEADLINES_KEY, correlationId);
    final Set<String> providers = knownProviders(correlationId);
    redis.delete(sessionKey(correlationId));
    for (String provider : providers) {
      redis.delete(pagesKey(correlationId, provider));
      redis.delete(lastKey(correlationId, provider));
    }
    // Keep done key until TTL so late pages cannot re-finish.
  }

  private boolean isComplete(String correlationId) {
    final var expected = appProperties.getSync().getExpectedDataProviders();
    final Set<String> completed = completedProviders(correlationId);
    if (expected == null || expected.isEmpty()) {
      return !completed.isEmpty();
    }
    return completed.containsAll(expected);
  }

  private Set<String> completedProviders(String correlationId) {
    final Set<String> completed = new HashSet<>();
    for (String provider : knownProviders(correlationId)) {
      if (isProviderComplete(correlationId, provider)) {
        completed.add(provider);
      }
    }
    return completed;
  }

  private List<String> incompleteProviders(String correlationId) {
    final var expected = appProperties.getSync().getExpectedDataProviders();
    final Set<String> completed = completedProviders(correlationId);
    if (expected == null || expected.isEmpty()) {
      return completed.isEmpty() ? List.copyOf(knownProviders(correlationId)) : List.of();
    }
    final List<String> incomplete = new ArrayList<>();
    for (String provider : expected) {
      if (!completed.contains(provider)) {
        incomplete.add(provider);
      }
    }
    return List.copyOf(incomplete);
  }

  private Set<String> knownProviders(String correlationId) {
    final Set<String> providers = new HashSet<>();
    final var expected = appProperties.getSync().getExpectedDataProviders();
    if (expected != null) {
      providers.addAll(expected);
    }
    // Discover providers that already wrote pages via SCAN of last keys is heavy;
    // track providers in a set on the session.
    final Set<String> tracked = redis.opsForSet().members(providersKey(correlationId));
    if (tracked != null) {
      providers.addAll(tracked);
    }
    return providers;
  }

  private boolean isProviderComplete(String correlationId, String provider) {
    final Long lastIndex = readLong(redis.opsForValue().get(lastKey(correlationId, provider)));
    if (lastIndex == null || lastIndex < 0) {
      return false;
    }
    final Set<String> pages = redis.opsForSet().members(pagesKey(correlationId, provider));
    if (pages == null) {
      return false;
    }
    for (int i = 0; i <= lastIndex; i++) {
      if (!pages.contains(Integer.toString(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean isDone(String correlationId) {
    return Boolean.TRUE.equals(redis.hasKey(doneKey(correlationId)));
  }

  private void expireSession(String correlationId) {
    redis.expire(sessionKey(correlationId), ttl());
    redis.expire(providersKey(correlationId), ttl());
  }

  private void expireKey(String key) {
    redis.expire(key, ttl());
  }

  private Duration ttl() {
    final Duration timeout = appProperties.getSync().getTimeout();
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      return Duration.ofMinutes(2);
    }
    return timeout.multipliedBy(2);
  }

  private static Long readLong(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return Long.parseLong(value.toString());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  static String sessionKey(String correlationId) {
    return "sync:session:" + correlationId;
  }

  static String providersKey(String correlationId) {
    return "sync:session:" + correlationId + ":providers";
  }

  static String pagesKey(String correlationId, String provider) {
    return "sync:session:" + correlationId + ":prov:" + provider + ":pages";
  }

  static String lastKey(String correlationId, String provider) {
    return "sync:session:" + correlationId + ":prov:" + provider + ":last";
  }

  static String doneKey(String correlationId) {
    return "sync:done:" + correlationId;
  }
}
