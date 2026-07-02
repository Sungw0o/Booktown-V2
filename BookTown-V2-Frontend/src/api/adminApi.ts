import axios, { type AxiosProgressEvent } from 'axios';
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

export interface GutendexBookSearchItem {
  gutenbergId: number;
  title: string;
  author: string;
  description: string | null;
  coverImageUrl: string | null;
  textPlainUrl: string | null;
  suggestedGenre: string;
  suggestedCountry: string;
  subjects: string[];
  bookshelves: string[];
  downloadCount: number | null;
}

export interface GutendexBookSearchResponse {
  count: number;
  nextPage: number | null;
  previousPage: number | null;
  books: GutendexBookSearchItem[];
}

export interface GutendexImportRequest {
  genre?: string;
  country?: string;
}

export interface GutendexImportResponse {
  book: RegisterBookResponse;
  contentJob: ContentJob;
}

export interface GeneratedCoverResponse {
  bookId: number;
  coverImageUrl: string;
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

interface ApiFieldError {
  field?: string;
  message?: string;
}

interface ApiErrorBody {
  error?: {
    message?: string;
    fieldErrors?: ApiFieldError[];
  };
  message?: string;
}

export class AdminApiError extends Error {
  status?: number;
  fieldErrors: ApiFieldError[];

  constructor(message: string, status?: number, fieldErrors: ApiFieldError[] = []) {
    super(message);
    this.name = 'AdminApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

const toAdminApiError = (error: unknown, fallback: string): AdminApiError => {
  if (!axios.isAxiosError<ApiErrorBody>(error)) {
    return error instanceof Error ? new AdminApiError(error.message) : new AdminApiError(fallback);
  }

  const status = error.response?.status;
  const body = error.response?.data;
  const fieldErrors = body?.error?.fieldErrors ?? [];

  if (status === 401) return new AdminApiError('로그인이 만료되었습니다. 다시 로그인해 주세요.', status, fieldErrors);
  if (status === 403) return new AdminApiError('관리자 권한이 필요한 작업입니다.', status, fieldErrors);
  if (status === 413) return new AdminApiError('파일 크기가 너무 큽니다. 더 작은 TXT 파일을 업로드해 주세요.', status, fieldErrors);

  return new AdminApiError(body?.error?.message ?? body?.message ?? fallback, status, fieldErrors);
};

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

const MOCK_GUTENDEX_BOOKS: GutendexBookSearchItem[] = [
  {
    gutenbergId: 1342,
    title: 'Pride and Prejudice',
    author: 'Austen, Jane',
    description: 'A classic novel of manners following Elizabeth Bennet and Mr. Darcy.',
    coverImageUrl: null,
    textPlainUrl: 'https://www.gutenberg.org/cache/epub/1342/pg1342.txt',
    suggestedGenre: 'NOVEL',
    suggestedCountry: 'WESTERN',
    subjects: ['Courtship -- Fiction', 'England -- Fiction'],
    bookshelves: ['Best Books Ever Listings'],
    downloadCount: 70000,
  },
  {
    gutenbergId: 84,
    title: 'Frankenstein; Or, The Modern Prometheus',
    author: 'Shelley, Mary Wollstonecraft',
    description: 'A Gothic novel about creation, responsibility, and alienation.',
    coverImageUrl: null,
    textPlainUrl: 'https://www.gutenberg.org/cache/epub/84/pg84.txt',
    suggestedGenre: 'NOVEL',
    suggestedCountry: 'WESTERN',
    subjects: ['Science fiction', 'Gothic fiction'],
    bookshelves: ['Gothic Fiction', 'Science Fiction'],
    downloadCount: 50000,
  },
];

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
  try {
    const res = await client.post<{ data: RegisterBookResponse }>('/admin/books', req);
    return res.data.data;
  } catch (error) {
    throw toAdminApiError(error, '도서 등록에 실패했습니다.');
  }
};

export const searchGutendexBooks = async (
  isMockMode: boolean,
  keyword: string,
  page = 1,
): Promise<GutendexBookSearchResponse> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 400));
    const q = keyword.trim().toLowerCase();
    const books = MOCK_GUTENDEX_BOOKS.filter((book) =>
      book.title.toLowerCase().includes(q) || book.author.toLowerCase().includes(q),
    );
    return { count: books.length, nextPage: null, previousPage: null, books };
  }
  try {
    const res = await client.get<{ data: GutendexBookSearchResponse }>('/admin/gutendex/books', {
      params: { keyword, page },
    });
    return res.data.data;
  } catch (error) {
    throw toAdminApiError(error, 'Gutendex 검색에 실패했습니다.');
  }
};

export const importGutendexBook = async (
  isMockMode: boolean,
  gutenbergId: number,
  req: GutendexImportRequest = {},
): Promise<GutendexImportResponse> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 700));
    _mockJobStatus = 'QUEUED';
    _mockJobTick = 0;
    const bookId = Math.floor(Math.random() * 9000) + 1000;
    return {
      book: { bookId },
      contentJob: mockContentJob(Math.floor(Math.random() * 9000) + 1000, bookId),
    };
  }
  try {
    const res = await client.post<{ data: GutendexImportResponse }>(
      `/admin/gutendex/books/${gutenbergId}/import`,
      req,
    );
    return res.data.data;
  } catch (error) {
    throw toAdminApiError(error, 'Gutendex 도서 가져오기에 실패했습니다.');
  }
};

/**
 * POST /admin/books/{bookId}/contents
 * TXT 원문 파일 업로드 (multipart/form-data) → ContentJob 반환
 */
export const uploadContent = async (
  isMockMode: boolean,
  bookId: number,
  file: File,
  onProgress?: (progress: number) => void,
): Promise<ContentJob> => {
  if (isMockMode) {
    onProgress?.(25);
    await new Promise((r) => setTimeout(r, 800));
    onProgress?.(100);
    _mockJobStatus = 'QUEUED';
    _mockJobTick = 0;
    return mockContentJob(Math.floor(Math.random() * 9000) + 1000, bookId);
  }
  const formData = new FormData();
  formData.append('file', file);
  try {
    const res = await client.post<{ data: ContentJob }>(
      `/admin/books/${bookId}/contents`,
      formData,
      {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (event: AxiosProgressEvent) => {
          if (!event.total) return;
          onProgress?.(Math.min(100, Math.round((event.loaded / event.total) * 100)));
        },
      },
    );
    return res.data.data;
  } catch (error) {
    throw toAdminApiError(error, '업로드에 실패했습니다.');
  }
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

export const generateBookCover = async (
  isMockMode: boolean,
  bookId: number,
): Promise<GeneratedCoverResponse> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 700));
    return { bookId, coverImageUrl: `/api/v1/books/${bookId}/cover-image` };
  }
  try {
    const res = await client.post<{ data: GeneratedCoverResponse }>(`/admin/books/${bookId}/cover`);
    return res.data.data;
  } catch (error) {
    throw toAdminApiError(error, 'AI 표지 생성에 실패했습니다.');
  }
};
