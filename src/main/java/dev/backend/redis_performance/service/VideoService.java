// 동영상 생성과 조회를 처리하는 Service
package dev.backend.redis_performance.service;

import java.util.List;

import dev.backend.redis_performance.domain.Member;
import dev.backend.redis_performance.domain.Video;
import dev.backend.redis_performance.repository.MemberRepository;
import dev.backend.redis_performance.repository.VideoRepository;
import dev.backend.redis_performance.service.cache.VideoCacheService;
import dev.backend.redis_performance.service.dto.VideoDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VideoService {

	private final MemberRepository memberRepository;
	private final VideoRepository videoRepository;
	private final VideoCacheService videoCacheService;

	public VideoService(
		MemberRepository memberRepository,
		VideoRepository videoRepository,
		VideoCacheService videoCacheService
	) {
		this.memberRepository = memberRepository;
		this.videoRepository = videoRepository;
		this.videoCacheService = videoCacheService;
	}

	@Transactional
	public VideoDto create(Long memberId, String title, String description) {
		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
		return VideoDto.from(videoRepository.save(new Video(member, title, description)));
	}

	public List<VideoDto> findAll() {
		return videoRepository.findAll().stream()
			.map(VideoDto::from)
			.toList();
	}

	public VideoDto findById(Long id) {
		return VideoDto.from(videoCacheService.findById(id));
	}
}
