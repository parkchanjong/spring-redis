// 회원 생성과 조회 HTTP 요청을 처리하는 Controller
package dev.backend.redis_performance.controller;

import java.util.List;

import dev.backend.redis_performance.dto.MemberCreateRequest;
import dev.backend.redis_performance.dto.MemberResponse;
import dev.backend.redis_performance.service.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
public class MemberController {

	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	@PostMapping
	public ResponseEntity<MemberResponse> create(@RequestBody MemberCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(MemberResponse.from(memberService.create(request.name())));
	}

	@GetMapping
	public List<MemberResponse> findAll() {
		return memberService.findAll().stream().map(MemberResponse::from).toList();
	}

	@GetMapping("/{id}")
	public MemberResponse findById(@PathVariable Long id) {
		return MemberResponse.from(memberService.findById(id));
	}

}
