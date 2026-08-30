// 조회 대상이 없을 때 사용하는 예외
package dev.backend.redis_performance.service;

public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String resource, Long id) {
		super(resource + " not found: " + id);
	}
}
