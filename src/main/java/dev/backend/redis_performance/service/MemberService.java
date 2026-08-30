// 회원 생성과 조회를 처리하는 Service
package dev.backend.redis_performance.service;

import java.util.List;

import dev.backend.redis_performance.domain.Member;
import dev.backend.redis_performance.repository.MemberRepository;
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
	public Member create(String name) {
		return memberRepository.save(new Member(name));
	}

	public List<Member> findAll() {
		return memberRepository.findAll();
	}

	public Member findById(Long id) {
		return memberRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Member", id));
	}
}
