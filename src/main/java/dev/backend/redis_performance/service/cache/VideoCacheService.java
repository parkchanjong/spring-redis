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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class VideoCacheService {

	private static final Logger log = LoggerFactory.getLogger(VideoCacheService.class);
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
			log.debug("동영상 캐시가 비활성화되어 DB에서 조회합니다. videoId={}", id);
			return loadFromDatabase(id);
		}

		try {
			VideoCacheEntry cachedEntry = readCache(id);
			if (cachedEntry == null) {
				log.debug("동영상 캐시 미스입니다. videoId={}", id);
				return loadAfterLock(id);
			}
			if (!cachedEntry.found()) {
				log.debug("동영상 음수 캐시 히트입니다. videoId={}", id);
				throw new ResourceNotFoundException("Video", id);
			}
			if (cachedEntry.isFresh()) {
				log.debug("동영상 신선 캐시 히트입니다. videoId={}", id);
				return cachedEntry.video();
			}

			log.debug("동영상 만료 캐시 히트로 비동기 갱신을 시작합니다. videoId={}", id);
			refreshStaleCache(id);
			return cachedEntry.video();
		} catch (DataAccessException exception) {
			log.warn("Redis 접근에 실패해 DB 조회로 대체합니다. videoId={}", id, exception);
			return loadFromDatabase(id);
		}
	}

	private VideoDetail loadAfterLock(Long id) {
		String token = UUID.randomUUID().toString();
		if (tryLock(id, token)) {
			log.debug("동영상 캐시 잠금을 획득했습니다. videoId={}", id);
			try {
				VideoCacheEntry cachedEntry = readCache(id);
				if (cachedEntry != null) {
					log.debug("잠금 획득 후 동영상 캐시가 채워진 것을 확인했습니다. videoId={}", id);
					return cachedVideo(id, cachedEntry);
				}
				return loadAndCache(id);
			} finally {
				unlockQuietly(id, token);
			}
		}

		log.debug("다른 요청이 동영상 캐시 잠금을 보유해 캐시를 대기합니다. videoId={}", id);
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
				log.debug("잠금 보유 요청이 동영상 캐시를 채운 것을 확인했습니다. videoId={}", id);
				return cachedVideo(id, cachedEntry);
			}
		}
		log.debug("동영상 캐시 잠금 대기가 끝나 DB 조회로 대체합니다. videoId={}", id);
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
			log.debug("동영상을 DB에서 조회해 캐시에 저장했습니다. videoId={}", id);
			return video;
		} catch (ResourceNotFoundException exception) {
			writeCache(id, VideoCacheEntry.missing(), MISSING_VIDEO_CACHE_TTL);
			log.debug("존재하지 않는 동영상을 음수 캐시에 저장했습니다. videoId={}", id);
			throw exception;
		}
	}

	private void refreshStaleCache(Long id) {
		String token = UUID.randomUUID().toString();
		try {
			if (!tryLock(id, token)) {
				log.debug("만료된 동영상 캐시 갱신이 이미 진행 중입니다. videoId={}", id);
				return;
			}
			log.debug("만료된 동영상 캐시 갱신을 예약했습니다. videoId={}", id);
			cacheRefreshExecutor.execute(() -> refreshCache(id, token));
		} catch (RuntimeException exception) {
			log.warn("만료된 동영상 캐시 갱신 예약에 실패했습니다. videoId={}", id, exception);
			unlockQuietly(id, token);
		}
	}

	private void refreshCache(Long id, String token) {
		try {
			log.debug("만료된 동영상 캐시를 갱신합니다. videoId={}", id);
			loadAndCache(id);
		} catch (ResourceNotFoundException exception) {
			// 음수 캐시는 loadAndCache에서 저장한다.
		} catch (DataAccessException exception) {
			// stale 값은 다음 요청에서 다시 갱신을 시도한다.
			log.warn("만료된 동영상 캐시 갱신에 실패했습니다. videoId={}", id, exception);
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
			log.warn("유효하지 않은 동영상 캐시 항목을 제거합니다. videoId={}", id);
		} catch (JacksonException exception) {
			// 잘못된 캐시 값은 DB 조회로 복구한다.
			log.warn("동영상 캐시 항목 역직렬화에 실패해 제거합니다. videoId={}", id, exception);
		}
		redisTemplate.delete(cacheKey(id));
		return null;
	}

	private void writeCache(Long id, VideoCacheEntry entry, Duration ttl) {
		try {
			redisTemplate.opsForValue().set(cacheKey(id), objectMapper.writeValueAsString(entry), ttl);
		} catch (JacksonException | DataAccessException exception) {
			// 캐시 저장 실패는 조회 결과에 영향을 주지 않는다.
			log.warn("동영상 캐시 항목 저장에 실패했습니다. videoId={}", id, exception);
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
			log.warn("동영상 캐시 잠금 해제에 실패했습니다. TTL 만료를 기다립니다. videoId={}", id, exception);
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
