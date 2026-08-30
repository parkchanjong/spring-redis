// 회원 엔티티의 데이터 접근을 제공하는 Repository
package dev.backend.redis_performance.repository;

import dev.backend.redis_performance.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
