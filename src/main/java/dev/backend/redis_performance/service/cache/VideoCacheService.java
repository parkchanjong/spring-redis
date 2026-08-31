// 동영상 단건 조회의 Redis 캐시 흐름을 처리하는 Service
package dev.backend.redis_performance.service.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.backend.redis_performance.domain.VideoDetail;
import dev.backend.redis_performance.repository.VideoRepository;
import dev.backend.redis_performance.service.ResourceNotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class VideoCacheService {

	private static final Duration VIDEO_CACHE_TTL = Duration.ofSeconds(30);
	private static final Duration VIDEO_CACHE_STALE_TTL = Duration.ofSeconds(30);
	private static final Duration MISSING_VIDEO_CACHE_TTL = Duration.ofSeconds(5);
	private static final Duration LOCK_TTL = Duration.ofSeconds(10);
	private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(1);
	private static final Duration LOCK_WAIT_INTERVAL = Duration.ofMillis(10);
	private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
		"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0",
		Long.class
	);

	private final VideoRepository videoRepository;
	private final RedisTemplate<String, String> redisTemplate;
	private final ObjectMapper objectMapper;
	private final Counter dbLoadCounter;
	private final TaskExecutor cacheRefreshExecutor;
	private final boolean cacheEnabled;

	public VideoCacheService(
		VideoRepository videoRepository,
		RedisTemplate<String, String> redisTemplate,
		ObjectMapper objectMapper,
		MeterRegistry meterRegistry,
		@Qualifier("applicationTaskExecutor") TaskExecutor cacheRefreshExecutor,
		@Value("${video.cache.enabled:true}") boolean cacheEnabled
	) {
		this.videoRepository = videoRepository;
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.dbLoadCounter = meterRegistry.counter("video.find.by.id.db.load");
		this.cacheRefreshExecutor = cacheRefreshExecutor;
		this.cacheEnabled = cacheEnabled;
	}

	public VideoDetail findById(Long id) {
		if (!cacheEnabled) {
			return loadFromDatabase(id);
		}

		try {
			VideoCacheEntry cachedEntry = readCache(id);
			if (cachedEntry == null) {
				return loadAfterLock(id);
			}
			if (!cachedEntry.found()) {
				throw new ResourceNotFoundException("Video", id);
			}
			if (cachedEntry.isFresh()) {
				return cachedEntry.video();
			}

			refreshStaleCache(id);
			return cachedEntry.video();
		} catch (DataAccessException exception) {
			return loadFromDatabase(id);
		}
	}

	private VideoDetail loadAfterLock(Long id) {
		String token = UUID.randomUUID().toString();
		if (tryLock(id, token)) {
			try {
				VideoCacheEntry cachedEntry = readCache(id);
				if (cachedEntry != null) {
					return cachedVideo(id, cachedEntry);
				}
				return loadAndCache(id);
			} finally {
				unlockQuietly(id, token);
			}
		}

		return waitForCacheOrLoad(id);
	}

	private VideoDetail waitForCacheOrLoad(Long id) {
		Instant deadline = Instant.now().plus(LOCK_WAIT_TIMEOUT);
		while (Instant.now().isBefore(deadline)) {
			try {
				Thread.sleep(LOCK_WAIT_INTERVAL);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				break;
			}

			VideoCacheEntry cachedEntry = readCache(id);
			if (cachedEntry != null) {
				return cachedVideo(id, cachedEntry);
			}
		}
		return loadFromDatabase(id);
	}

	private VideoDetail cachedVideo(Long id, VideoCacheEntry cachedEntry) {
		if (!cachedEntry.found()) {
			throw new ResourceNotFoundException("Video", id);
		}
		return cachedEntry.video();
	}

	private VideoDetail loadAndCache(Long id) {
		try {
			VideoDetail video = loadFromDatabase(id);
			writeCache(id, VideoCacheEntry.found(video), VIDEO_CACHE_TTL.plus(VIDEO_CACHE_STALE_TTL));
			return video;
		} catch (ResourceNotFoundException exception) {
			writeCache(id, VideoCacheEntry.missing(), MISSING_VIDEO_CACHE_TTL);
			throw exception;
		}
	}

	private void refreshStaleCache(Long id) {
		String token = UUID.randomUUID().toString();
		try {
			if (!tryLock(id, token)) {
				return;
			}
			cacheRefreshExecutor.execute(() -> refreshCache(id, token));
		} catch (RuntimeException exception) {
			unlockQuietly(id, token);
		}
	}

	private void refreshCache(Long id, String token) {
		try {
			loadAndCache(id);
		} catch (ResourceNotFoundException exception) {
			// 음수 캐시는 loadAndCache에서 저장한다.
		} catch (DataAccessException exception) {
			// stale 값은 다음 요청에서 다시 갱신을 시도한다.
		} finally {
			unlockQuietly(id, token);
		}
	}

	private VideoDetail loadFromDatabase(Long id) {
		dbLoadCounter.increment();
		return videoRepository.findDetailById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Video", id));
	}

	private VideoCacheEntry readCache(Long id) {
		String serialized = redisTemplate.opsForValue().get(cacheKey(id));
		if (serialized == null) {
			return null;
		}

		try {
			VideoCacheEntry cachedEntry = objectMapper.readValue(serialized, VideoCacheEntry.class);
			if (cachedEntry.isValid()) {
				return cachedEntry;
			}
		} catch (JacksonException exception) {
			// 잘못된 캐시 값은 DB 조회로 복구한다.
		}
		redisTemplate.delete(cacheKey(id));
		return null;
	}

	private void writeCache(Long id, VideoCacheEntry entry, Duration ttl) {
		try {
			redisTemplate.opsForValue().set(cacheKey(id), objectMapper.writeValueAsString(entry), ttl);
		} catch (JacksonException | DataAccessException exception) {
			// 캐시 저장 실패는 조회 결과에 영향을 주지 않는다.
		}
	}

	private boolean tryLock(Long id, String token) {
		return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey(id), token, LOCK_TTL));
	}

	private void unlock(Long id, String token) {
		redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey(id)), token);
	}

	private void unlockQuietly(Long id, String token) {
		try {
			unlock(id, token);
		} catch (DataAccessException exception) {
			// Lock TTL이 남은 경우에도 자동 만료된다.
		}
	}

	private String cacheKey(Long id) {
		return "video:detail:" + id;
	}

	private String lockKey(Long id) {
		return "video:detail:lock:" + id;
	}

	private record VideoCacheEntry(VideoDetail video, boolean found, Instant freshUntil) {

		private static VideoCacheEntry found(VideoDetail video) {
			return new VideoCacheEntry(video, true, Instant.now().plus(VIDEO_CACHE_TTL));
		}

		private static VideoCacheEntry missing() {
			return new VideoCacheEntry(null, false, null);
		}

		private boolean isFresh() {
			return freshUntil != null && freshUntil.isAfter(Instant.now());
		}

		private boolean isValid() {
			return found ? video != null && freshUntil != null : video == null;
		}
	}
}
