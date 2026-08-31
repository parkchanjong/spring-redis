-- k6 동영상 캐시 스탬피드 테스트용 고정 데이터를 준비하는 쿼리
INSERT INTO members (id, name, created_at)
VALUES (900001, 'k6-cache-test-member', UTC_TIMESTAMP()) AS new_member
ON DUPLICATE KEY UPDATE
	name = new_member.name;

INSERT INTO videos (id, member_id, title, description, view_count, like_count, created_at)
VALUES (900001, 900001, 'k6-cache-test-video', 'cache stampede test', 0, 0, UTC_TIMESTAMP()) AS new_video
ON DUPLICATE KEY UPDATE
	member_id = new_video.member_id,
	title = new_video.title,
	description = new_video.description,
	view_count = new_video.view_count,
	like_count = new_video.like_count;
