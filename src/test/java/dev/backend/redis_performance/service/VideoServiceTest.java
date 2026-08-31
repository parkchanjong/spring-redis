// 동영상 조회 캐시와 생성 동작을 검증하는 Service 테스트
package dev.backend.redis_performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import dev.backend.redis_performance.domain.Member;
import dev.backend.redis_performance.domain.Video;
import dev.backend.redis_performance.domain.VideoDetail;
import dev.backend.redis_performance.repository.MemberRepository;
import dev.backend.redis_performance.repository.VideoRepository;
import dev.backend.redis_performance.service.cache.VideoCacheService;
import dev.backend.redis_performance.service.dto.VideoDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private VideoRepository videoRepository;

	@Mock
	private VideoCacheService videoCacheService;

	@Test
	void createsVideoForExistingMember() {
		VideoService videoService = videoService();
		Member member = new Member("chan");
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
		when(videoRepository.save(any(Video.class))).thenAnswer(invocation -> invocation.getArgument(0));

		VideoDto video = videoService.create(1L, "redis", "performance test");

		ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);

		assertThat(video.memberId()).isNull();
		assertThat(video.title()).isEqualTo("redis");
		assertThat(video.viewCount()).isZero();
		assertThat(video.likeCount()).isZero();
		verify(videoRepository).save(videoCaptor.capture());
		assertThat(videoCaptor.getValue().getMember()).isSameAs(member);
	}

	@Test
	void returnsVideoDtos() {
		VideoService videoService = videoService();
		when(videoRepository.findAll()).thenReturn(List.of(new Video(new Member("chan"), "redis", "performance test")));

		List<VideoDto> videos = videoService.findAll();

		assertThat(videos).extracting(VideoDto::title).containsExactly("redis");
	}

	@Test
	void throwsNotFoundWhenVideoMemberDoesNotExist() {
		VideoService videoService = videoService();
		when(memberRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> videoService.create(1L, "redis", "performance test"))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void delegatesSingleVideoLookupToCacheService() {
		VideoService videoService = videoService();
		VideoDetail videoDetail = videoDetail();
		when(videoCacheService.findById(1L)).thenReturn(videoDetail);

		VideoDto result = videoService.findById(1L);

		assertThat(result).isEqualTo(VideoDto.from(videoDetail));
		verify(videoCacheService).findById(1L);
	}

	private VideoService videoService() {
		return new VideoService(memberRepository, videoRepository, videoCacheService);
	}

	private VideoDetail videoDetail() {
		return new VideoDetail(1L, 2L, "redis", "performance test", 3L, 4L, LocalDateTime.of(2026, 8, 31, 13, 0));
	}
}
