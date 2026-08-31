// 회원 서비스의 반환 데이터를 표현하는 DTO
package dev.backend.redis_performance.service.dto;

import java.time.LocalDateTime;

import dev.backend.redis_performance.domain.Member;

public record MemberDto(Long id, String name, LocalDateTime createdAt) {

	public static MemberDto from(Member member) {
		return new MemberDto(member.getId(), member.getName(), member.getCreatedAt());
	}
}
