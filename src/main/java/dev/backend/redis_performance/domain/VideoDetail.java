// 동영상 단건 조회에 사용하는 캐시 가능한 읽기 모델
package dev.backend.redis_performance.domain;

import java.time.LocalDateTime;

public record VideoDetail(
	Long id,
	Long memberId,
	String title,
	String description,
	long viewCount,
	long likeCount,
	LocalDateTime createdAt
) {
}
