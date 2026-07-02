import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AlertCircle, BookOpen, ImageIcon, Loader2, Sparkles } from 'lucide-react';
import { getBookById, type Book } from '../api/bookApi';
import { getScenes, getIllustrations, type Illustration, type Scene } from '../api/illustrationApi';
import { getSummaries, type Summary } from '../api/summaryApi';
import { DBack, DkCover, DkTopNav } from '../components/Primitives';
import { useAuth } from '../hooks/useAuth';

const ReadingPage: React.FC = () => {
  const { bookId } = useParams<{ bookId: string }>();
  const { user, logout, isMockMode, sessionExpiresAt, extendSession } = useAuth();
  const navigate = useNavigate();
  const [book, setBook] = useState<Book | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [scenes, setScenes] = useState<Scene[]>([]);
  const [illustrations, setIllustrations] = useState<Map<number, Illustration[]>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!bookId) return;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const [bookData, summariesData, scenePage] = await Promise.all([
          getBookById(isMockMode, bookId),
          getSummaries(isMockMode, bookId).catch(() => []),
          getScenes(isMockMode, bookId, 0, 12).catch(() => ({ scenes: [], meta: { page: 0, size: 12, totalElements: 0, totalPages: 0, hasNext: false } })),
        ]);
        setBook(bookData);
        setSummary(summariesData[0] ?? null);
        setScenes(scenePage.scenes);

        const entries = await Promise.all(
          scenePage.scenes.slice(0, 8).map(async (scene) => {
            const images = await getIllustrations(isMockMode, scene.sceneId).catch(() => []);
            return [scene.sceneId, images] as const;
          }),
        );
        setIllustrations(new Map(entries));
      } catch (err) {
        setError(err instanceof Error ? err.message : '읽기 데이터를 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, [bookId, isMockMode]);

  if (loading) {
    return (
      <div className="min-h-screen dk-surface grid place-items-center">
        <div className="dk-grain" />
        <div className="relative z-10 text-center">
          <Loader2 className="w-8 h-8 text-purple-500 animate-spin mx-auto" />
          <p className="mt-3 text-xs text-slate-400">요약과 장면을 엮고 있습니다...</p>
        </div>
      </div>
    );
  }

  if (error || !book) {
    return (
      <div className="min-h-screen dk-surface grid place-items-center p-6">
        <div className="glass rounded-2xl p-8 text-center">
          <AlertCircle className="w-10 h-10 mx-auto text-red-500 mb-4" />
          <p className="text-sm text-slate-500 dark:text-white/50">{error || '도서를 찾을 수 없습니다.'}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen dk-surface p-6 pt-24 relative">
      <div className="dk-grain" />
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

      <main className="relative z-10 max-w-5xl mx-auto space-y-6">
        <button
          onClick={() => navigate(`/books/${book.id}`)}
          className="inline-flex items-center gap-1.5 text-slate-500 dark:text-white/50 hover:text-slate-800 dark:hover:text-white text-xs font-semibold transition"
        >
          <DBack className="w-4 h-4" />
          도서 상세로
        </button>

        <section className="glass rounded-3xl p-6 md:p-8 grid grid-cols-1 md:grid-cols-[180px_1fr] gap-6">
          <div className="aspect-[3/4] rounded-2xl overflow-hidden bg-slate-900/40">
            {book.coverImageUrl ? (
              <img src={book.coverImageUrl} alt={`${book.title} 표지`} className="w-full h-full object-cover" />
            ) : (
              <DkCover book={{ id: book.id, title: book.title, author: book.author }} className="w-full h-full" />
            )}
          </div>
          <div className="min-w-0">
            <p className="text-[10px] font-mono text-purple-500 font-bold uppercase tracking-wider mb-2">{book.genre}</p>
            <h1 className="font-serif text-3xl md:text-4xl font-bold text-slate-800 dark:text-white leading-tight">{book.title}</h1>
            <p className="text-sm text-slate-500 dark:text-white/45 mt-2">{book.author}</p>
            <p className="text-sm leading-relaxed text-slate-600 dark:text-white/65 mt-5">{book.description}</p>
          </div>
        </section>

        <section className="glass rounded-3xl p-6 md:p-8">
          <h2 className="font-serif text-xl font-semibold text-slate-800 dark:text-white flex items-center gap-2 mb-5">
            <Sparkles className="w-4 h-4 text-purple-500" />
            AI 요약본
          </h2>
          {summary ? (
            <article className="prose prose-sm dark:prose-invert max-w-none whitespace-pre-wrap text-slate-700 dark:text-white/70 leading-relaxed">
              {summary.content}
            </article>
          ) : (
            <p className="text-sm text-slate-500 dark:text-white/45">아직 생성된 요약본이 없습니다.</p>
          )}
        </section>

        <section className="glass rounded-3xl p-6 md:p-8 mb-10">
          <h2 className="font-serif text-xl font-semibold text-slate-800 dark:text-white flex items-center gap-2 mb-5">
            <ImageIcon className="w-4 h-4 text-amber-500" />
            장면 갤러리
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {scenes.map((scene) => {
              const firstImage = illustrations.get(scene.sceneId)?.[0];
              return (
                <div key={scene.sceneId} className="glass-soft rounded-2xl overflow-hidden">
                  <div className="aspect-video bg-black/10 dark:bg-white/5">
                    {firstImage ? (
                      <img src={firstImage.imageUrl} alt={scene.title} className="w-full h-full object-cover" />
                    ) : (
                      <div className="w-full h-full grid place-items-center text-slate-400 dark:text-white/35">
                        <BookOpen className="w-5 h-5" />
                      </div>
                    )}
                  </div>
                  <div className="p-4">
                    <p className="text-sm font-semibold text-slate-800 dark:text-white">{scene.title}</p>
                    <p className="text-xs leading-relaxed text-slate-500 dark:text-white/45 mt-2 line-clamp-3">{scene.excerpt}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      </main>
    </div>
  );
};

export default ReadingPage;
