// 동영상 정보를 영속화하는 JPA 엔티티
package dev.backend.redis_performance.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "videos")
public class Video {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false)
	private String title;

	private String description;

	@Column(nullable = false)
	private long viewCount;

	@Column(nullable = false)
	private long likeCount;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Video() {
	}

	public Video(Member member, String title, String description) {
		this.member = member;
		this.title = title;
		this.description = description;
	}

	public Long getId() {
		return id;
	}

	public Member getMember() {
		return member;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public long getViewCount() {
		return viewCount;
	}

	public long getLikeCount() {
		return likeCount;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
