// 동영상 생성과 조회를 처리하는 Service
package dev.backend.redis_performance.service;

import java.util.List;

import dev.backend.redis_performance.domain.Member;
import dev.backend.redis_performance.domain.Video;
import dev.backend.redis_performance.repository.MemberRepository;
import dev.backend.redis_performance.repository.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VideoService {

	private final MemberRepository memberRepository;
	private final VideoRepository videoRepository;

	public VideoService(MemberRepository memberRepository, VideoRepository videoRepository) {
		this.memberRepository = memberRepository;
		this.videoRepository = videoRepository;
	}

	@Transactional
	public Video create(Long memberId, String title, String description) {
		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
		return videoRepository.save(new Video(member, title, description));
	}

	public List<Video> findAll() {
		return videoRepository.findAll();
	}

	public Video findById(Long id) {
		return videoRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Video", id));
	}
}
