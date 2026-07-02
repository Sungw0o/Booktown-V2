import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
  scenarios: {
    steady_readiness_probe: {
      executor: 'constant-vus',
      vus: Number(__ENV.K6_VUS || 5),
      duration: __ENV.K6_DURATION || '30s',
    },
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

export default function () {
  const response = http.get(`${BASE_URL}/health/readiness`, {
    tags: { endpoint: 'readiness' },
  });

  check(response, {
    'readiness returns 200': (res) => res.status === 200,
    'readiness response is fast': (res) => res.timings.duration < 500,
  });

  sleep(1);
}
