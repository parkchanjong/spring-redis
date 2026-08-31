// 회원 생성과 조회를 처리하는 Service
package dev.backend.redis_performance.service;

import java.util.List;

import dev.backend.redis_performance.domain.Member;
import dev.backend.redis_performance.repository.MemberRepository;
import dev.backend.redis_performance.service.dto.MemberDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;

	public MemberService(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	@Transactional
	public MemberDto create(String name) {
		return MemberDto.from(memberRepository.save(new Member(name)));
	}

	public List<MemberDto> findAll() {
		return memberRepository.findAll().stream()
			.map(MemberDto::from)
			.toList();
	}

	public MemberDto findById(Long id) {
		return memberRepository.findById(id)
			.map(MemberDto::from)
			.orElseThrow(() -> new ResourceNotFoundException("Member", id));
	}
}
