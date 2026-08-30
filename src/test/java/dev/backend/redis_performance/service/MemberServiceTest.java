// 회원 Service의 생성과 조회를 검증하는 테스트
package dev.backend.redis_performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import dev.backend.redis_performance.domain.Member;
import dev.backend.redis_performance.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

	@Mock
	private MemberRepository memberRepository;

	@InjectMocks
	private MemberService memberService;

	@Test
	void createsMember() {
		when(memberRepository.save(org.mockito.ArgumentMatchers.any(Member.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		Member member = memberService.create("chan");

		assertThat(member.getName()).isEqualTo("chan");
		verify(memberRepository).save(org.mockito.ArgumentMatchers.any(Member.class));
	}

	@Test
	void throwsNotFoundWhenMemberDoesNotExist() {
		when(memberRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> memberService.findById(1L))
			.isInstanceOf(ResourceNotFoundException.class);
	}
}
