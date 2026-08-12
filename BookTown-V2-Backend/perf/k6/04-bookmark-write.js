// [우선순위 3] 쓰기 API — 북마크 추가/삭제
// POST /books/{bookId}/bookmark, DELETE /books/{bookId}/bookmark   (JWT 필요)
// 노션 축 04: 북마크 카운트 lost update 동시성 대상.
// ⚠️ DB 쓰기 발생. setup 에서 로그인한 "테스트 전용" 계정으로만 실행할 것.
//   각 iteration 은 POST 후 DELETE 로 스스로 정리하지만, 실패 시 잔여 데이터가 남을 수 있음.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, vusOptions, makeHandleSummary, login } from './lib/config.js';

export const options = vusOptions({
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<1000'],
});

const MAX_BOOK_ID = Number(__ENV.MAX_BOOK_ID || 100);

// setup: 1회 로그인해서 accessToken 확보 후 모든 VU 에 공유
export function setup() {
  const email = __ENV.TEST_EMAIL || 'perf-test@booktown.local';
  const password = __ENV.TEST_PASSWORD || 'perf-test-1234';
  const token = login(email, password);
  return { token };
}

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`,
    'Content-Type': 'application/json',
  };
  const bookId = 1 + Math.floor(Math.random() * MAX_BOOK_ID);

  const add = http.post(`${BASE_URL}/books/${bookId}/bookmark`, null, {
    headers,
    tags: { endpoint: 'bookmark_add' },
  });
  check(add, { 'add 2xx': (r) => r.status >= 200 && r.status < 300 });

  const del = http.del(`${BASE_URL}/books/${bookId}/bookmark`, null, {
    headers,
    tags: { endpoint: 'bookmark_remove' },
  });
  check(del, { 'remove 2xx': (r) => r.status >= 200 && r.status < 300 });

  sleep(0.5);
}

export const handleSummary = makeHandleSummary(
  __ENV.RESULT_FILE || '/results/04-bookmark-write-vu10.json'
);
