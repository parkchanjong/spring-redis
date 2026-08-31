// 동영상 Redis 캐시 조회 흐름을 검증하는 Service 테스트
package dev.backend.redis_performance.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import dev.backend.redis_performance.domain.VideoDetail;
import dev.backend.redis_performance.repository.VideoRepository;
import dev.backend.redis_performance.service.ResourceNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class VideoCacheServiceTest {

	@Mock
	private VideoRepository videoRepository;

	@Mock
	private RedisTemplate<String, String> redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

	@Test
	void returnsFreshCachedVideoWithoutDatabaseLookup() throws Exception {
		VideoCacheService videoCacheService = videoCacheService(true);
		when(valueOperations.get("video:detail:1")).thenReturn(cacheValue(videoDetail(), Instant.now().plusSeconds(30)));

		VideoDetail result = videoCacheService.findById(1L);

		assertThat(result).isEqualTo(videoDetail());
		verify(videoRepository, never()).findDetailById(any());
	}

	@Test
	void loadsAndCachesVideoWhenLockIsAcquired() {
		VideoCacheService videoCacheService = videoCacheService(true);
		when(valueOperations.get("video:detail:1")).thenReturn(null);
		when(valueOperations.setIfAbsent(eq("video:detail:lock:1"), anyString(), eq(Duration.ofSeconds(10))))
			.thenReturn(true);
		when(videoRepository.findDetailById(1L)).thenReturn(Optional.of(videoDetail()));

		VideoDetail result = videoCacheService.findById(1L);

		assertThat(result).isEqualTo(videoDetail());
		verify(videoRepository).findDetailById(1L);
		verify(valueOperations).set(eq("video:detail:1"), anyString(), eq(Duration.ofSeconds(60)));
	}

	@Test
	void returnsCacheFilledByLockOwnerWithoutDatabaseLookup() throws Exception {
		VideoCacheService videoCacheService = videoCacheService(true);
		when(valueOperations.get("video:detail:1"))
			.thenReturn(null, cacheValue(videoDetail(), Instant.now().plusSeconds(30)));
		when(valueOperations.setIfAbsent(eq("video:detail:lock:1"), anyString(), eq(Duration.ofSeconds(10))))
			.thenReturn(false);

		VideoDetail result = videoCacheService.findById(1L);

		assertThat(result).isEqualTo(videoDetail());
		verify(videoRepository, never()).findDetailById(any());
	}

	@Test
	void returnsStaleVideoAndRefreshesCacheOnce() throws Exception {
		VideoCacheService videoCacheService = videoCacheService(true);
		VideoDetail staleVideo = videoDetail();
		VideoDetail refreshedVideo = new VideoDetail(
			1L, 2L, "redis refreshed", "performance test", 3L, 4L, LocalDateTime.of(2026, 8, 31, 13, 0)
		);
		when(valueOperations.get("video:detail:1")).thenReturn(cacheValue(staleVideo, Instant.now().minusSeconds(1)));
		when(valueOperations.setIfAbsent(eq("video:detail:lock:1"), anyString(), eq(Duration.ofSeconds(10))))
			.thenReturn(true);
		when(videoRepository.findDetailById(1L)).thenReturn(Optional.of(refreshedVideo));

		VideoDetail result = videoCacheService.findById(1L);

		assertThat(result).isEqualTo(staleVideo);
		verify(videoRepository).findDetailById(1L);
		verify(valueOperations).set(eq("video:detail:1"), anyString(), eq(Duration.ofSeconds(60)));
	}

	@Test
	void cachesMissingVideoForShortTtl() {
		VideoCacheService videoCacheService = videoCacheService(true);
		when(valueOperations.get("video:detail:1")).thenReturn(null);
		when(valueOperations.setIfAbsent(eq("video:detail:lock:1"), anyString(), eq(Duration.ofSeconds(10))))
			.thenReturn(true);
		when(videoRepository.findDetailById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> videoCacheService.findById(1L))
			.isInstanceOf(ResourceNotFoundException.class);

		verify(valueOperations).set(eq("video:detail:1"), anyString(), eq(Duration.ofSeconds(5)));
	}

	@Test
	void returnsLoadedVideoWhenLockReleaseFails() {
		VideoCacheService videoCacheService = videoCacheService(true);
		when(valueOperations.get("video:detail:1")).thenReturn(null);
		when(valueOperations.setIfAbsent(eq("video:detail:lock:1"), anyString(), eq(Duration.ofSeconds(10))))
			.thenReturn(true);
		when(videoRepository.findDetailById(1L)).thenReturn(Optional.of(videoDetail()));
		when(redisTemplate.execute(any(), any(), anyString()))
			.thenThrow(new DataAccessResourceFailureException("redis unavailable"));

		VideoDetail result = videoCacheService.findById(1L);

		assertThat(result).isEqualTo(videoDetail());
		verify(videoRepository).findDetailById(1L);
	}

	@Test
	void fallsBackToDatabaseWhenRedisIsUnavailable() {
		VideoCacheService videoCacheService = videoCacheService(true);
		when(valueOperations.get("video:detail:1")).thenThrow(new DataAccessResourceFailureException("redis unavailable"));
		when(videoRepository.findDetailById(1L)).thenReturn(Optional.of(videoDetail()));

		VideoDetail result = videoCacheService.findById(1L);

		assertThat(result).isEqualTo(videoDetail());
		assertThat(meterRegistry.find("video.find.by.id.db.load").counter().count()).isEqualTo(1.0);
	}

	@Test
	void skipsRedisWhenCacheIsDisabled() {
		VideoCacheService videoCacheService = videoCacheService(false);
		when(videoRepository.findDetailById(1L)).thenReturn(Optional.of(videoDetail()));

		VideoDetail result = videoCacheService.findById(1L);

		assertThat(result).isEqualTo(videoDetail());
		verify(redisTemplate, never()).opsForValue();
	}

	private VideoCacheService videoCacheService(boolean cacheEnabled) {
		if (cacheEnabled) {
			when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		}
		return new VideoCacheService(
			videoRepository,
			redisTemplate,
			objectMapper,
			meterRegistry,
			Runnable::run,
			cacheEnabled
		);
	}

	private VideoDetail videoDetail() {
		return new VideoDetail(1L, 2L, "redis", "performance test", 3L, 4L, LocalDateTime.of(2026, 8, 31, 13, 0));
	}

	private String cacheValue(VideoDetail video, Instant freshUntil) throws Exception {
		return objectMapper.writeValueAsString(new CacheValue(video, true, freshUntil));
	}

	private record CacheValue(VideoDetail video, boolean found, Instant freshUntil) {
	}
}
