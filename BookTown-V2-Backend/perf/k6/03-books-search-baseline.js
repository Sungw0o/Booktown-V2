// [우선순위 2] 검색 API
// GET /books/search?q=&page=&size=   (public, 인증 불필요)
// 노션 축 02: BookRepository 가 `title LIKE %:q% OR author LIKE %:q%` 풀스캔.
// FULLTEXT 인덱스 도입 전/후 p95 비교의 핵심 대상.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, vusOptions, makeHandleSummary } from './lib/config.js';

export const options = vusOptions();

// 다양한 검색어로 캐시/최적화 효과를 왜곡하지 않도록 랜덤 키워드 사용
const KEYWORDS = (__ENV.KEYWORDS ||
  'the,love,war,king,house,night,city,life,death,time,man,world,sea,book,star')
  .split(',');

export default function () {
  const q = KEYWORDS[Math.floor(Math.random() * KEYWORDS.length)];
  const res = http.get(`${BASE_URL}/books/search?q=${encodeURIComponent(q)}&page=0&size=20`, {
    tags: { endpoint: 'books_search' },
  });
  check(res, {
    'status 200': (r) => r.status === 200,
    'body has data': (r) => r.body && r.body.indexOf('"data"') >= 0,
  });
  sleep(0.5);
}

export const handleSummary = makeHandleSummary(
  __ENV.RESULT_FILE || '/results/03-books-search-vu10.json'
);
