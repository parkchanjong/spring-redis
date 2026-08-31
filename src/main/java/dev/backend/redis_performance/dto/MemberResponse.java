// 회원 정보를 HTTP 응답으로 변환하는 DTO
package dev.backend.redis_performance.dto;

import java.time.LocalDateTime;

import dev.backend.redis_performance.service.dto.MemberDto;

public record MemberResponse(Long id, String name, LocalDateTime createdAt) {

	public static MemberResponse from(MemberDto member) {
		return new MemberResponse(member.id(), member.name(), member.createdAt());
	}
}
