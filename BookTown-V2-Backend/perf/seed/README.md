# perf/seed — 부하 테스트용 시드 데이터

쓰기 API(북마크·요약) 테스트는 실제 DB에 데이터를 남긴다. **운영 DB가 아닌 로컬 테스트 DB**에서만 실행하고,
전용 테스트 계정으로만 부하를 건다.

## 1. 테스트 전용 계정 만들기 (쓰기 테스트 필수)

로그인 토큰이 필요한 04/05 스크립트를 돌리기 전에 계정을 하나 만든다.

```powershell
# BASE_URL 은 host 기준(PowerShell에서 직접 호출) → localhost 사용
curl.exe -X POST http://localhost:8080/api/v1/auth/signup `
  -H "Content-Type: application/json" `
  -d '{\"email\":\"perf-test@booktown.local\",\"nickname\":\"perftest\",\"password\":\"perf-test-1234\"}'
```

> ⚠️ 로그인/회원가입에 Cloudflare Turnstile 검증이 걸려 있으면 로컬에서 실패할 수 있다.
> 로컬 프로파일에서 Turnstile 을 비활성화했는지 `application.yml`/환경변수로 확인하고,
> 필요하면 k6 실행 시 `-e TURNSTILE_TOKEN=<로컬용 토큰>` 을 넘긴다.

계정 정보는 k6 실행 시 아래 env 로 전달한다(기본값과 동일하면 생략 가능):
`-e TEST_EMAIL=perf-test@booktown.local -e TEST_PASSWORD=perf-test-1234`

## 2. 도서 시드 데이터

조회/검색/상세 테스트는 **도서가 충분히 많아야** 의미가 있다(풀스캔·인덱스 효과가 데이터량에 비례).
BookTown 은 관리자 화면에서 Gutendex 도서를 수집하는 구조이므로, 시드는 두 가지 방법 중 하나:

- **권장**: 관리자 계정으로 Gutendex 수집을 여러 번 실행해 수백~수천 건을 적재.
- **빠른 대량 적재**: 아래 `seed_books.sql` 로 더미 도서를 직접 INSERT (스키마는 실제 `books` 테이블에 맞춰 조정 필요).

적재 후 실제 `bookId` 최댓값을 확인하고, k6 실행 시 `-e MAX_BOOK_ID=<최댓값>` / `-e MAX_PAGE=<총페이지>` 로 넘긴다.

```sql
-- 현재 도서 수 / 최대 id 확인
SELECT COUNT(*) AS total, MAX(id) AS max_id FROM books;
```
