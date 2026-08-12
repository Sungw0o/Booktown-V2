// [우선순위 1] 핵심 조회 API — 도서 목록
// GET /books?page=&size=&sort=&genre=   (public, 인증 불필요)
// 노션 축 03(read-heavy) baseline. Redis 캐시 전/후 비교 대상.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, vusOptions, makeHandleSummary } from './lib/config.js';

export const options = vusOptions();

const SIZE = Number(__ENV.PAGE_SIZE || 20);
const SORT = __ENV.SORT || 'latest';

export default function () {
  // 목록은 페이지네이션 특성상 여러 페이지를 고르게 조회
  const page = Math.floor(Math.random() * Number(__ENV.MAX_PAGE || 10));
  const res = http.get(`${BASE_URL}/books?page=${page}&size=${SIZE}&sort=${SORT}`, {
    tags: { endpoint: 'books_list' },
  });
  check(res, {
    'status 200': (r) => r.status === 200,
    'body has data': (r) => r.body && r.body.indexOf('"data"') >= 0,
  });
  sleep(0.5);
}

export const handleSummary = makeHandleSummary(
  __ENV.RESULT_FILE || '/results/01-books-list-vu10.json'
);
