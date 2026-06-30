import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { getBookById } from '../api/bookApi';
import type { Book } from '../api/bookApi';
import {
  createIllustration,
  getIllustrationJob,
  getIllustrations,
  getScenes,
  regenerateIllustration,
  STYLE_META,
} from '../api/illustrationApi';
import type {
  Illustration,
  IllustrationJob,
  IllustrationJobStatus,
  IllustrationStyle,
  Scene,
} from '../api/illustrationApi';
import { DBack, DkTopNav } from '../components/Primitives';
import {
  AlertCircle,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Loader2,
  RefreshCw,
  Sparkles,
  X,
  ZoomIn,
} from 'lucide-react';

// ─── Sub-components ──────────────────────────────────────────────────────────

interface StylePickerProps {
  selected: IllustrationStyle;
  onChange: (s: IllustrationStyle) => void;
}

const StylePicker: React.FC<StylePickerProps> = ({ selected, onChange }) => (
  <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
    {(Object.keys(STYLE_META) as IllustrationStyle[]).map((style) => {
      const meta = STYLE_META[style];
      const isSelected = style === selected;
      return (
        <button
          key={style}
          id={`style-btn-${style}`}
          onClick={() => onChange(style)}
          className={`
            relative group rounded-2xl p-3 text-left transition-all duration-200 border
            ${
              isSelected
                ? 'border-purple-500/60 bg-gradient-to-br ' +
                  meta.color +
                  ' shadow-lg shadow-purple-500/10'
                : 'border-black/5 dark:border-white/5 glass-soft hover:border-purple-400/30'
            }
          `}
        >
          <div
            className={`
            w-8 h-8 rounded-xl mb-2 bg-gradient-to-br ${meta.color}
            flex items-center justify-center transition-transform duration-200
            ${isSelected ? 'scale-110' : 'group-hover:scale-105'}
          `}
          >
            <Sparkles
              className={`w-4 h-4 ${isSelected ? 'text-purple-500' : 'text-slate-400 dark:text-white/40'}`}
            />
          </div>
          <p
            className={`text-xs font-semibold ${isSelected ? 'text-purple-600 dark:text-purple-300' : 'text-slate-700 dark:text-white/80'}`}
          >
            {meta.label}
          </p>
          <p className="text-[10px] text-slate-400 dark:text-white/35 mt-0.5 leading-tight">
            {meta.desc}
          </p>
          {isSelected && (
            <CheckCircle2 className="absolute top-2 right-2 w-4 h-4 text-purple-500" />
          )}
        </button>
      );
    })}
  </div>
);

interface JobStatusBadgeProps {
  status: IllustrationJobStatus;
}

const JobStatusBadge: React.FC<JobStatusBadgeProps> = ({ status }) => {
  const map: Record<IllustrationJobStatus, { label: string; cls: string }> = {
    QUEUED: { label: '대기 중', cls: 'bg-amber-500/10 text-amber-500' },
    PROCESSING: { label: '생성 중', cls: 'bg-blue-500/10 text-blue-500' },
    COMPLETED: { label: '완료', cls: 'bg-emerald-500/10 text-emerald-500' },
    FAILED: { label: '실패', cls: 'bg-red-500/10 text-red-500' },
  };
  const { label, cls } = map[status];
  return (
    <span className={`inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full ${cls}`}>
      {(status === 'QUEUED' || status === 'PROCESSING') && (
        <Loader2 className="w-3 h-3 animate-spin" />
      )}
      {label}
    </span>
  );
};

// ─── Scene Card ───────────────────────────────────────────────────────────────

interface SceneCardProps {
  scene: Scene;
  illustrations: Illustration[];
  activeJob: IllustrationJob | null;
  onGenerate: (sceneId: number) => void;
  onRegenerate: (illustrationId: string) => void;
  onZoom: (url: string, title: string) => void;
  isGenerating: boolean;
}

const SceneCard: React.FC<SceneCardProps> = ({
  scene,
  illustrations,
  activeJob,
  onGenerate,
  onRegenerate,
  onZoom,
  isGenerating,
}) => {
  const latestIllust = illustrations[illustrations.length - 1] ?? null;
  const isJobActive =
    activeJob?.status === 'QUEUED' || activeJob?.status === 'PROCESSING';

  return (
    <div className="glass rounded-2xl overflow-hidden flex flex-col transition-all duration-300 hover:shadow-lg hover:shadow-purple-950/10">
      {/* Image area */}
      <div className="relative aspect-[4/3] bg-gradient-to-br from-slate-900/20 to-slate-900/40 dark:from-white/3 dark:to-white/1">
        {latestIllust ? (
          <>
            <img
              src={latestIllust.imageUrl}
              alt={`${scene.title} 일러스트`}
              className="w-full h-full object-cover"
              onError={(e) => {
                (e.target as HTMLImageElement).src =
                  'https://images.unsplash.com/photo-1532012197267-da84d127e765?w=800&auto=format';
              }}
            />
            {/* Overlay buttons */}
            <div className="absolute inset-0 bg-black/0 hover:bg-black/30 transition-all duration-200 flex items-center justify-center gap-2 opacity-0 hover:opacity-100">
              <button
                id={`zoom-btn-${scene.sceneId}`}
                onClick={() => onZoom(latestIllust.imageUrl, scene.title)}
                className="p-2.5 rounded-full bg-white/20 backdrop-blur-sm text-white hover:bg-white/30 transition"
              >
                <ZoomIn className="w-4 h-4" />
              </button>
              <button
                id={`regen-btn-${scene.sceneId}`}
                onClick={() => onRegenerate(latestIllust.illustrationId)}
                disabled={isJobActive || isGenerating}
                className="p-2.5 rounded-full bg-white/20 backdrop-blur-sm text-white hover:bg-white/30 transition disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <RefreshCw className="w-4 h-4" />
              </button>
            </div>
            {/* Style badge */}
            <div className="absolute top-2 left-2">
              <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded-full bg-black/40 backdrop-blur-sm text-white/80">
                {STYLE_META[latestIllust.style]?.label ?? latestIllust.style}
              </span>
            </div>
          </>
        ) : isJobActive ? (
          <div className="w-full h-full flex flex-col items-center justify-center gap-3">
            <div className="relative">
              <div className="w-12 h-12 rounded-full border-2 border-purple-500/20 border-t-purple-500 animate-spin" />
              <Sparkles className="absolute inset-0 m-auto w-5 h-5 text-purple-400" />
            </div>
            <div className="text-center">
              <JobStatusBadge status={activeJob.status} />
              <p className="text-[10px] text-slate-400 dark:text-white/35 mt-1">
                {STYLE_META[activeJob.style]?.label} 스타일
              </p>
            </div>
          </div>
        ) : activeJob?.status === 'FAILED' ? (
          <div className="w-full h-full flex flex-col items-center justify-center gap-2 p-4">
            <AlertCircle className="w-8 h-8 text-red-400" />
            <p className="text-xs text-red-400 text-center">
              {activeJob.errorMessage ?? '생성에 실패했습니다.'}
            </p>
          </div>
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <Sparkles className="w-10 h-10 text-slate-300 dark:text-white/15" />
          </div>
        )}
      </div>

      {/* Info area */}
      <div className="p-4 flex flex-col gap-3 flex-1">
        <div>
          <p className="text-xs font-mono text-purple-500/70 dark:text-purple-400/60 mb-0.5">
            Scene {scene.sceneId}
          </p>
          <h3 className="text-sm font-semibold text-slate-800 dark:text-white leading-tight">
            {scene.title}
          </h3>
          <p className="text-[11px] text-slate-500 dark:text-white/40 mt-1.5 leading-relaxed line-clamp-2">
            {scene.excerpt}
          </p>
        </div>

        <div className="mt-auto flex items-center gap-2">
          {activeJob && <JobStatusBadge status={activeJob.status} />}
          {illustrations.length > 0 && !isJobActive && (
            <span className="text-[10px] text-slate-400 dark:text-white/30">
              {illustrations.length}개 생성됨
            </span>
          )}
          <div className="ml-auto">
            {!latestIllust && !isJobActive ? (
              <button
                id={`generate-btn-${scene.sceneId}`}
                onClick={() => onGenerate(scene.sceneId)}
                disabled={isGenerating}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-purple-600 hover:bg-purple-500 disabled:bg-purple-600/40 disabled:cursor-not-allowed text-white text-[11px] font-semibold transition-all duration-200"
              >
                <Sparkles className="w-3.5 h-3.5" />
                생성하기
              </button>
            ) : activeJob?.status === 'FAILED' && activeJob.retryable ? (
              <button
                id={`retry-btn-${scene.sceneId}`}
                onClick={() => onGenerate(scene.sceneId)}
                disabled={isGenerating}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-red-600 hover:bg-red-500 disabled:opacity-40 text-white text-[11px] font-semibold transition-all duration-200"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                재시도
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
};

// ─── Lightbox ────────────────────────────────────────────────────────────────

interface LightboxProps {
  url: string;
  title: string;
  onClose: () => void;
}

const Lightbox: React.FC<LightboxProps> = ({ url, title, onClose }) => {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-center justify-center p-4"
      onClick={onClose}
    >
      <div
        className="relative max-w-4xl w-full"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          id="lightbox-close-btn"
          onClick={onClose}
          className="absolute -top-10 right-0 p-2 text-white/60 hover:text-white transition"
        >
          <X className="w-5 h-5" />
        </button>
        <img
          src={url}
          alt={title}
          className="w-full max-h-[80vh] object-contain rounded-2xl shadow-2xl"
          onError={(e) => {
            (e.target as HTMLImageElement).src =
              'https://images.unsplash.com/photo-1532012197267-da84d127e765?w=800&auto=format';
          }}
        />
        <p className="text-center text-white/60 text-xs mt-3">{title}</p>
      </div>
    </div>
  );
};

// ─── Main Page ────────────────────────────────────────────────────────────────

export const IllustrationGallery: React.FC = () => {
  const { bookId } = useParams<{ bookId: string }>();
  const { user, logout, isMockMode, sessionExpiresAt, extendSession } = useAuth();
  const navigate = useNavigate();

  // Book info
  const [book, setBook] = useState<Book | null>(null);
  const [bookLoading, setBookLoading] = useState(true);

  // Scenes
  const [scenes, setScenes] = useState<Scene[]>([]);
  const [scenePage, setScenePage] = useState(0);
  const [sceneMeta, setSceneMeta] = useState<{
    totalPages: number;
    hasNext: boolean;
  } | null>(null);
  const [scenesLoading, setScenesLoading] = useState(true);
  const [scenesError, setScenesError] = useState<string | null>(null);

  // Illustrations per scene
  const [illustrations, setIllustrations] = useState<
    Map<number, Illustration[]>
  >(new Map());

  // Active jobs per scene  (sceneId → IllustrationJob)
  const [activeJobs, setActiveJobs] = useState<Map<number, IllustrationJob>>(
    new Map(),
  );
  const pollingRefs = useRef<Map<number, ReturnType<typeof setInterval>>>(
    new Map(),
  );

  // Style selection
  const [selectedStyle, setSelectedStyle] =
    useState<IllustrationStyle>('WATERCOLOR');

  // Generating flag (prevent double-click)
  const [generatingSceneId, setGeneratingSceneId] = useState<number | null>(
    null,
  );

  // Lightbox
  const [lightbox, setLightbox] = useState<{
    url: string;
    title: string;
  } | null>(null);

  // Toast
  const [toast, setToast] = useState<{
    message: string;
    type: 'success' | 'error';
  } | null>(null);

  const showToast = (message: string, type: 'success' | 'error') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3500);
  };

  // ── Load book info ──────────────────────────────────────────────────────────
  useEffect(() => {
    if (!bookId) return;
    getBookById(isMockMode, bookId)
      .then(setBook)
      .catch(() => {})
      .finally(() => setBookLoading(false));
  }, [bookId, isMockMode]);

  // ── Load scenes ─────────────────────────────────────────────────────────────
  const loadScenes = useCallback(async () => {
    if (!bookId) return;
    setScenesLoading(true);
    setScenesError(null);
    try {
      const result = await getScenes(isMockMode, bookId, scenePage);
      setScenes(result.scenes);
      setSceneMeta({
        totalPages: result.meta.totalPages,
        hasNext: result.meta.hasNext,
      });

      // Load illustrations for each scene
      const illusMap = new Map<number, Illustration[]>();
      await Promise.allSettled(
        result.scenes.map(async (scene) => {
          try {
            const illusts = await getIllustrations(
              isMockMode,
              scene.sceneId,
            );
            illusMap.set(scene.sceneId, illusts);
          } catch {
            illusMap.set(scene.sceneId, []);
          }
        }),
      );
      setIllustrations(illusMap);
    } catch {
      setScenesError('장면 목록을 불러오는데 실패했습니다.');
    } finally {
      setScenesLoading(false);
    }
  }, [bookId, isMockMode, scenePage]);

  useEffect(() => {
    let cancelled = false;
    const fetchData = async () => {
      if (!bookId) return;
      setScenesLoading(true);
      setScenesError(null);
      try {
        const result = await getScenes(isMockMode, bookId, scenePage);
        if (cancelled) return;
        setScenes(result.scenes);
        setSceneMeta({
          totalPages: result.meta.totalPages,
          hasNext: result.meta.hasNext,
        });
        const illusMap = new Map<number, Illustration[]>();
        await Promise.allSettled(
          result.scenes.map(async (scene) => {
            try {
              const illusts = await getIllustrations(isMockMode, scene.sceneId);
              illusMap.set(scene.sceneId, illusts);
            } catch {
              illusMap.set(scene.sceneId, []);
            }
          }),
        );
        if (!cancelled) setIllustrations(illusMap);
      } catch {
        if (!cancelled) setScenesError('장면 목록을 불러오는데 실패했습니다.');
      } finally {
        if (!cancelled) setScenesLoading(false);
      }
    };
    fetchData();
    return () => { cancelled = true; };
  }, [bookId, isMockMode, scenePage]);

  // ── Polling ─────────────────────────────────────────────────────────────────
  const startPolling = useCallback(
    (job: IllustrationJob) => {
      const { jobId, sceneId } = job;
      if (pollingRefs.current.has(sceneId)) return;

      const intervalId = setInterval(async () => {
        try {
          const updated = await getIllustrationJob(isMockMode, jobId);
          setActiveJobs((prev) => {
            const next = new Map(prev);
            next.set(sceneId, updated);
            return next;
          });

          if (
            updated.status === 'COMPLETED' ||
            updated.status === 'FAILED'
          ) {
            clearInterval(pollingRefs.current.get(sceneId));
            pollingRefs.current.delete(sceneId);

            if (updated.status === 'COMPLETED') {
              // Refresh illustrations for this scene
              getIllustrations(isMockMode, sceneId)
                .then((illusts) => {
                  setIllustrations((prev) => {
                    const next = new Map(prev);
                    next.set(sceneId, illusts);
                    return next;
                  });
                })
                .catch(() => {});
              showToast('일러스트가 생성되었습니다! ✨', 'success');
            } else {
              showToast(
                updated.errorMessage ?? '생성에 실패했습니다.',
                'error',
              );
            }
          }
        } catch {
          // silent
        }
      }, 3000);

      pollingRefs.current.set(sceneId, intervalId);
    },
    [isMockMode],
  );

  // Cleanup on unmount
  useEffect(() => {
    const refs = pollingRefs.current;
    return () => {
      refs.forEach((id) => clearInterval(id));
    };
  }, []);

  // ── Generate ────────────────────────────────────────────────────────────────
  const handleGenerate = async (sceneId: number) => {
    if (generatingSceneId !== null) return;
    const existingJob = activeJobs.get(sceneId);
    if (
      existingJob?.status === 'QUEUED' ||
      existingJob?.status === 'PROCESSING'
    )
      return;

    setGeneratingSceneId(sceneId);
    try {
      const job = await createIllustration(
        isMockMode,
        sceneId,
        selectedStyle,
      );
      setActiveJobs((prev) => {
        const next = new Map(prev);
        next.set(sceneId, job);
        return next;
      });
      startPolling(job);
      showToast('일러스트 생성을 요청했습니다.', 'success');
    } catch {
      showToast('생성 요청에 실패했습니다. 다시 시도해 주세요.', 'error');
    } finally {
      setGeneratingSceneId(null);
    }
  };

  // ── Regenerate ──────────────────────────────────────────────────────────────
  const handleRegenerate = async (illustrationId: string, sceneId: number) => {
    if (generatingSceneId !== null) return;
    setGeneratingSceneId(sceneId);
    try {
      const job = await regenerateIllustration(isMockMode, illustrationId);
      setActiveJobs((prev) => {
        const next = new Map(prev);
        next.set(sceneId, job);
        return next;
      });
      startPolling(job);
      showToast('재생성을 요청했습니다.', 'success');
    } catch {
      showToast('재생성 요청에 실패했습니다.', 'error');
    } finally {
      setGeneratingSceneId(null);
    }
  };

  // ─── Render ──────────────────────────────────────────────────────────────────

  return (
    <div className="min-h-screen dk-surface flex flex-col selection:bg-purple-500 selection:text-white relative">
      <div className="dk-grain absolute inset-0 opacity-40 pointer-events-none" />

      {/* Decorative Orbs */}
      <div className="absolute top-[5%] right-[10%] w-[500px] h-[500px] rounded-full bg-purple-600/5 dark:bg-purple-900/10 blur-[160px] pointer-events-none" />
      <div className="absolute bottom-[10%] left-[5%] w-[400px] h-[400px] rounded-full bg-amber-600/5 dark:bg-amber-900/8 blur-[140px] pointer-events-none" />

      <DkTopNav
        active="home"
        go={(tab) => {
          if (tab === 'home' || tab === 'search' || tab === 'history') navigate('/');
          else if (tab === 'me') navigate('/me');
          else if (tab === 'admin') navigate('/admin');
        }}
        onLogout={logout}
        onExtendSession={extendSession}
        nickname={user?.nickname || '민'}
        userRole={user?.role}
        sessionExpiresAt={sessionExpiresAt}
      />

      <div className="w-full max-w-5xl mx-auto px-4 pt-24 pb-16 z-10 flex flex-col gap-6">
        {/* Back & Title */}
        <div>
          <button
            id="back-btn"
            onClick={() => navigate(bookId ? `/books/${bookId}` : '/')}
            className="inline-flex items-center gap-1.5 text-slate-500 dark:text-white/50 hover:text-slate-800 dark:hover:text-white text-xs font-semibold mb-5 transition"
          >
            <DBack className="w-4 h-4" />
            도서 상세로
          </button>

          <div className="flex items-start justify-between gap-4">
            <div>
              {!bookLoading && book && (
                <p className="text-xs font-mono text-purple-500/70 dark:text-purple-400/60 mb-1">
                  {book.author} · {book.genre}
                </p>
              )}
              <h1 className="font-serif text-2xl md:text-3xl font-bold text-slate-800 dark:text-white">
                {bookLoading ? '...' : (book?.title ?? '장면 갤러리')}
              </h1>
              <p className="text-xs text-slate-500 dark:text-white/40 mt-1">
                AI가 그려낸 장면 일러스트 컬렉션
              </p>
            </div>
          </div>
        </div>

        {/* Style Picker */}
        <div className="glass rounded-2xl p-5">
          <div className="flex items-center gap-2 mb-4">
            <Sparkles className="w-4 h-4 text-purple-500" />
            <h2 className="text-sm font-semibold text-slate-700 dark:text-white/80">
              일러스트 스타일 선택
            </h2>
            <span className="text-[10px] text-slate-400 dark:text-white/30 ml-auto">
              생성할 때 적용됩니다
            </span>
          </div>
          <StylePicker selected={selectedStyle} onChange={setSelectedStyle} />
        </div>

        {/* Scene Grid */}
        {scenesLoading ? (
          <div className="flex flex-col items-center justify-center py-24 gap-4">
            <div className="w-10 h-10 rounded-full border-2 border-purple-500/20 border-t-purple-500 animate-spin" />
            <p className="text-xs text-slate-400 dark:text-white/30">
              장면을 불러오는 중...
            </p>
          </div>
        ) : scenesError ? (
          <div className="glass rounded-2xl p-10 flex flex-col items-center gap-3">
            <AlertCircle className="w-8 h-8 text-red-400" />
            <p className="text-sm text-red-400">{scenesError}</p>
            <button
              id="retry-load-btn"
              onClick={loadScenes}
              className="text-xs text-purple-500 hover:underline"
            >
              다시 시도
            </button>
          </div>
        ) : scenes.length === 0 ? (
          <div className="glass rounded-2xl p-10 flex flex-col items-center gap-3">
            <Sparkles className="w-8 h-8 text-slate-300 dark:text-white/15" />
            <p className="text-sm text-slate-500 dark:text-white/40">
              등록된 장면이 없습니다.
            </p>
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {scenes.map((scene) => (
                <SceneCard
                  key={scene.sceneId}
                  scene={scene}
                  illustrations={illustrations.get(scene.sceneId) ?? []}
                  activeJob={activeJobs.get(scene.sceneId) ?? null}
                  onGenerate={handleGenerate}
                  onRegenerate={(illustId) =>
                    handleRegenerate(illustId, scene.sceneId)
                  }
                  onZoom={(url, title) => setLightbox({ url, title })}
                  isGenerating={generatingSceneId !== null}
                />
              ))}
            </div>

            {/* Pagination */}
            {sceneMeta && sceneMeta.totalPages > 1 && (
              <div className="flex items-center justify-center gap-3 mt-2">
                <button
                  id="prev-page-btn"
                  onClick={() => setScenePage((p) => Math.max(0, p - 1))}
                  disabled={scenePage === 0}
                  className="p-2 rounded-full glass-soft disabled:opacity-30 hover:bg-black/[0.04] dark:hover:bg-white/[0.06] transition"
                >
                  <ChevronLeft className="w-4 h-4 text-slate-600 dark:text-white/60" />
                </button>
                <span className="text-xs text-slate-500 dark:text-white/40 font-mono">
                  {scenePage + 1} / {sceneMeta.totalPages}
                </span>
                <button
                  id="next-page-btn"
                  onClick={() =>
                    setScenePage((p) =>
                      sceneMeta.hasNext ? p + 1 : p,
                    )
                  }
                  disabled={!sceneMeta.hasNext}
                  className="p-2 rounded-full glass-soft disabled:opacity-30 hover:bg-black/[0.04] dark:hover:bg-white/[0.06] transition"
                >
                  <ChevronRight className="w-4 h-4 text-slate-600 dark:text-white/60" />
                </button>
              </div>
            )}
          </>
        )}
      </div>

      {/* Lightbox */}
      {lightbox && (
        <Lightbox
          url={lightbox.url}
          title={lightbox.title}
          onClose={() => setLightbox(null)}
        />
      )}

      {/* Toast */}
      {toast && (
        <div
          className={`
            fixed bottom-6 left-1/2 -translate-x-1/2 z-50
            flex items-center gap-2 px-4 py-2.5 rounded-full shadow-lg
            text-xs font-semibold backdrop-blur-md
            transition-all duration-300 animate-in slide-in-from-bottom-4
            ${
              toast.type === 'success'
                ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/20'
                : 'bg-red-500/20 text-red-400 border border-red-500/20'
            }
          `}
        >
          {toast.type === 'success' ? (
            <CheckCircle2 className="w-3.5 h-3.5" />
          ) : (
            <AlertCircle className="w-3.5 h-3.5" />
          )}
          {toast.message}
        </div>
      )}
    </div>
  );
};

export default IllustrationGallery;
