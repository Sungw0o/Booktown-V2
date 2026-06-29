import client from './client';

// ─── Types ──────────────────────────────────────────────────────────────────

export type SummaryLevel = 'SIMPLE' | 'STANDARD' | 'DETAILED';
export type SummaryLength = 'SHORT' | 'MEDIUM' | 'LONG';
export type SummaryJobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface SummaryJob {
  jobId: number;
  status: SummaryJobStatus;
  summaryId: string | null;
  errorMessage: string | null;
  retryable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Summary {
  summaryId: string;
  bookId: number;
  chapterId: number;
  level: SummaryLevel;
  length: SummaryLength;
  content: string;
  keyPoints: string[];
  characters: string[];
  helpful: boolean | null;
  createdAt: string;
}

interface ApiResponse<T> {
  data: T;
  meta: PageMeta | null;
}

interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface PagedSummaries {
  summaries: Summary[];
  meta: PageMeta;
}

// ─── Level / Length metadata ─────────────────────────────────────────────────

export const LEVEL_META: Record<SummaryLevel, { label: string; desc: string }> = {
  SIMPLE: { label: '간단', desc: '핵심 줄거리만 한눈에' },
  STANDARD: { label: '표준', desc: '주요 사건과 인물 포함' },
  DETAILED: { label: '상세', desc: '등장인물·주제·상징 분석' },
};

export const LENGTH_META: Record<SummaryLength, { label: string; desc: string }> = {
  SHORT: { label: '짧게', desc: '200자 내외' },
  MEDIUM: { label: '보통', desc: '500자 내외' },
  LONG: { label: '길게', desc: '1,000자 내외' },
};

// ─── Mock Data ───────────────────────────────────────────────────────────────

let mockJobIdCounter = 200;
const MOCK_JOBS: Map<number, SummaryJob> = new Map();
const MOCK_SUMMARIES: Map<string, Summary> = new Map();

const MOCK_SUMMARY_CONTENTS: Record<number, string> = {
  1: '엘리자베스 베넷과 다아시 씨의 첫 만남은 무도회에서 이루어진다. 다아시는 오만한 태도로 엘리자베스를 무시하고, 이는 두 사람 사이에 깊은 편견을 심어준다. 빙리 씨와 제인의 관계가 싹트는 사이, 엘리자베스는 다아시에 대한 반감을 키워간다.',
  2: '위컴 씨가 등장하여 다아시에 대한 부정적인 이야기를 전한다. 엘리자베스는 그의 말을 믿고 다아시에 대한 편견을 더욱 굳힌다. 한편 빙리 씨 일행이 네더필드를 떠나며 제인의 희망이 꺾인다.',
  3: '콜린스 씨의 청혼을 거절한 엘리자베스는 루카스 집안의 샬럿이 그와 결혼하는 것을 목격한다. 다아시의 첫 번째 청혼을 엘리자베스가 단호히 거절하고, 다아시는 위컴에 관한 진실을 담은 편지를 남긴다.',
};

const MOCK_KEY_POINTS: string[] = [
  '첫인상과 편견이 관계에 미치는 영향',
  '사회적 계급과 결혼의 관계',
  '등장인물의 성격 대비와 성장',
];

const MOCK_CHARACTERS: string[] = ['엘리자베스 베넷', '다아시 씨', '제인 베넷', '빙리 씨', '위컴 씨'];

// ─── API Functions ────────────────────────────────────────────────────────────

export const createSummary = async (
  isMockMode: boolean,
  bookId: string | number,
  chapterId: number,
  level: SummaryLevel = 'STANDARD',
  length: SummaryLength = 'MEDIUM',
): Promise<SummaryJob> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 500));
    const jobId = mockJobIdCounter++;
    const job: SummaryJob = {
      jobId,
      status: 'QUEUED',
      summaryId: null,
      errorMessage: null,
      retryable: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    MOCK_JOBS.set(jobId, job);

    // 비동기 완료 시뮬레이션
    setTimeout(() => {
      const j = MOCK_JOBS.get(jobId);
      if (j) MOCK_JOBS.set(jobId, { ...j, status: 'PROCESSING', updatedAt: new Date().toISOString() });
      setTimeout(() => {
        const j2 = MOCK_JOBS.get(jobId);
        if (!j2) return;
        const summaryId = `mock-summary-${jobId}`;
        MOCK_JOBS.set(jobId, {
          ...j2,
          status: 'COMPLETED',
          summaryId,
          updatedAt: new Date().toISOString(),
        });
        const summary: Summary = {
          summaryId,
          bookId: Number(bookId),
          chapterId,
          level,
          length,
          content: MOCK_SUMMARY_CONTENTS[chapterId] ?? '이 챕터의 주요 사건이 전개됩니다.',
          keyPoints: MOCK_KEY_POINTS,
          characters: MOCK_CHARACTERS,
          helpful: null,
          createdAt: new Date().toISOString(),
        };
        MOCK_SUMMARIES.set(summaryId, summary);
      }, 4000);
    }, 2000);

    return job;
  }

  const res = await client.post<ApiResponse<SummaryJob>>(
    `/books/${bookId}/summaries`,
    { chapterId, level, length },
  );
  return res.data.data;
};

export const getSummaryJob = async (
  isMockMode: boolean,
  jobId: number,
): Promise<SummaryJob> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 200));
    const job = MOCK_JOBS.get(jobId);
    if (!job) throw new Error('Job을 찾을 수 없습니다.');
    return job;
  }

  const res = await client.get<ApiResponse<SummaryJob>>(`/summary-jobs/${jobId}`);
  return res.data.data;
};

export const getSummaryList = async (
  isMockMode: boolean,
  bookId: string | number,
  page = 0,
  size = 20,
): Promise<PagedSummaries> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 300));
    const all = Array.from(MOCK_SUMMARIES.values()).filter(
      (s) => s.bookId === Number(bookId),
    );
    const start = page * size;
    const sliced = all.slice(start, start + size);
    return {
      summaries: sliced,
      meta: {
        page,
        size,
        totalElements: all.length,
        totalPages: Math.ceil(all.length / size),
        hasNext: start + size < all.length,
      },
    };
  }

  const res = await client.get<ApiResponse<Summary[]>>(
    `/books/${bookId}/summaries`,
    { params: { page, size } },
  );
  const content = res.data.data;
  const meta = res.data.meta ?? {
    page,
    size,
    totalElements: content.length,
    totalPages: 1,
    hasNext: false,
  };
  return { summaries: content, meta };
};

export const getSummaryDetail = async (
  isMockMode: boolean,
  summaryId: string,
): Promise<Summary> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 200));
    const summary = MOCK_SUMMARIES.get(summaryId);
    if (!summary) throw new Error('요약을 찾을 수 없습니다.');
    return summary;
  }

  const res = await client.get<ApiResponse<Summary>>(`/summaries/${summaryId}`);
  return res.data.data;
};

export const regenerateSummary = async (
  isMockMode: boolean,
  summaryId: string,
): Promise<SummaryJob> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 500));
    const jobId = mockJobIdCounter++;
    const existing = MOCK_SUMMARIES.get(summaryId);
    const job: SummaryJob = {
      jobId,
      status: 'QUEUED',
      summaryId: null,
      errorMessage: null,
      retryable: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    MOCK_JOBS.set(jobId, job);

    setTimeout(() => {
      const newSummaryId = `mock-regen-${jobId}`;
      MOCK_JOBS.set(jobId, {
        ...job,
        status: 'COMPLETED',
        summaryId: newSummaryId,
        updatedAt: new Date().toISOString(),
      });
      if (existing) {
        MOCK_SUMMARIES.set(newSummaryId, {
          ...existing,
          summaryId: newSummaryId,
          content: existing.content + '\n\n[재생성된 요약] 새로운 관점으로 재해석되었습니다.',
          createdAt: new Date().toISOString(),
        });
      }
    }, 5000);

    return job;
  }

  const res = await client.post<ApiResponse<SummaryJob>>(
    `/summaries/${summaryId}/regenerations`,
  );
  return res.data.data;
};

export const sendFeedback = async (
  isMockMode: boolean,
  summaryId: string,
  helpful: boolean,
): Promise<void> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 200));
    const s = MOCK_SUMMARIES.get(summaryId);
    if (s) MOCK_SUMMARIES.set(summaryId, { ...s, helpful });
    return;
  }

  await client.put(`/summaries/${summaryId}/feedback`, { helpful });
};
