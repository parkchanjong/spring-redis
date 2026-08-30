// 회원 Controller의 HTTP 응답을 검증하는 MVC 테스트
package dev.backend.redis_performance.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import dev.backend.redis_performance.domain.Member;
import dev.backend.redis_performance.service.ResourceNotFoundException;
import dev.backend.redis_performance.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(dev.backend.redis_performance.controller.MemberController .class)
class MemberControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MemberService memberService;

	@Test
	void createsMember() throws Exception {
		given(memberService.create(anyString())).willReturn(new Member("chan"));

		mockMvc.perform(post("/members")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"chan\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("chan"));
	}

	@Test
	void returnsMemberList() throws Exception {
		given(memberService.findAll()).willReturn(List.of(new Member("chan")));

		mockMvc.perform(get("/members"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("chan"));
	}

	@Test
	void returnsNotFoundForMissingMember() throws Exception {
		given(memberService.findById(1L)).willThrow(new ResourceNotFoundException("Member", 1L));

		mockMvc.perform(get("/members/1"))
			.andExpect(status().isNotFound());
	}
}
