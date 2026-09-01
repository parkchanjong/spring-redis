// 동영상 단건 조회의 지속 부하에서 캐시 효과를 관측하는 k6 시나리오
import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
	scenarios: {
		videoCacheSustained: {
			executor: 'constant-arrival-rate',
			rate: 50,
			timeUnit: '1s',
			duration: '60s',
			preAllocatedVUs: 50,
			maxVUs: 100,
		},
	},
	thresholds: {
		checks: ['rate>0.99'],
		http_req_failed: ['rate<0.01'],
	},
};

export function setup() {
	const videoId = createSeedVideo();
	const response = http.get(`${baseUrl}/videos/${videoId}`);
	if (!check(response, { 'seed video lookup succeeds': (result) => result.status === 200 })) {
		throw new Error(`Seed video ${videoId} lookup failed with status ${response.status}: ${response.body}`);
	}

	return { videoId, dbLoadBefore: databaseLoadCount() };
}

export default function (data) {
	const response = http.get(`${baseUrl}/videos/${data.videoId}`);
	check(response, { 'video lookup succeeds': (result) => result.status === 200 });
}

export function teardown(data) {
	const dbLoadAfter = databaseLoadCount();
	console.log(`video.find.by.id DB load delta: ${dbLoadAfter - data.dbLoadBefore}`);
}

function createSeedVideo() {
	const member = http.post(`${baseUrl}/members`, JSON.stringify({ name: 'k6-member' }), jsonParams());
	if (!check(member, { 'seed member created': (response) => response.status === 201 })) {
		throw new Error(`Seed member creation failed with status ${member.status}: ${member.body}`);
	}

	const video = http.post(
		`${baseUrl}/videos`,
		JSON.stringify({ memberId: member.json('id'), title: 'k6-video', description: 'sustained cache test' }),
		jsonParams(),
	);
	if (!check(video, { 'seed video created': (response) => response.status === 201 })) {
		throw new Error(`Seed video creation failed with status ${video.status}: ${video.body}`);
	}

	return video.json('id');
}

function jsonParams() {
	return { headers: { 'Content-Type': 'application/json' } };
}

function databaseLoadCount() {
	const response = http.get(`${baseUrl}/actuator/prometheus`);
	const match = response.body.match(/^video_find_by_id_db_load_total(?:\{[^}]*\})?\s+([\d.e+-]+)$/m);
	return match ? Number(match[1]) : 0;
}
