// 공통 설정/헬퍼 — BookTown k6 부하 테스트
// 모든 스크립트가 import 해서 사용한다. (docker에서 /scripts/lib/config.js 로 마운트됨)

// BASE_URL 예: http://host.docker.internal:8080/api/v1
export const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080/api/v1';

// 공통 임계값 — 주의사항 기준: 실패율 1% 미만, p95 1000ms 미만
export const defaultThresholds = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<1000'],
};

// constant-vus 시나리오 옵션 빌더
export function vusOptions(thresholds = defaultThresholds) {
  return {
    thresholds,
    scenarios: {
      load: {
        executor: 'constant-vus',
        vus: Number(__ENV.K6_VUS || 10),
        duration: __ENV.K6_DURATION || '1m',
      },
    },
  };
}

// 단계적 부하(ramping) 옵션 — 한도 탐색용
export function rampingOptions(thresholds = defaultThresholds) {
  return {
    thresholds,
    scenarios: {
      ramp: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
          { duration: '30s', target: Number(__ENV.K6_VUS || 20) },
          { duration: '1m', target: Number(__ENV.K6_VUS || 20) },
          { duration: '15s', target: 0 },
        ],
      },
    },
  };
}

// JWT 로그인 → accessToken 반환 (쓰기 테스트 setup 단계에서 사용)
import http from 'k6/http';
export function login(email, password) {
  const res = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({
      email,
      password,
      turnstileToken: __ENV.TURNSTILE_TOKEN || null,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status !== 200) {
    throw new Error(`login 실패 status=${res.status} body=${res.body}`);
  }
  return JSON.parse(res.body).data.accessToken;
}

// handleSummary — /results 에 요약 JSON 저장 + stdout 요약 출력
// RESULT_FILE 로 저장 경로 지정 (예: /results/01-books-list-vu10.json)
export function makeHandleSummary(resultFile) {
  return function (data) {
    const dur = data.metrics.http_req_duration ? data.metrics.http_req_duration.values : {};
    const failed = data.metrics.http_req_failed ? data.metrics.http_req_failed.values : {};
    const reqs = data.metrics.http_reqs ? data.metrics.http_reqs.values : {};
    const checks = data.metrics.checks ? data.metrics.checks.values : {};

    const out = {
      target: __ENV.TARGET_LABEL || resultFile,
      base_url: BASE_URL,
      vus: Number(__ENV.K6_VUS || 10),
      duration: __ENV.K6_DURATION || '1m',
      total_requests: reqs.count,
      requests_per_sec: reqs.rate,
      fail_rate: failed.rate, // 0.0 ~ 1.0
      checks_pass_rate: checks.rate,
      latency_ms: {
        avg: dur.avg,
        min: dur.min,
        med: dur.med,
        p90: dur['p(90)'],
        p95: dur['p(95)'],
        p99: dur['p(99)'], // --summary-trend-stats 에 p(99) 포함 시에만 채워짐
        max: dur.max,
      },
      thresholds_passed: Object.values(data.metrics).every(
        (m) => !m.thresholds || Object.values(m.thresholds).every((t) => t.ok !== false)
      ),
      generated_at: new Date().toISOString(),
    };

    const result = {};
    result[resultFile] = JSON.stringify(out, null, 2);
    result['stdout'] = textSummary(out);
    return result;
  };
}

function textSummary(o) {
  const pct = (v) => (v == null ? 'n/a' : (v * 100).toFixed(2) + '%');
  const ms = (v) => (v == null ? 'n/a' : v.toFixed(1) + 'ms');
  return `
──────── BookTown k6 요약 ────────
대상          : ${o.target}
VU / duration : ${o.vus} / ${o.duration}
총 요청 수    : ${o.total_requests}  (${o.requests_per_sec ? o.requests_per_sec.toFixed(1) : 'n/a'} req/s)
실패율        : ${pct(o.fail_rate)}
checks 통과   : ${pct(o.checks_pass_rate)}
응답시간 avg  : ${ms(o.latency_ms.avg)}
        p90   : ${ms(o.latency_ms.p90)}
        p95   : ${ms(o.latency_ms.p95)}
        p99   : ${ms(o.latency_ms.p99)}
        max   : ${ms(o.latency_ms.max)}
threshold 통과: ${o.thresholds_passed ? 'PASS ✅' : 'FAIL ❌'}
──────────────────────────────────
`;
}
