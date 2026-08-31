// 동영상 엔티티의 데이터 접근을 제공하는 Repository
package dev.backend.redis_performance.repository;

import java.util.Optional;

import dev.backend.redis_performance.domain.Video;
import dev.backend.redis_performance.domain.VideoDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, Long> {

	@Query("""
		select new dev.backend.redis_performance.domain.VideoDetail(
			video.id, member.id, video.title, video.description,
			video.viewCount, video.likeCount, video.createdAt
		)
		from Video video
		join video.member member
		where video.id = :id
		""")
	Optional<VideoDetail> findDetailById(@Param("id") Long id);
}
