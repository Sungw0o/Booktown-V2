import client from './client';

// ─── Types ──────────────────────────────────────────────────────────────────

export type IllustrationStyle = 'WEBTOON' | 'WATERCOLOR' | 'INK' | 'CLASSIC' | 'CINEMATIC';
export type IllustrationJobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface Scene {
  sceneId: number;
  bookId: number;
  chapterId: number;
  title: string;
  excerpt: string;
}

export interface IllustrationJob {
  jobId: number;
  sceneId: number;
  style: IllustrationStyle;
  status: IllustrationJobStatus;
  illustrationId: string | null;
  errorMessage: string | null;
  retryable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Illustration {
  illustrationId: string;
  sceneId: number;
  style: IllustrationStyle;
  imageUrl: string;
  isRegeneration: boolean;
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

export interface PagedScenes {
  scenes: Scene[];
  meta: PageMeta;
}

// ─── Style metadata ──────────────────────────────────────────────────────────

export const STYLE_META: Record<
  IllustrationStyle,
  { label: string; desc: string; color: string }
> = {
  WEBTOON: {
    label: '웹툰',
    desc: '표지와 같은 선화·채색을 유지하는 기본 스타일',
    color: 'from-rose-400/20 to-fuchsia-600/20',
  },
  WATERCOLOR: {
    label: '수채화',
    desc: '부드럽고 몽환적인 수채화 스타일',
    color: 'from-sky-400/20 to-blue-600/20',
  },
  INK: {
    label: '잉크화',
    desc: '선명한 먹선의 동양화 풍',
    color: 'from-slate-400/20 to-zinc-600/20',
  },
  CLASSIC: {
    label: '고전화',
    desc: '르네상스 풍 유화 클래식 스타일',
    color: 'from-amber-400/20 to-orange-600/20',
  },
  CINEMATIC: {
    label: '시네마틱',
    desc: '영화 스틸 같은 현대적 구도',
    color: 'from-purple-400/20 to-violet-600/20',
  },
};

// ─── Mock Data ───────────────────────────────────────────────────────────────

const MOCK_SCENES: Scene[] = [
  {
    sceneId: 1,
    bookId: 1,
    chapterId: 1,
    title: '무도회에서의 첫 만남',
    excerpt:
      '엘리자베스는 방 한쪽에 선 그를 바라보았다. 다아시 씨의 눈은 그녀와 마주쳤고, 두 사람 모두 아무 말도 하지 않았다.',
  },
  {
    sceneId: 2,
    bookId: 1,
    chapterId: 2,
    title: '비 내리는 오솔길',
    excerpt:
      '빗속에서 그가 다가왔다. 그의 목소리는 떨렸고, 그것이 고백인지 비난인지 알 수 없었다.',
  },
  {
    sceneId: 3,
    bookId: 1,
    chapterId: 3,
    title: '펨벌리 저택의 아침',
    excerpt:
      '드넓은 정원 너머로 펨벌리 저택이 아침 햇살 속에 빛나고 있었다. 그 순간 엘리자베스는 자신이 실수를 했음을 깨달았다.',
  },
  {
    sceneId: 4,
    bookId: 1,
    chapterId: 4,
    title: '제인의 편지',
    excerpt: '편지를 읽는 제인의 손이 떨렸다. 빙리 씨가 런던으로 떠났다는 소식이 담겨 있었다.',
  },
  {
    sceneId: 5,
    bookId: 1,
    chapterId: 5,
    title: '캐서린 부인의 방문',
    excerpt:
      '캐서린 드 버그 부인은 냉정하게 말했다. "당신은 내 조카와 결혼할 수 없어요." 엘리자베스는 당당히 고개를 들었다.',
  },
];

const MOCK_ILLUSTRATIONS: Map<number, Illustration[]> = new Map([
  [
    1,
    [
      {
        illustrationId: 'mock-illust-1',
        sceneId: 1,
        style: 'WEBTOON',
        imageUrl:
          'https://images.unsplash.com/photo-1518895949257-7621c3c786d7?w=800&auto=format',
        isRegeneration: false,
        createdAt: new Date().toISOString(),
      },
    ],
  ],
]);

let mockJobIdCounter = 100;
const MOCK_JOBS: Map<number, IllustrationJob> = new Map();

// ─── API Functions ────────────────────────────────────────────────────────────

export const getScenes = async (
  isMockMode: boolean,
  bookId: string | number,
  page = 0,
  size = 20,
): Promise<PagedScenes> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 400));
    const start = page * size;
    const sliced = MOCK_SCENES.slice(start, start + size);
    return {
      scenes: sliced,
      meta: {
        page,
        size,
        totalElements: MOCK_SCENES.length,
        totalPages: Math.ceil(MOCK_SCENES.length / size),
        hasNext: start + size < MOCK_SCENES.length,
      },
    };
  }

  const res = await client.get<ApiResponse<{ content: Scene[] }>>(
    `/books/${bookId}/scenes`,
    { params: { page, size } },
  );
  const content = res.data.data.content;
  const meta = res.data.meta ?? {
    page,
    size,
    totalElements: content.length,
    totalPages: 1,
    hasNext: false,
  };
  return { scenes: content, meta };
};

export const createIllustration = async (
  isMockMode: boolean,
  sceneId: number,
  style: IllustrationStyle,
  promptHint?: string,
): Promise<IllustrationJob> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 600));
    const jobId = mockJobIdCounter++;
    const job: IllustrationJob = {
      jobId,
      sceneId,
      style,
      status: 'QUEUED',
      illustrationId: null,
      errorMessage: null,
      retryable: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    MOCK_JOBS.set(jobId, job);
    // simulate async completion
    setTimeout(() => {
      const completing = MOCK_JOBS.get(jobId);
      if (completing) {
        MOCK_JOBS.set(jobId, { ...completing, status: 'PROCESSING' });
        setTimeout(() => {
          const done = MOCK_JOBS.get(jobId);
          if (done) {
            const illustrationId = `mock-illust-${jobId}`;
            MOCK_JOBS.set(jobId, {
              ...done,
              status: 'COMPLETED',
              illustrationId,
              updatedAt: new Date().toISOString(),
            });
            const mockIllust: Illustration = {
              illustrationId,
              sceneId,
              style,
              imageUrl: `https://images.unsplash.com/photo-${1500000000000 + jobId}?w=800&auto=format`,
              isRegeneration: false,
              createdAt: new Date().toISOString(),
            };
            const existing = MOCK_ILLUSTRATIONS.get(sceneId) ?? [];
            MOCK_ILLUSTRATIONS.set(sceneId, [...existing, mockIllust]);
          }
        }, 5000);
      }
    }, 2000);
    return job;
  }

  const res = await client.post<ApiResponse<IllustrationJob>>(
    `/scenes/${sceneId}/illustrations`,
    { style, promptHint },
  );
  return res.data.data;
};

export const getIllustrationJob = async (
  isMockMode: boolean,
  jobId: number,
): Promise<IllustrationJob> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 200));
    const job = MOCK_JOBS.get(jobId);
    if (!job) throw new Error('Job을 찾을 수 없습니다.');
    return job;
  }

  const res = await client.get<ApiResponse<IllustrationJob>>(
    `/illustration-jobs/${jobId}`,
  );
  return res.data.data;
};

export const getIllustrations = async (
  isMockMode: boolean,
  sceneId: number,
): Promise<Illustration[]> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 300));
    return MOCK_ILLUSTRATIONS.get(sceneId) ?? [];
  }

  const res = await client.get<ApiResponse<Illustration[]>>(
    `/scenes/${sceneId}/illustrations`,
  );
  return res.data.data;
};

export const regenerateIllustration = async (
  isMockMode: boolean,
  illustrationId: string,
): Promise<IllustrationJob> => {
  if (isMockMode) {
    await new Promise((r) => setTimeout(r, 600));
    const jobId = mockJobIdCounter++;
    // find sceneId from existing illustrations
    let sceneId = 1;
    for (const [sid, illusts] of MOCK_ILLUSTRATIONS.entries()) {
      if (illusts.some((i) => i.illustrationId === illustrationId)) {
        sceneId = sid;
        break;
      }
    }
    const existing = Array.from(MOCK_ILLUSTRATIONS.values())
      .flat()
      .find((i) => i.illustrationId === illustrationId);
    const style: IllustrationStyle = existing?.style ?? 'WEBTOON';
    const job: IllustrationJob = {
      jobId,
      sceneId,
      style,
      status: 'QUEUED',
      illustrationId: null,
      errorMessage: null,
      retryable: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    MOCK_JOBS.set(jobId, job);
    setTimeout(() => {
      const done = MOCK_JOBS.get(jobId);
      if (done) {
        const newId = `mock-regen-${jobId}`;
        MOCK_JOBS.set(jobId, {
          ...done,
          status: 'COMPLETED',
          illustrationId: newId,
          updatedAt: new Date().toISOString(),
        });
        const regenIllust: Illustration = {
          illustrationId: newId,
          sceneId,
          style,
          imageUrl: `https://images.unsplash.com/photo-${1600000000000 + jobId}?w=800&auto=format`,
          isRegeneration: true,
          createdAt: new Date().toISOString(),
        };
        const list = MOCK_ILLUSTRATIONS.get(sceneId) ?? [];
        MOCK_ILLUSTRATIONS.set(sceneId, [...list, regenIllust]);
      }
    }, 5000);
    return job;
  }

  const res = await client.post<ApiResponse<IllustrationJob>>(
    `/illustrations/${illustrationId}/regenerations`,
  );
  return res.data.data;
};
