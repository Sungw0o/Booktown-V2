// [우선순위 6 / 노션 축 06] 장애·지연 시나리오 — Readiness 장애 전파(blast radius)
// GET /health/readiness   (public)
// 사용법: 이 스크립트를 돌리는 "동안" 다른 터미널에서 Chroma 컨테이너를 강제 종료한다.
//   docker compose stop chroma   (또는 docker kill <chroma_container>)
// 관찰 목적: 부가 의존성(Chroma) 장애가 readiness 503 으로 나타나는지, 그리고 그 사이
//   핵심 조회 API(01/02)는 계속 200 을 반환하는지(= 장애가 격리되는지) 확인.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, makeHandleSummary } from './lib/config.js';

export const options = {
  thresholds: {
    // 장애 주입 구간에서 503 이 예상되므로 게이트를 걸지 않고 관찰만 한다
    http_req_duration: ['p(95)<1000'],
  },
  scenarios: {
    probe: {
      executor: 'constant-vus',
      vus: Number(__ENV.K6_VUS || 5),
      duration: __ENV.K6_DURATION || '2m',
    },
  },
};

export default function () {
  const ready = http.get(`${BASE_URL}/health/readiness`, { tags: { endpoint: 'readiness' } });
  check(ready, { 'readiness 200': (r) => r.status === 200 });

  // 핵심 조회가 장애와 무관하게 살아있는지 동시 확인
  const detail = http.get(`${BASE_URL}/books/1`, { tags: { endpoint: 'book_detail_during_fault' } });
  check(detail, { 'book detail still 200': (r) => r.status === 200 });

  sleep(1);
}

export const handleSummary = makeHandleSummary(
  __ENV.RESULT_FILE || '/results/06-readiness-resilience.json'
);
