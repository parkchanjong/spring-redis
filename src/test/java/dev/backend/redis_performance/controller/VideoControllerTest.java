// 동영상 Controller의 HTTP 응답을 검증하는 MVC 테스트
package dev.backend.redis_performance.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import dev.backend.redis_performance.domain.Member;
import dev.backend.redis_performance.domain.Video;
import dev.backend.redis_performance.service.ResourceNotFoundException;
import dev.backend.redis_performance.service.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VideoController.class)
class VideoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VideoService videoService;

	@Test
	void createsVideo() throws Exception {
		given(videoService.create(anyLong(), anyString(), anyString()))
			.willReturn(new Video(new Member("chan"), "redis", "performance test"));

		mockMvc.perform(post("/videos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"memberId\":1,\"title\":\"redis\",\"description\":\"performance test\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("redis"))
			.andExpect(jsonPath("$.viewCount").value(0))
			.andExpect(jsonPath("$.likeCount").value(0));
	}

	@Test
	void returnsVideoList() throws Exception {
		given(videoService.findAll()).willReturn(List.of(new Video(new Member("chan"), "redis", "performance test")));

		mockMvc.perform(get("/videos"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].title").value("redis"));
	}

	@Test
	void returnsNotFoundForMissingVideo() throws Exception {
		given(videoService.findById(1L)).willThrow(new ResourceNotFoundException("Video", 1L));

		mockMvc.perform(get("/videos/1"))
			.andExpect(status().isNotFound());
	}
}
