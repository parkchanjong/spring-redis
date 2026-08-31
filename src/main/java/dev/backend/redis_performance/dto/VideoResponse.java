// 동영상 정보를 HTTP 응답으로 변환하는 DTO
package dev.backend.redis_performance.dto;

import java.time.LocalDateTime;

import dev.backend.redis_performance.service.dto.VideoDto;

public record VideoResponse(
	Long id,
	Long memberId,
	String title,
	String description,
	long viewCount,
	long likeCount,
	LocalDateTime createdAt
) {

	public static VideoResponse from(VideoDto video) {
		return new VideoResponse(
			video.id(),
			video.memberId(),
			video.title(),
			video.description(),
			video.viewCount(),
			video.likeCount(),
			video.createdAt()
		);
	}
}
