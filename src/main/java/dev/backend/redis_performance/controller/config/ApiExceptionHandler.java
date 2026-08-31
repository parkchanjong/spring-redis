// REST API 예외를 HTTP 응답으로 변환하는 Controller Advice
package dev.backend.redis_performance.controller.config;

import dev.backend.redis_performance.service.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Void> handleNotFound(ResourceNotFoundException exception) {
		return ResponseEntity.notFound().build();
	}
}
