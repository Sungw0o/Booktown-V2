import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, BookMarked, ChevronRight, History, Loader2, UserRound } from 'lucide-react';
import { getMyBookmarks, getMyProfile, getMyQuizHistory } from '../api/userApi';
import type { BookmarkPage, QuizHistoryItem, UserProfile } from '../api/userApi';
import { DkBadge, DkCover, DkTabs, DkTopNav } from '../components/Primitives';
import { useAuth } from '../hooks/useAuth';

const SectionState: React.FC<{ loading: boolean; error: string | null; empty: boolean; emptyText: string }> = ({
  loading,
  error,
  empty,
  emptyText,
}) => {
  if (loading) {
    return (
      <div className="glass-soft rounded-xl p-5 flex items-center gap-3 text-slate-500 dark:text-white/50 text-sm">
        <Loader2 className="w-4 h-4 animate-spin" />
        불러오는 중입니다...
      </div>
    );
  }

  if (error) {
    return (
      <div className="glass-soft rounded-xl p-5 flex items-center gap-3 text-[#D46A6A] text-sm">
        <AlertCircle className="w-4 h-4" />
        {error}
      </div>
    );
  }

  if (empty) {
    return (
      <div className="glass-soft rounded-xl p-5 text-slate-500 dark:text-white/45 text-sm">
        {emptyText}
      </div>
    );
  }

  return null;
};

export const MyPage: React.FC = () => {
  const navigate = useNavigate();
  const { user, logout, isMockMode, isAuthenticated } = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [bookmarks, setBookmarks] = useState<BookmarkPage | null>(null);
  const [bookmarksLoading, setBookmarksLoading] = useState(true);
  const [bookmarksError, setBookmarksError] = useState<string | null>(null);
  const [quizHistory, setQuizHistory] = useState<QuizHistoryItem[]>([]);
  const [quizLoading, setQuizLoading] = useState(true);
  const [quizError, setQuizError] = useState<string | null>(null);

  useEffect(() => {
    getMyProfile(isMockMode, user)
      .then(setProfile)
      .catch((err) => setProfileError(err instanceof Error ? err.message : '프로필을 불러오지 못했습니다.'))
      .finally(() => setProfileLoading(false));

    getMyBookmarks(isMockMode)
      .then(setBookmarks)
      .catch((err) => setBookmarksError(err instanceof Error ? err.message : '찜 목록을 불러오지 못했습니다.'))
      .finally(() => setBookmarksLoading(false));

    getMyQuizHistory(isMockMode)
      .then(setQuizHistory)
      .catch((err) => setQuizError(err instanceof Error ? err.message : '퀴즈 기록을 불러오지 못했습니다.'))
      .finally(() => setQuizLoading(false));
  }, [isMockMode, user]);

  const handleGo = (tab: string) => {
    if (tab === 'home' || tab === 'search' || tab === 'history') {
      navigate('/');
    } else if (tab === 'me') {
      navigate('/me');
    } else if (tab === 'admin') {
      navigate('/admin');
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const displayProfile = profile ?? user;
  const bookmarkedBooks = bookmarks?.books ?? [];
  const bestAccuracy = quizHistory.length > 0 ? Math.max(...quizHistory.map((item) => item.accuracy)) : 0;

  return (
    <div className="min-h-screen dk-surface flex flex-col relative overflow-hidden p-5 md:p-8 md:pt-24 selection:bg-purple-500 selection:text-white">
      <div className="dk-grain" />
      <DkTopNav
        active="me"
        go={handleGo}
        onLogout={isAuthenticated ? handleLogout : undefined}
        nickname={displayProfile?.nickname || '민'}
      />

      <main className="w-full max-w-6xl mx-auto z-10 flex-1 pb-24 md:pb-8">
        <section className="grid lg:grid-cols-[360px_minmax(0,1fr)] gap-5 md:gap-6">
          <aside className="glass rounded-2xl p-5 md:p-6 border border-white/5">
            <div className="flex items-center gap-4">
              <div className="w-14 h-14 rounded-2xl bg-[#7AA3D6]/15 border border-[#7AA3D6]/30 grid place-items-center text-[#7AA3D6]">
                <UserRound className="w-7 h-7" />
              </div>
              <div className="min-w-0">
                <div className="text-[11px] font-mono uppercase tracking-[0.18em] text-slate-500 dark:text-white/40">My Library</div>
                <h1 className="font-display text-2xl text-slate-900 dark:text-white font-bold truncate">
                  {profileLoading ? '불러오는 중' : displayProfile?.nickname ?? '독자'}
                </h1>
                <p className="text-xs text-slate-500 dark:text-white/45 truncate">{displayProfile?.email ?? '이메일 정보 없음'}</p>
              </div>
            </div>

            {profileError && (
              <div className="mt-4 glass-soft rounded-xl p-3 text-xs text-[#D46A6A]">{profileError}</div>
            )}

            <div className="grid grid-cols-3 gap-2 mt-6">
              <div className="glass-soft rounded-xl p-3">
                <div className="text-[10px] text-slate-500 dark:text-white/40">찜</div>
                <div className="font-display text-2xl font-bold text-slate-900 dark:text-white mt-1">{bookmarks?.meta.totalElements ?? 0}</div>
              </div>
              <div className="glass-soft rounded-xl p-3">
                <div className="text-[10px] text-slate-500 dark:text-white/40">퀴즈</div>
                <div className="font-display text-2xl font-bold text-slate-900 dark:text-white mt-1">{quizHistory.length}</div>
              </div>
              <div className="glass-soft rounded-xl p-3">
                <div className="text-[10px] text-slate-500 dark:text-white/40">최고</div>
                <div className="font-display text-2xl font-bold text-slate-900 dark:text-white mt-1">{bestAccuracy}%</div>
              </div>
            </div>
          </aside>

          <section className="space-y-5 md:space-y-6">
            <div className="glass rounded-2xl p-5 md:p-6 border border-white/5">
              <div className="flex items-center justify-between gap-3 mb-4">
                <div>
                  <div className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-[0.18em] text-slate-500 dark:text-white/40">
                    <BookMarked className="w-4 h-4" /> Bookmarks
                  </div>
                  <h2 className="font-display text-xl text-slate-900 dark:text-white font-bold mt-1">찜한 도서</h2>
                </div>
              </div>

              <SectionState
                loading={bookmarksLoading}
                error={bookmarksError}
                empty={bookmarkedBooks.length === 0}
                emptyText="아직 찜한 도서가 없습니다."
              />

              {bookmarkedBooks.length > 0 && (
                <div className="grid sm:grid-cols-2 xl:grid-cols-3 gap-3">
                  {bookmarkedBooks.map((book) => (
                    <button
                      key={book.id}
                      onClick={() => navigate(`/books/${book.id}`)}
                      className="glass-soft rounded-xl p-3 border border-white/5 text-left hover:border-white/15 transition flex gap-3"
                    >
                      <DkCover book={book} className="w-14 h-20 shrink-0 rounded-lg" />
                      <div className="min-w-0 flex-1">
                        <div className="font-semibold text-sm text-slate-900 dark:text-white truncate">{book.title}</div>
                        <div className="text-xs text-slate-500 dark:text-white/45 mt-1 truncate">{book.author}</div>
                        <div className="mt-2 flex flex-wrap gap-1">
                          <DkBadge kind={book.genre} size="xs" />
                          {book.hasQuiz && <DkBadge kind="퀴즈" size="xs" />}
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="glass rounded-2xl p-5 md:p-6 border border-white/5">
              <div className="flex items-center gap-2 text-[11px] font-mono uppercase tracking-[0.18em] text-slate-500 dark:text-white/40">
                <History className="w-4 h-4" /> Quiz History
              </div>
              <h2 className="font-display text-xl text-slate-900 dark:text-white font-bold mt-1 mb-4">퀴즈 기록</h2>

              <SectionState
                loading={quizLoading}
                error={quizError}
                empty={quizHistory.length === 0}
                emptyText="아직 완료한 퀴즈가 없습니다."
              />

              {quizHistory.length > 0 && (
                <div className="space-y-2">
                  {quizHistory.map((item) => (
                    <button
                      key={`${item.quizId}-${item.submittedAt}`}
                      onClick={() => navigate(`/books/${item.bookId}`)}
                      className="w-full glass-soft rounded-xl px-4 py-3 border border-white/5 hover:border-white/15 transition flex items-center justify-between gap-3 text-left"
                    >
                      <div className="min-w-0">
                        <div className="font-semibold text-sm text-slate-900 dark:text-white truncate">{item.bookTitle}</div>
                        <div className="text-xs text-slate-500 dark:text-white/45 mt-1">
                          {item.score}/{item.total} · 정답률 {item.accuracy}%
                        </div>
                      </div>
                      <ChevronRight className="w-4 h-4 text-slate-400 dark:text-white/35 shrink-0" />
                    </button>
                  ))}
                </div>
              )}
            </div>
          </section>
        </section>
      </main>

      <div className="md:hidden">
        <DkTabs active="me" go={handleGo} />
      </div>
    </div>
  );
};
