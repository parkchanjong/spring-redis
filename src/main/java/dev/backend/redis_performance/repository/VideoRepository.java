// 동영상 엔티티의 데이터 접근을 제공하는 Repository
package dev.backend.redis_performance.repository;

import dev.backend.redis_performance.domain.Video;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {
}
