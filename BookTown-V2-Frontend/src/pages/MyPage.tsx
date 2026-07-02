import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, BookMarked, ChevronRight, History, Loader2, Pencil, X } from 'lucide-react';
import { getMyBookmarks, getMyProfile, getMyQuizHistory, updateProfile } from '../api/userApi';
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

/* ---------- 프로필 아바타 ---------- */
const ProfileAvatar: React.FC<{
  nickname?: string;
  profileImageUrl?: string;
  size?: 'md' | 'lg';
  onClick?: () => void;
  showEditHint?: boolean;
}> = ({ nickname = '독', profileImageUrl, size = 'md', onClick, showEditHint }) => {
  const dim = size === 'lg' ? 'w-20 h-20 text-2xl' : 'w-14 h-14 text-xl';
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={!onClick}
      className={`relative ${dim} rounded-full overflow-hidden bg-[#7AA3D6]/15 border-2 border-[#7AA3D6]/25 text-[#3E6FA9] dark:text-[#7AA3D6] flex items-center justify-center group transition-all ${onClick ? 'hover:ring-4 hover:ring-[#7AA3D6]/25 cursor-pointer' : 'cursor-default'}`}
    >
      {profileImageUrl ? (
        <img src={profileImageUrl} alt="프로필" className="w-full h-full object-cover" />
      ) : (
        <span className="font-display font-bold select-none">{nickname.slice(0, 1)}</span>
      )}
      {showEditHint && (
        <div className="absolute inset-0 bg-black/35 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
          <Pencil className="w-4 h-4 text-white" />
        </div>
      )}
    </button>
  );
};

/* ---------- 프로필 편집 폼 ---------- */
const ProfileEditForm: React.FC<{
  current: UserProfile;
  isMockMode: boolean;
  onSave: (updated: UserProfile) => void;
  onCancel: () => void;
}> = ({ current, isMockMode, onSave, onCancel }) => {
  const [nickname, setNickname] = useState(current.nickname);
  const [imageUrl, setImageUrl] = useState(current.profileImageUrl ?? '');
  const [previewUrl, setPreviewUrl] = useState(current.profileImageUrl ?? '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSave = async () => {
    if (!nickname.trim()) {
      setError('닉네임을 입력해주세요.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const updated = await updateProfile(
        isMockMode,
        { nickname: nickname.trim(), profileImageUrl: imageUrl.trim() || undefined },
        current,
      );
      onSave(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : '저장에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mt-4 glass-soft rounded-2xl p-4 border border-[#7AA3D6]/20 space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-[11px] font-mono uppercase tracking-[0.16em] text-slate-500 dark:text-white/40">
          프로필 수정
        </span>
        <button
          type="button"
          onClick={onCancel}
          className="w-5 h-5 rounded-full hover:bg-black/8 dark:hover:bg-white/10 flex items-center justify-center text-slate-400 dark:text-white/35 transition"
        >
          <X className="w-3 h-3" />
        </button>
      </div>

      {/* 미리보기 아바타 */}
      <div className="flex justify-center">
        <ProfileAvatar nickname={nickname} profileImageUrl={previewUrl || undefined} size="lg" />
      </div>

      {/* 닉네임 */}
      <div className="space-y-1">
        <label className="text-[10px] font-mono uppercase tracking-wider text-slate-500 dark:text-white/38">
          닉네임
        </label>
        <input
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          placeholder="닉네임 입력"
          maxLength={20}
          className="w-full glass-soft rounded-xl px-3 py-2.5 text-[13px] text-slate-800 dark:text-white/88 placeholder:text-slate-400 dark:placeholder:text-white/28 focus:outline-none"
        />
      </div>

      {/* 프로필 이미지 URL */}
      <div className="space-y-1">
        <label className="text-[10px] font-mono uppercase tracking-wider text-slate-500 dark:text-white/38">
          프로필 이미지 URL
        </label>
        <input
          value={imageUrl}
          onChange={(e) => {
            setImageUrl(e.target.value);
            setPreviewUrl(e.target.value);
          }}
          placeholder="https://example.com/avatar.png"
          className="w-full glass-soft rounded-xl px-3 py-2.5 text-[13px] text-slate-800 dark:text-white/88 placeholder:text-slate-400 dark:placeholder:text-white/28 focus:outline-none"
        />
        <p className="text-[10px] text-slate-400 dark:text-white/30 pl-1">
          이미지 파일 업로드는 추후 지원 예정입니다.
        </p>
      </div>

      {error && (
        <div className="flex items-center gap-2 text-[12px] text-[#D46A6A]">
          <AlertCircle className="w-3.5 h-3.5 shrink-0" />
          {error}
        </div>
      )}

      <div className="flex gap-2 pt-1">
        <button
          type="button"
          onClick={handleSave}
          disabled={loading}
          className="flex-1 rounded-xl px-3 py-2.5 text-[12px] font-semibold text-white disabled:opacity-50 transition"
          style={{ background: 'linear-gradient(135deg, #7AA3D6 0%, #3E6FA9 100%)', boxShadow: '0 2px 12px rgba(122,163,214,0.35)' }}
        >
          {loading ? (
            <span className="flex items-center justify-center gap-1.5">
              <Loader2 className="w-3.5 h-3.5 animate-spin" /> 저장 중...
            </span>
          ) : '저장'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2.5 text-[12px] glass rounded-xl text-slate-600 dark:text-white/55 hover:text-slate-800 dark:hover:text-white/80 transition"
        >
          취소
        </button>
      </div>
    </div>
  );
};

export const MyPage: React.FC = () => {
  const navigate = useNavigate();
  const { user, logout, isMockMode, isAuthenticated, updateUser } = useAuth();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [bookmarks, setBookmarks] = useState<BookmarkPage | null>(null);
  const [bookmarksLoading, setBookmarksLoading] = useState(true);
  const [bookmarksError, setBookmarksError] = useState<string | null>(null);
  const [quizHistory, setQuizHistory] = useState<QuizHistoryItem[]>([]);
  const [quizLoading, setQuizLoading] = useState(true);
  const [quizError, setQuizError] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);

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

  const handleProfileSaved = (updated: UserProfile) => {
    setProfile(updated);
    updateUser({ nickname: updated.nickname, profileImageUrl: updated.profileImageUrl });
    setIsEditing(false);
  };

  const displayProfile = profile ?? (user as UserProfile | null);
  const bookmarkedBooks = bookmarks?.books ?? [];
  const bestAccuracy = quizHistory.length > 0 ? Math.max(...quizHistory.map((item) => item.accuracy)) : 0;

  return (
    <div className="min-h-screen dk-surface flex flex-col relative overflow-hidden p-5 md:p-8 md:pt-24 selection:bg-purple-500 selection:text-white">
      <div className="dk-grain" />
      <DkTopNav
        active="me"
        go={handleGo}
        onLogout={isAuthenticated ? handleLogout : undefined}
        nickname={displayProfile?.nickname || '독'}
        userRole={user?.role}
      />

      <main className="w-full max-w-6xl mx-auto z-10 flex-1 pb-24 md:pb-8">
        <section className="grid lg:grid-cols-[360px_minmax(0,1fr)] gap-5 md:gap-6">
          {/* ── 사이드 프로필 카드 ── */}
          <aside className="glass rounded-2xl p-5 md:p-6 border border-white/5">
            <div className="flex items-start gap-4">
              <ProfileAvatar
                nickname={displayProfile?.nickname}
                profileImageUrl={displayProfile?.profileImageUrl}
                onClick={() => !profileLoading && setIsEditing((v) => !v)}
                showEditHint={!profileLoading}
              />
              <div className="min-w-0 flex-1">
                <div className="text-[11px] font-mono uppercase tracking-[0.18em] text-slate-500 dark:text-white/40">
                  My Library
                </div>
                <h1 className="font-display text-2xl text-slate-900 dark:text-white font-bold truncate mt-0.5">
                  {profileLoading ? '불러오는 중' : displayProfile?.nickname ?? '독자'}
                </h1>
                <p className="text-xs text-slate-500 dark:text-white/45 truncate">
                  {displayProfile?.email ?? '이메일 정보 없음'}
                </p>
                {!profileLoading && (
                  <button
                    type="button"
                    onClick={() => setIsEditing((v) => !v)}
                    className="mt-2 inline-flex items-center gap-1 text-[10px] font-mono text-slate-400 dark:text-white/35 hover:text-[#7AA3D6] dark:hover:text-[#7AA3D6] transition"
                  >
                    <Pencil className="w-2.5 h-2.5" />
                    프로필 수정
                  </button>
                )}
              </div>
            </div>

            {profileError && (
              <div className="mt-4 glass-soft rounded-xl p-3 text-xs text-[#D46A6A]">{profileError}</div>
            )}

            {/* 인라인 편집 폼 */}
            {isEditing && displayProfile && (
              <ProfileEditForm
                current={displayProfile}
                isMockMode={isMockMode}
                onSave={handleProfileSaved}
                onCancel={() => setIsEditing(false)}
              />
            )}

            {/* 통계 카드 */}
            <div className="grid grid-cols-3 gap-2 mt-6">
              <div className="glass-soft rounded-xl p-3">
                <div className="text-[10px] text-slate-500 dark:text-white/40">찜</div>
                <div className="font-display text-2xl font-bold text-slate-900 dark:text-white mt-1">
                  {bookmarks?.meta.totalElements ?? 0}
                </div>
              </div>
              <div className="glass-soft rounded-xl p-3">
                <div className="text-[10px] text-slate-500 dark:text-white/40">퀴즈</div>
                <div className="font-display text-2xl font-bold text-slate-900 dark:text-white mt-1">
                  {quizHistory.length}
                </div>
              </div>
              <div className="glass-soft rounded-xl p-3">
                <div className="text-[10px] text-slate-500 dark:text-white/40">최고</div>
                <div className="font-display text-2xl font-bold text-slate-900 dark:text-white mt-1">
                  {bestAccuracy}%
                </div>
              </div>
            </div>
          </aside>

          {/* ── 메인 콘텐츠 ── */}
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
