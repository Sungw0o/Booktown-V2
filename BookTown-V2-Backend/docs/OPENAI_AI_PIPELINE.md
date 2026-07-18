# OpenAI AI 파이프라인 운영 메모

## 모델과 환경변수

| 용도 | 기본 모델 | 환경변수 |
|---|---|---|
| 요약·퀴즈·메타데이터 번역 | `gpt-4o-mini` | `OPENAI_CHAT_MODEL` |
| RAG 임베딩 | `text-embedding-3-small` | `OPENAI_EMBEDDING_MODEL` |
| 표지·장면 이미지 | `gpt-image-1-mini` | `OPENAI_IMAGE_MODEL` |

API Key는 서버 환경변수 `OPENAI_API_KEY`로만 주입한다. 저장소의 `.env`, YAML, 로그, 이슈, PR 본문에는 실제 값을 남기지 않는다.

이미지 비용을 제한하기 위한 기본값은 `OPENAI_IMAGE_QUALITY=low`, `OPENAI_IMAGE_SIZE=1024x1536`이다. 생성된 표지와 장면 이미지는 기존과 같이 MongoDB에 저장하므로 조회 시 다시 생성 비용이 발생하지 않는다.

## 배포 전 필수 작업

1. 배포 환경에 `OPENAI_API_KEY`를 Secret으로 등록한다.
2. 기존 Chroma 컬렉션을 백업한 뒤 `text-embedding-3-small`로 전체 도서를 재색인한다.
3. 표지 이미지 한 장을 생성해 200 응답, MongoDB 저장, `/api/v1/books/{bookId}/cover-image` 조회를 확인한다.
4. 요약 한 건을 생성해 OpenAI 채팅과 Chroma 검색이 함께 동작하는지 확인한다.

임베딩 모델이 바뀌면 기존 벡터와 새 벡터를 같은 컬렉션에서 혼용하면 안 된다. 운영 Chroma 삭제·재색인은 데이터 변경 작업이므로 별도 승인과 백업 후 수행한다.

## 비용·재시도 기준

- Spring AI 재시도는 기본 3회로 제한한다.
- 이미지 생성은 사용자 요청마다 즉시 반복하지 않고, 관리자 사전 생성 후 저장된 결과를 제공한다.
- 도서 10권의 표지 일괄 생성은 실제 키 등록 후 별도 작업으로 수행한다.
- 실제 호출 테스트는 단위 테스트와 분리하고, 최소 한 건만 스모크 테스트한다.

## 10개 챕터와 공통 웹툰 화풍

- 원문 길이나 원래 장 수와 관계없이 서비스용 독서 챕터는 문단·문장 경계를 우선해 정확히 10개로 균등 분할한다.
- 각 독서 챕터에는 장면 1개를 연결하므로 책당 장면 수도 10개다.
- 표지와 장면 프롬프트는 같은 웹툰 스타일 바이블(선화, 셀 채색, 팔레트, 조명, 시대 의상, 캐릭터 특징 유지)을 공유한다.
- 현재는 텍스트 프롬프트 기반 일관성이다. 참조 이미지 편집은 더 강한 캐릭터 고정이 가능하지만 API 호출과 저장 구조 변경이 필요해 후속 범위로 둔다.

## 이미지 비동기 병렬 처리

- 장면 이미지 Job은 전용 bounded executor에서 최대 2개 worker만 병렬 실행한다.
- 대기 큐는 20개이며, 포화 시 큐에 자리가 생길 때까지 생산 측을 대기시켜 backpressure를 적용하고 작업을 조용히 유실하지 않는다.
- 각 Job은 독립 상태를 저장하므로 한 장면 실패가 이미 접수된 다른 장면 Job을 취소하지 않는다.
- 운영에서 동시성 확대 전에는 이미지당 비용, 429 비율, 평균/최대 지연 시간을 먼저 측정한다.

## 공식 문서

- OpenAI 이미지 생성: https://developers.openai.com/api/docs/guides/image-generation
- OpenAI API Key 보안: https://developers.openai.com/api/docs/guides/production-best-practices#api-keys
- Spring AI OpenAI Chat: https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html
- Spring AI OpenAI Embeddings: https://docs.spring.io/spring-ai/reference/api/embeddings/openai-embeddings.html
- Spring AI OpenAI Image: https://docs.spring.io/spring-ai/reference/api/image/openai-image.html
