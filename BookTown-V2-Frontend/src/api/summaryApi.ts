import client from './client';

// ─── Types ──────────────────────────────────────────────────────────────────

export type SummaryJobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface SummaryJob {
  jobId: number;
  bookId: number;
  status: SummaryJobStatus;
  summaryId: string | null;
  errorMessage: string | null;
  retryable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface FeedbackInfo {
  rating: number;
  comment: string | null;
}

export interface Summary {
  summaryId: string;
  bookId: number;
  content: string;
  isRegeneration: boolean;
  feedback: FeedbackInfo | null;
  createdAt: string;
}

interface ApiResponse<T> {
  data: T;
  meta: null;
}

// ─── Mock Data ────────────────────────────────────────────────────────────────

const MOCK_SUMMARY_CONTENT = `## 오만과 편견 — AI 요약

### 핵심 줄거리

영국 시골 마을 롱번에 사는 베넷 가문에는 다섯 딸이 있다. 어머니 베넷 부인은 딸들을 좋은 집안에 시집보내는 것을 인생 목표로 삼는다. 부유한 청년 빙리 씨가 이웃 네더필드에 이사 오고, 그의 친구 다아시도 함께 나타난다.

둘째 딸 엘리자베스는 다아시의 오만한 태도에 반감을 품지만, 다아시는 점차 그녀에게 매력을 느낀다. 한편 위컴이라는 매력적인 장교가 나타나 다아시에 대한 거짓 이야기로 엘리자베스의 편견을 강화시킨다.

다아시의 첫 번째 청혼은 엘리자베스에게 거절당하고, 이후 그가 보낸 편지를 통해 엘리자베스는 자신의 편견을 돌아보게 된다. 펨벌리 방문과 리디아의 위컴과의 도피 사건을 거치면서 두 사람은 서로를 이해하고 사랑으로 맺어진다.

### 주요 인물

- **엘리자베스 베넷**: 총명하고 독립적인 주인공. 편견을 극복하고 성장한다.
- **피츠윌리엄 다아시**: 오만하지만 진실한 내면을 가진 부유한 신사.
- **제인 베넷**: 착하고 낙관적인 첫째 딸. 빙리와 사랑에 빠진다.
- **찰스 빙리**: 다아시의 친구로 온화하고 친절한 성격.
- **위컴**: 겉으로는 매력적이지만 실제로는 간교한 인물.

### 주제

이 소설의 핵심 주제는 **오만(Pride)과 편견(Prejudice)**이다. 다아시의 계급적 오만함과 엘리자베스의 첫인상에 기반한 편견이 두 사람의 사랑을 가로막지만, 성찰과 이해를 통해 극복된다. 오스틴은 18세기 영국 사회의 계급 제도와 결혼에 대한 사회적 압력을 풍자적으로 묘사한다.`;

let mockJobIdCounter = 200;
const MOCK_JOBS = new Map<number, SummaryJob>();
const MOCK_SUMMARIES = new Map<number, Summary[]>();

// ─── API Functions ────────────────────────────────────────────────────────────

export const createSummary = async (
  isMockMode: boolean,
  bookId: string | number,
  scope?: string,
): Promise<SummaryJob> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 500));

    // 409 시뮬레이션: 이미 QUEUED/PROCESSING 중인 Job이 있으면 에러
    const existing = Array.from(MOCK_JOBS.values()).find(
      (j) =>
        j.bookId === Number(bookId) &&
        (j.status === 'QUEUED' || j.status === 'PROCESSING'),
    );
    if (existing) {
      const err = new Error('이미 진행 중인 요약이 있습니다.');
      (err as Error & { response: { status: number } }).response = { status: 409 };
      throw err;
    }

    const jobId = mockJobIdCounter++;
    const job: SummaryJob = {
      jobId,
      bookId: Number(bookId),
      status: 'QUEUED',
      summaryId: null,
      errorMessage: null,
      retryable: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    MOCK_JOBS.set(jobId, job);

    // 2초 후 PROCESSING, 7초 후 COMPLETED
    setTimeout(() => {
      const j = MOCK_JOBS.get(jobId);
      if (j) MOCK_JOBS.set(jobId, { ...j, status: 'PROCESSING', updatedAt: new Date().toISOString() });
    }, 2000);

    setTimeout(() => {
      const j = MOCK_JOBS.get(jobId);
      if (j) {
        const summaryId = `mock-summary-${jobId}`;
        MOCK_JOBS.set(jobId, {
          ...j,
          status: 'COMPLETED',
          summaryId,
          updatedAt: new Date().toISOString(),
        });
        const summary: Summary = {
          summaryId,
          bookId: Number(bookId),
          content: MOCK_SUMMARY_CONTENT + (scope ? `\n\n> 범위: ${scope}` : ''),
          isRegeneration: false,
          feedback: null,
          createdAt: new Date().toISOString(),
        };
        const list = MOCK_SUMMARIES.get(Number(bookId)) ?? [];
        MOCK_SUMMARIES.set(Number(bookId), [summary, ...list]);
      }
    }, 7000);

    return job;
  }

  const res = await client.post<ApiResponse<SummaryJob>>(
    `/books/${bookId}/summaries`,
    scope ? { scope } : {},
  );
  return res.data.data;
};

export const getSummaryJob = async (
  isMockMode: boolean,
  jobId: number,
): Promise<SummaryJob> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 150));
    const job = MOCK_JOBS.get(jobId);
    if (!job) throw new Error('Job을 찾을 수 없습니다.');
    return job;
  }

  const res = await client.get<ApiResponse<SummaryJob>>(
    `/summary-jobs/${jobId}`,
  );
  return res.data.data;
};

export const getSummaries = async (
  isMockMode: boolean,
  bookId: string | number,
): Promise<Summary[]> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 300));
    return MOCK_SUMMARIES.get(Number(bookId)) ?? [];
  }

  const res = await client.get<ApiResponse<Summary[]>>(
    `/books/${bookId}/summaries`,
  );
  return res.data.data;
};

export const getSummary = async (
  isMockMode: boolean,
  summaryId: string,
): Promise<Summary> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 200));
    for (const list of MOCK_SUMMARIES.values()) {
      const found = list.find((s) => s.summaryId === summaryId);
      if (found) return found;
    }
    throw new Error('요약을 찾을 수 없습니다.');
  }

  const res = await client.get<ApiResponse<Summary>>(
    `/summaries/${summaryId}`,
  );
  return res.data.data;
};

export const regenerateSummary = async (
  isMockMode: boolean,
  summaryId: string,
  bookId: number,
): Promise<SummaryJob> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 500));
    const jobId = mockJobIdCounter++;
    const job: SummaryJob = {
      jobId,
      bookId,
      status: 'QUEUED',
      summaryId: null,
      errorMessage: null,
      retryable: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    MOCK_JOBS.set(jobId, job);

    setTimeout(() => {
      const j = MOCK_JOBS.get(jobId);
      if (j) {
        const newSummaryId = `mock-regen-${jobId}`;
        MOCK_JOBS.set(jobId, {
          ...j,
          status: 'COMPLETED',
          summaryId: newSummaryId,
          updatedAt: new Date().toISOString(),
        });
        const regenSummary: Summary = {
          summaryId: newSummaryId,
          bookId,
          content: MOCK_SUMMARY_CONTENT + '\n\n> ♻️ 재생성된 요약입니다.',
          isRegeneration: true,
          feedback: null,
          createdAt: new Date().toISOString(),
        };
        const list = MOCK_SUMMARIES.get(bookId) ?? [];
        MOCK_SUMMARIES.set(bookId, [regenSummary, ...list]);
      }
    }, 6000);

    return job;
  }

  const res = await client.post<ApiResponse<SummaryJob>>(
    `/summaries/${summaryId}/regenerations`,
  );
  return res.data.data;
};

export const saveFeedback = async (
  isMockMode: boolean,
  summaryId: string,
  rating: number,
  comment?: string,
): Promise<void> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 300));
    for (const [bookId, list] of MOCK_SUMMARIES.entries()) {
      const idx = list.findIndex((s) => s.summaryId === summaryId);
      if (idx !== -1) {
        const updated = { ...list[idx], feedback: { rating, comment: comment ?? null } };
        const newList = [...list];
        newList[idx] = updated;
        MOCK_SUMMARIES.set(bookId, newList);
        return;
      }
    }
    return;
  }

  await client.put(`/summaries/${summaryId}/feedback`, { rating, comment });
};
