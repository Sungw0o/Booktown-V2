// [우선순위 1] 핵심 조회 API — 도서 상세
// GET /books/{bookId}   (public, 인증 불필요)
// 노션 축 03: 상세는 현재 캐시 미적용(@Cacheable 없음) → Redis 캐시 전/후 비교의 핵심 대상.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, vusOptions, makeHandleSummary } from './lib/config.js';

export const options = vusOptions();

// 존재하는 bookId 범위. seed 데이터 규모에 맞춰 조정.
const MAX_BOOK_ID = Number(__ENV.MAX_BOOK_ID || 100);

export default function () {
  const bookId = 1 + Math.floor(Math.random() * MAX_BOOK_ID);
  const res = http.get(`${BASE_URL}/books/${bookId}`, {
    tags: { endpoint: 'book_detail' },
  });
  check(res, {
    'status 200': (r) => r.status === 200,
    'body has data': (r) => r.body && r.body.indexOf('"data"') >= 0,
  });
  sleep(0.5);
}

export const handleSummary = makeHandleSummary(
  __ENV.RESULT_FILE || '/results/02-book-detail-vu10.json'
);
