import client from './client';
import { MOCK_BOOKS } from './bookApi';
import type { Book, PageMeta } from './bookApi';

export interface UserProfile {
  nickname: string;
  email: string;
  role: 'USER' | 'ADMIN' | string;
}

export interface BookmarkPage {
  books: Book[];
  meta: PageMeta;
}

export interface QuizHistoryItem {
  quizId: string;
  bookId: string;
  bookTitle: string;
  score: number;
  total: number;
  accuracy: number;
  submittedAt: string;
}

interface ApiResponse<T> {
  data: T;
  meta: PageMeta | null;
}

interface BookmarkedBookDto {
  bookmarkId: number | string;
  bookId: number | string;
  title: string;
  author: string;
  genre: string;
  coverImageUrl?: string | null;
  bookmarkedAt?: string;
}

interface QuizHistoryDto {
  quizId?: number | string;
  id?: number | string;
  bookId: number | string;
  bookTitle?: string;
  title?: string;
  score: number;
  total?: number;
  totalCount?: number;
  correctCount?: number;
  totalQuestions?: number;
  accuracy?: number;
  submittedAt?: string;
  createdAt?: string;
}

interface SpringPage<T> {
  content: T[];
}

const DEFAULT_META: PageMeta = {
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 1,
  hasNext: false,
};

const getMockBookmarkedIds = (): string[] => {
  const raw = localStorage.getItem('bt_mock_bookmarks');
  return raw ? JSON.parse(raw) : [];
};

const getMockQuizHistory = (): QuizHistoryItem[] => {
  const raw = localStorage.getItem('bt_mock_quiz_history');
  return raw ? JSON.parse(raw) : [];
};

const toBook = (dto: BookmarkedBookDto): Book => ({
  id: String(dto.bookId),
  title: dto.title,
  author: dto.author,
  genre: dto.genre,
  description: '',
  coverImageUrl: dto.coverImageUrl,
  isBookmarked: true,
  hasSummary: false,
  hasIllust: false,
  hasQuiz: true,
  chapters: [],
});

const toQuizHistory = (dto: QuizHistoryDto): QuizHistoryItem => {
  const total = dto.total ?? dto.totalCount ?? dto.totalQuestions ?? 0;
  const correct = dto.correctCount ?? dto.score;
  return {
    quizId: String(dto.quizId ?? dto.id ?? `${dto.bookId}-${dto.submittedAt ?? dto.createdAt ?? ''}`),
    bookId: String(dto.bookId),
    bookTitle: dto.bookTitle ?? dto.title ?? `도서 #${dto.bookId}`,
    score: correct,
    total,
    accuracy: dto.accuracy ?? dto.score ?? (total > 0 ? Math.round((correct / total) * 100) : 0),
    submittedAt: dto.submittedAt ?? dto.createdAt ?? new Date().toISOString(),
  };
};

export const getMyProfile = async (isMockMode: boolean, fallback?: UserProfile | null): Promise<UserProfile> => {
  if (isMockMode) {
    await new Promise((resolve) => setTimeout(resolve, 250));
    return fallback ?? {
      nickname: '민지',
      email: 'minji@example.com',
      role: 'USER',
    };
  }

  const res = await client.get<ApiResponse<UserProfile>>('/users/me');
  return res.data.data;
};

export const getMyBookmarks = async (
  isMockMode: boolean,
  params: { page?: number; size?: number } = {}
): Promise<BookmarkPage> => {
  const page = params.page ?? 0;
  const size = params.size ?? 20;

  if (isMockMode) {
    await new Promise((resolve) => setTimeout(resolve, 300));
    const ids = getMockBookmarkedIds();
    const books = MOCK_BOOKS
      .filter((book) => ids.includes(book.id))
      .map((book) => ({ ...book, isBookmarked: true }));
    return {
      books,
      meta: {
        page,
        size,
        totalElements: books.length,
        totalPages: Math.max(1, Math.ceil(books.length / size)),
        hasNext: (page + 1) * size < books.length,
      },
    };
  }

  const res = await client.get<ApiResponse<SpringPage<BookmarkedBookDto>>>('/users/me/bookmarks', {
    params: { page, size },
  });

  return {
    books: res.data.data.content.map(toBook),
    meta: res.data.meta ?? DEFAULT_META,
  };
};

export const getMyQuizHistory = async (isMockMode: boolean): Promise<QuizHistoryItem[]> => {
  if (isMockMode) {
    await new Promise((resolve) => setTimeout(resolve, 300));
    return getMockQuizHistory();
  }

  const res = await client.get<ApiResponse<SpringPage<QuizHistoryDto> | QuizHistoryDto[]>>('/users/me/quizzes');
  const data = res.data.data;
  return Array.isArray(data) ? data.map(toQuizHistory) : data.content.map(toQuizHistory);
};
