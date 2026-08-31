// 동영상 단건 조회의 Cache Stampede 방지 효과를 측정하는 k6 시나리오
import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
	scenarios: {
		videoCacheStampede: {
			executor: 'per-vu-iterations',
			vus: 200,
			iterations: 1,
			maxDuration: '30s',
			gracefulStop: '5s',
		},
	},
};

export function setup() {
	const member = http.post(`${baseUrl}/members`, JSON.stringify({ name: 'k6-member' }), jsonParams());
	check(member, { 'member created': (response) => response.status === 201 });

	const video = http.post(
		`${baseUrl}/videos`,
		JSON.stringify({ memberId: member.json('id'), title: 'k6-video', description: 'cache stampede test' }),
		jsonParams(),
	);
	check(video, { 'video created': (response) => response.status === 201 });

	const videoId = video.json('id');
	http.get(`${baseUrl}/videos/${videoId}`);
	sleep(31);

	return { videoId, dbLoadBefore: databaseLoadCount() };
}

export default function (data) {
	const response = http.get(`${baseUrl}/videos/${data.videoId}`);
	check(response, { 'video lookup succeeds': (result) => result.status === 200 });
}

export function teardown(data) {
	sleep(2);
	const dbLoadAfter = databaseLoadCount();
	console.log(`video.find.by.id DB load delta: ${dbLoadAfter - data.dbLoadBefore}`);
}

function jsonParams() {
	return { headers: { 'Content-Type': 'application/json' } };
}

function databaseLoadCount() {
	const response = http.get(`${baseUrl}/actuator/prometheus`);
	const match = response.body.match(/^video_find_by_id_db_load_total(?:\{[^}]*\})?\s+([\d.e+-]+)$/m);
	return match ? Number(match[1]) : 0;
}
