// 동영상 단건 조회의 Cache Stampede 방지 효과를 측정하는 k6 시나리오
import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const videoId = __ENV.VIDEO_ID || '900001';

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
	const response = http.get(`${baseUrl}/videos/${videoId}`);
	if (!check(response, { 'seed video lookup succeeds': (result) => result.status === 200 })) {
		throw new Error(`Seed video ${videoId} is not available. Run k6/seed-video-cache-stampede.sql first.`);
	}
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

function databaseLoadCount() {
	const response = http.get(`${baseUrl}/actuator/prometheus`);
	const match = response.body.match(/^video_find_by_id_db_load_total(?:\{[^}]*\})?\s+([\d.e+-]+)$/m);
	return match ? Number(match[1]) : 0;
}
