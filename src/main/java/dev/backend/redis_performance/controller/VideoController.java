// 동영상 생성과 조회 HTTP 요청을 처리하는 Controller
package dev.backend.redis_performance.controller;

import java.time.LocalDateTime;
import java.util.List;

import dev.backend.redis_performance.domain.Video;
import dev.backend.redis_performance.service.VideoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/videos")
public class VideoController {

	private final VideoService videoService;

	public VideoController(VideoService videoService) {
		this.videoService = videoService;
	}

	@PostMapping
	public ResponseEntity<VideoResponse> create(@RequestBody VideoCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(VideoResponse.from(videoService.create(request.memberId(), request.title(), request.description())));
	}

	@GetMapping
	public List<VideoResponse> findAll() {
		return videoService.findAll().stream().map(VideoResponse::from).toList();
	}

	@GetMapping("/{id}")
	public VideoResponse findById(@PathVariable Long id) {
		return VideoResponse.from(videoService.findById(id));
	}

	public record VideoCreateRequest(Long memberId, String title, String description) {
	}

	public record VideoResponse(
		Long id,
		Long memberId,
		String title,
		String description,
		long viewCount,
		long likeCount,
		LocalDateTime createdAt
	) {

		private static VideoResponse from(Video video) {
			return new VideoResponse(
				video.getId(),
				video.getMember().getId(),
				video.getTitle(),
				video.getDescription(),
				video.getViewCount(),
				video.getLikeCount(),
				video.getCreatedAt()
			);
		}
	}
}
