// ============================================================================
// 07-ai-vs-read-contention.js
//
// 목적: "AI Job 트래픽이 늘어날 때 일반 조회 API 의 latency 가 같이 올라가는가"
//       를 한 번의 실행 안에서 관측한다.
//
// 왜 한 스크립트에 두 시나리오를 넣었나
//   조회 baseline 과 AI Job 을 별도 터미널에서 돌리면 두 실행의 시작 시각과
//   측정 구간이 어긋나 "AI 부하 구간의 조회 p95" 를 정확히 잘라낼 수 없다.
//   k6 의 다중 시나리오 + startTime 을 쓰면 동일 실행 안에서
//   [AI 없음 구간] → [AI 있음 구간] 을 태그로 분리해 비교할 수 있다.
//
// 타임라인 (총 3분)
//   0:00 ~ 3:00  read  : 조회 API 를 일정 부하로 계속 호출 (기준선)
//   1:00 ~ 2:00  ai    : 요약 생성 Job 을 동시에 발생  ← 이 구간만 경합 발생
//
//   따라서 아래 3구간의 조회 p95 를 비교하면 된다.
//     A. 0~1분  : AI 없음 (before)
//     B. 1~2분  : AI 있음 (contention)
//     C. 2~3분  : AI 종료 후 회복
//
// ⚠️ 비용 주의
//   이 스크립트는 실제 OpenAI 호출을 유발한다. AI_VUS 를 함부로 올리지 말 것.
//   기본값 5 는 커넥션 풀(기본 10)과의 관계를 보기 위한 최소값이다.
//
// 실행 예시는 perf/RUNBOOK-transaction-boundary.md 참조.
// ============================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, login } from './lib/config.js';

const AI_VUS = Number(__ENV.AI_VUS || 5);
const READ_VUS = Number(__ENV.READ_VUS || 20);
const MAX_BOOK_ID = Number(__ENV.MAX_BOOK_ID || 5000);
const MAX_PAGE = Number(__ENV.MAX_PAGE || 250);
const BOOK_ID = Number(__ENV.BOOK_ID || 1);

// 구간별 조회 latency 를 따로 모으기 위한 커스텀 메트릭
const readBefore = new Trend('read_latency_before_ai', true);
const readDuring = new Trend('read_latency_during_ai', true);
const readAfter = new Trend('read_latency_after_ai', true);

export const options = {
  scenarios: {
    read: {
      executor: 'constant-vus',
      vus: READ_VUS,
      duration: '3m',
      exec: 'readTraffic',
      startTime: '0s',
    },
    ai: {
      executor: 'constant-vus',
      vus: AI_VUS,
      duration: '1m',
      exec: 'aiTraffic',
      startTime: '1m',
    },
  },
  thresholds: {
    // 임계값을 걸어 실패시키기보다, 구간별 수치를 뽑아 비교하는 것이 목적이다.
    'read_latency_before_ai': ['p(95)>=0'],
    'read_latency_during_ai': ['p(95)>=0'],
    'read_latency_after_ai': ['p(95)>=0'],
  },
};

export function setup() {
  const email = __ENV.TEST_EMAIL || 'perf-test@booktown.local';
  const password = __ENV.TEST_PASSWORD || 'perf-test-1234';
  return { token: login(email, password), startedAt: Date.now() };
}

// ---------------------------------------------------------------------------
// 일반 사용자 트래픽 — 목록 / 상세 / 검색을 섞어 호출
// ---------------------------------------------------------------------------
export function readTraffic(data) {
  const elapsedSec = (Date.now() - data.startedAt) / 1000;
  const pick = Math.random();

  let res;
  if (pick < 0.5) {
    const page = Math.floor(Math.random() * MAX_PAGE);
    res = http.get(`${BASE_URL}/books?page=${page}&size=20`, { tags: { endpoint: 'books_list' } });
  } else if (pick < 0.8) {
    const id = 1 + Math.floor(Math.random() * MAX_BOOK_ID);
    res = http.get(`${BASE_URL}/books/${id}`, { tags: { endpoint: 'book_detail' } });
  } else {
    res = http.get(`${BASE_URL}/books/search?q=the&page=0&size=20`, { tags: { endpoint: 'books_search' } });
  }

  check(res, { 'read ok': (r) => r.status === 200 });

  // AI 시나리오는 60~120초 구간에서만 동작한다. 그 경계로 구간을 나눈다.
  if (elapsedSec < 60) {
    readBefore.add(res.timings.duration);
  } else if (elapsedSec < 120) {
    readDuring.add(res.timings.duration);
  } else {
    readAfter.add(res.timings.duration);
  }

  sleep(0.5);
}

// ---------------------------------------------------------------------------
// AI Job 트래픽 — 요약 생성 요청
//
// VU 마다 서로 다른 책(BOOK_ID + (__VU-1)*3, has_content=true 인 3의 배수)을 요청한다.
// 모든 VU 가 같은 책을 요청하면 SummaryService 의 (userId, bookId) 단위 중복 Job 방어
// (existsByUserIdAndBookIdAndStatusIn) 때문에 첫 VU 가 만든 Job 이 끝날 때까지
// 나머지 VU 는 전부 409 를 받아 실제 동시 AI Job 수가 1개로 고정되어 버린다.
// 그러면 AI_VUS 를 아무리 올려도 커넥션 풀 경합이 재현되지 않으므로, 책을 분산한다.
// ---------------------------------------------------------------------------
export function aiTraffic(data) {
  const bookId = BOOK_ID + (__VU - 1) * 3;
  const res = http.post(`${BASE_URL}/books/${bookId}/summaries`, null, {
    headers: { Authorization: `Bearer ${data.token}`, 'Content-Type': 'application/json' },
    tags: { endpoint: 'summary_create' },
  });
  // 429(rate limit) / 409(중복 Job) 은 정상 방어 동작이다.
  check(res, {
    'ai accepted or defended': (r) => [200, 202, 409, 429].includes(r.status),
  });
  sleep(1);
}

// ---------------------------------------------------------------------------
// 결과 요약 — 구간별 조회 latency 를 표로 출력하고 JSON 으로 저장
// ---------------------------------------------------------------------------
export function handleSummary(data) {
  const g = (name) => {
    const m = data.metrics[name];
    return m ? m.values : {};
  };
  const before = g('read_latency_before_ai');
  const during = g('read_latency_during_ai');
  const after = g('read_latency_after_ai');

  const ms = (v) => (v == null ? 'n/a' : v.toFixed(1) + 'ms');
  const ratio = (a, b) => (a && b ? (b / a).toFixed(2) + 'x' : 'n/a');

  const text = `
──────── AI Job vs 조회 API 경합 측정 ────────
조회 VU: ${READ_VUS}   AI VU: ${AI_VUS}
BASE_URL: ${BASE_URL}

구간                     avg        p95        p99        건수
0~1분  AI 없음        ${ms(before.avg)}   ${ms(before['p(95)'])}   ${ms(before['p(99)'])}   ${before.count ?? 0}
1~2분  AI 동시 실행   ${ms(during.avg)}   ${ms(during['p(95)'])}   ${ms(during['p(99)'])}   ${during.count ?? 0}
2~3분  AI 종료 후     ${ms(after.avg)}   ${ms(after['p(95)'])}   ${ms(after['p(99)'])}   ${after.count ?? 0}

AI 부하로 인한 p95 증가 배수: ${ratio(before['p(95)'], during['p(95)'])}
──────────────────────────────────────────────
※ 이 수치와 함께 Grafana 의 hikaricp_connections_pending / active 그래프를
   같은 시간대로 캡처해야 "커넥션 풀 고갈이 원인"이라는 주장이 성립한다.
`;

  const out = {
    base_url: BASE_URL,
    read_vus: READ_VUS,
    ai_vus: AI_VUS,
    read_latency: {
      before_ai: { avg: before.avg, p95: before['p(95)'], p99: before['p(99)'], count: before.count },
      during_ai: { avg: during.avg, p95: during['p(95)'], p99: during['p(99)'], count: during.count },
      after_ai: { avg: after.avg, p95: after['p(95)'], p99: after['p(99)'], count: after.count },
    },
    http_req_duration_overall: data.metrics.http_req_duration
      ? data.metrics.http_req_duration.values
      : null,
    http_req_failed: data.metrics.http_req_failed ? data.metrics.http_req_failed.values : null,
    generated_at: new Date().toISOString(),
  };

  const result = {};
  result[__ENV.RESULT_FILE || '/results/07-ai-contention.json'] = JSON.stringify(out, null, 2);
  result['stdout'] = text;
  return result;
}
