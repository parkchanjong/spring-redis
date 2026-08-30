// 동영상 Service의 회원 검증과 생성을 검증하는 테스트
package dev.backend.redis_performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import dev.backend.redis_performance.domain.Member;
import dev.backend.redis_performance.domain.Video;
import dev.backend.redis_performance.repository.MemberRepository;
import dev.backend.redis_performance.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private VideoRepository videoRepository;

	@InjectMocks
	private VideoService videoService;

	@Test
	void createsVideoForExistingMember() {
		Member member = new Member("chan");
		when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
		when(videoRepository.save(org.mockito.ArgumentMatchers.any(Video.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		Video video = videoService.create(1L, "redis", "performance test");

		assertThat(video.getMember()).isSameAs(member);
		assertThat(video.getViewCount()).isZero();
		assertThat(video.getLikeCount()).isZero();
		verify(videoRepository).save(org.mockito.ArgumentMatchers.any(Video.class));
	}

	@Test
	void throwsNotFoundWhenVideoMemberDoesNotExist() {
		when(memberRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> videoService.create(1L, "redis", "performance test"))
			.isInstanceOf(ResourceNotFoundException.class);
	}
}
