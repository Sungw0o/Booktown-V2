// [우선순위 3 / 노션 축 01·05] AI 요약 Job 생성 — 트랜잭션 분리 & Rate Limit·중복 Job
// POST /books/{bookId}/summaries   (JWT 필요)
// ⚠️⚠️ 매우 주의:
//   - 실제 Gemini API 호출과 비동기 Job 을 발생시켜 비용/외부 rate limit 에 직접 영향.
//   - 절대 높은 VU 로 돌리지 말 것. 목적은 "부하량"이 아니라
//     (a) AI Job 폭주 시 커넥션 풀 점유로 조회 API 가 지연되는지(축01),
//     (b) 동일 book 중복 요청이 rate limit / 중복 Job 로 방어되는지(축05) 관찰.
//   - 권장: K6_VUS=3~5, K6_DURATION=30s, 그리고 별도 터미널에서 01/02 조회 baseline 동시 실행 후 p95 비교.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, makeHandleSummary, login } from './lib/config.js';

export const options = {
  thresholds: {
    // AI Job 은 rate limit(429) 이 "정상 방어"일 수 있으므로 실패율 임계값을 완화
    http_req_duration: ['p(95)<3000'],
  },
  scenarios: {
    ai_jobs: {
      executor: 'constant-vus',
      vus: Number(__ENV.K6_VUS || 3),
      duration: __ENV.K6_DURATION || '30s',
    },
  },
};

const BOOK_ID = Number(__ENV.BOOK_ID || 1);

export function setup() {
  const email = __ENV.TEST_EMAIL || 'perf-test@booktown.local';
  const password = __ENV.TEST_PASSWORD || 'perf-test-1234';
  return { token: login(email, password) };
}

export default function (data) {
  const res = http.post(`${BASE_URL}/books/${BOOK_ID}/summaries`, null, {
    headers: { Authorization: `Bearer ${data.token}`, 'Content-Type': 'application/json' },
    tags: { endpoint: 'summary_create' },
  });
  check(res, {
    // 202 생성 수락 또는 429 rate limit(방어 동작) 모두 "예상된" 응답으로 간주
    'accepted or rate-limited': (r) => r.status === 202 || r.status === 200 || r.status === 429,
  });
  sleep(1);
}

export const handleSummary = makeHandleSummary(
  __ENV.RESULT_FILE || '/results/05-summary-job-vu3.json'
);
