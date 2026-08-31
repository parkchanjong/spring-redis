// 동영상 서비스의 반환 데이터를 표현하는 DTO
package dev.backend.redis_performance.service.dto;

import java.time.LocalDateTime;

import dev.backend.redis_performance.domain.Video;
import dev.backend.redis_performance.domain.VideoDetail;

public record VideoDto(
	Long id,
	Long memberId,
	String title,
	String description,
	long viewCount,
	long likeCount,
	LocalDateTime createdAt
) {

	public static VideoDto from(Video video) {
		return new VideoDto(
			video.getId(),
			video.getMember().getId(),
			video.getTitle(),
			video.getDescription(),
			video.getViewCount(),
			video.getLikeCount(),
			video.getCreatedAt()
		);
	}

	public static VideoDto from(VideoDetail video) {
		return new VideoDto(
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
