import client from './client';

// ─── Types ────────────────────────────────────────────────────────────────────

export interface RegisterBookRequest {
  title: string;
  author: string;
  description?: string;
  coverImageUrl?: string;
  genre: string;
  country: string;
}

export interface RegisterBookResponse {
  bookId: number;
}

export type ContentJobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface ContentJob {
  jobId: number;
  bookId: number;
  status: ContentJobStatus;
  chapterCount: number | null;
  errorMessage: string | null;
  retryable: boolean;
  createdAt: string;
  updatedAt: string;
}

// ─── Mock Data ────────────────────────────────────────────────────────────────

let _mockJobStatus: ContentJobStatus = 'QUEUED';
let _mockJobTick = 0;

const mockContentJob = (jobId: number, bookId: number): ContentJob => {
  _mockJobTick++;
  if (_mockJobTick >= 2 && _mockJobStatus === 'QUEUED') _mockJobStatus = 'PROCESSING';
  if (_mockJobTick >= 4 && _mockJobStatus === 'PROCESSING') _mockJobStatus = 'COMPLETED';
  return {
    jobId,
    bookId,
    status: _mockJobStatus,
    chapterCount: _mockJobStatus === 'COMPLETED' ? 12 : null,
    errorMessage: null,
    retryable: false,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
};

// ─── API Functions ────────────────────────────────────────────────────────────

/**
 * POST /admin/books
 * 도서 메타데이터 등록 → bookId 반환
 */
export const registerBook = async (
  isMockMode: boolean,
  req: RegisterBookRequest,
): Promise<RegisterBookResponse> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 600));
    return { bookId: Math.floor(Math.random() * 9000) + 1000 };
  }
  const res = await client.post<{ data: RegisterBookResponse }>('/admin/books', req);
  return res.data.data;
};

/**
 * POST /admin/books/{bookId}/contents
 * TXT 원문 파일 업로드 (multipart/form-data) → ContentJob 반환
 */
export const uploadContent = async (
  isMockMode: boolean,
  bookId: number,
  file: File,
): Promise<ContentJob> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 800));
    _mockJobStatus = 'QUEUED';
    _mockJobTick = 0;
    return mockContentJob(Math.floor(Math.random() * 9000) + 1000, bookId);
  }
  const formData = new FormData();
  formData.append('file', file);
  const res = await client.post<{ data: ContentJob }>(
    `/admin/books/${bookId}/contents`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
  return res.data.data;
};

/**
 * GET /admin/content-jobs/{jobId}
 * 원문 파싱·임베딩 Job 상태 조회
 */
export const getContentJob = async (
  isMockMode: boolean,
  jobId: number,
): Promise<ContentJob> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 300));
    return mockContentJob(jobId, 0);
  }
  const res = await client.get<{ data: ContentJob }>(`/admin/content-jobs/${jobId}`);
  return res.data.data;
};
