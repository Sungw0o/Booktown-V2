import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { getBookById, toggleBookmarkApi } from '../api/bookApi';
import type { Book } from '../api/bookApi';
import { DkTopNav, DkCover, DkBadge, DBack } from '../components/Primitives';
import { Loader2, Heart, BookOpen, AlertCircle, FileText, Image, CheckSquare } from 'lucide-react';

export const BookDetail: React.FC = () => {
  const { bookId } = useParams<{ bookId: string }>();
  const { user, logout, isMockMode, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [book, setBook] = useState<Book | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  // Optimistic UI를 위한 로컬 북마크 상태
  const [isBookmarked, setIsBookmarked] = useState(false);
  const [bookmarkMutating, setBookmarkMutating] = useState(false);

  useEffect(() => {
    if (!bookId) return;

    const fetchBookDetail = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await getBookById(isMockMode, bookId);
        setBook(data);
        setIsBookmarked(data.isBookmarked);
      } catch (err) {
        setError(err instanceof Error ? err.message : '도서 정보를 가져오는 데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchBookDetail();
  }, [bookId, isMockMode]);

  const handleBookmarkToggle = async () => {
    if (!isAuthenticated) {
      if (confirm('찜하기 기능은 로그인이 필요합니다. 로그인 화면으로 이동할까요?')) {
        navigate('/login');
      }
      return;
    }

    if (!book || bookmarkMutating) return;

    // Optimistic UI: 화면에 하트 색상을 즉각 반영
    const nextBookmarkState = !isBookmarked;
    setIsBookmarked(nextBookmarkState);
    setBookmarkMutating(true);

    try {
      await toggleBookmarkApi(isMockMode, book.id, nextBookmarkState);
      // 서버 전송 성공 시 local book 상태도 갱신
      setBook((prev) => prev ? { ...prev, isBookmarked: nextBookmarkState } : null);
    } catch (err) {
      // 에러 발생 시 원래 상태로 롤백
      setIsBookmarked(!nextBookmarkState);
      alert(err instanceof Error ? err.message : '북마크 처리에 실패했습니다. 다시 시도해 주세요.');
    } finally {
      setBookmarkMutating(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen dk-surface flex flex-col items-center justify-center relative">
        <div className="dk-grain" />
        <Loader2 className="w-10 h-10 text-purple-500 animate-spin" />
        <p className="text-slate-400 text-xs mt-3">명작의 첫 페이지를 펼치고 있습니다...</p>
      </div>
    );
  }

  if (error || !book) {
    return (
      <div className="min-h-screen dk-surface flex flex-col items-center justify-center p-6 relative">
        <div className="dk-grain" />
        <div className="glass p-8 rounded-2xl text-center max-w-md">
          <AlertCircle className="w-10 h-10 text-red-500 mx-auto mb-4" />
          <h2 className="text-lg font-medium text-slate-800 dark:text-white mb-2">조회 실패</h2>
          <p className="text-slate-500 dark:text-white/45 text-sm mb-6">{error || '도서를 찾을 수 없습니다.'}</p>
          <button
            onClick={() => navigate('/')}
            className="px-6 py-2.5 rounded-full bg-slate-900 text-white dark:bg-white dark:text-[#0B0E14] text-xs font-semibold"
          >
            서재로 돌아가기
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen dk-surface flex flex-col p-6 pt-24 selection:bg-purple-500 selection:text-white relative">
      <div className="dk-grain" />
      <DkTopNav
        active="home"
        go={(tab) => {
          if (tab === 'home') {
            navigate('/');
          } else if (tab === 'admin') {
            navigate('/admin');
          } else {
            alert('준비 중인 기능입니다!');
          }
        }}
        onLogout={logout}
        nickname={user?.nickname || '민'}
      />

      {/* Decorative Orbs */}
      <div className="absolute top-[10%] right-[15%] w-[450px] h-[450px] rounded-full bg-purple-600/5 dark:bg-purple-900/10 blur-[140px] pointer-events-none" />
      <div className="absolute bottom-[10%] left-[10%] w-[380px] h-[380px] rounded-full bg-amber-600/5 dark:bg-amber-900/10 blur-[130px] pointer-events-none" />

      <div className="w-full max-w-4xl mx-auto z-10 flex-1 flex flex-col">
        {/* Back Button */}
        <button
          onClick={() => navigate(-1)}
          className="inline-flex items-center gap-1.5 text-slate-500 dark:text-white/50 hover:text-slate-800 dark:hover:text-white text-xs font-semibold mb-6 transition"
        >
          <DBack className="w-4 h-4" />
          뒤로 가기
        </button>

        {/* Detail Panel */}
        <div className="glass rounded-3xl p-6 md:p-10 mb-8">
          <div className="grid grid-cols-1 md:grid-cols-[240px_1fr] gap-8">
            {/* Book Cover */}
            <div className="aspect-[4/5] w-full max-w-[240px] mx-auto rounded-xl overflow-hidden shadow-2xl shadow-purple-950/20 bg-slate-900/40 border border-black/5 dark:border-white/5">
              <DkCover book={{ id: book.id, title: book.title, author: book.author }} className="w-full h-full object-cover" />
            </div>

            {/* Information Info */}
            <div className="flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between gap-4">
                  <span className="px-3 py-1 rounded-full glass-soft text-[10px] text-purple-600 dark:text-purple-400 font-mono font-bold uppercase tracking-wider">
                    {book.genre}
                  </span>
                  
                  {/* Bookmark Button */}
                  <button
                    onClick={handleBookmarkToggle}
                    className="p-2.5 rounded-full glass-soft text-slate-500 dark:text-white/60 hover:text-rose-500 dark:hover:text-rose-400 transition"
                    title={isBookmarked ? '찜 취소하기' : '찜하기'}
                  >
                    <Heart className={`w-5 h-5 transition-transform duration-300 ${isBookmarked ? 'fill-rose-500 text-rose-500 scale-110' : 'hover:scale-105'}`} />
                  </button>
                </div>

                <h2 className="font-serif text-2xl md:text-3xl font-bold text-slate-800 dark:text-white mt-4 leading-tight">
                  {book.title}
                </h2>
                <p className="text-slate-500 dark:text-white/50 text-sm mt-1">{book.author} 저</p>

                {/* Badges */}
                <div className="flex flex-wrap items-center gap-1.5 mt-4">
                  {book.hasSummary && <DkBadge kind="요약" />}
                  {book.hasIllust && <DkBadge kind="일러스트" />}
                  {book.hasQuiz && <DkBadge kind="퀴즈" />}
                </div>

                {/* Description */}
                <p className="text-slate-600 dark:text-white/70 text-xs md:text-sm leading-relaxed font-light mt-6 border-t border-black/5 dark:border-white/5 pt-5">
                  {book.description}
                </p>
              </div>

              {/* Action Buttons */}
              <div className="grid grid-cols-3 gap-2 mt-8 border-t border-black/5 dark:border-white/5 pt-6">
                <button
                  disabled={!book.hasSummary}
                  onClick={() => alert(`${book.title} AI 요약 생성을 준비하고 있습니다.`)}
                  className="glass-soft disabled:opacity-30 rounded-xl py-3 text-[11px] font-semibold text-slate-700 dark:text-white hover:bg-black/[0.04] dark:hover:bg-white/[0.06] transition flex flex-col items-center gap-1.5"
                >
                  <FileText className="w-4 h-4 text-blue-500" />
                  AI 요약본
                </button>
                <button
                  disabled={!book.hasIllust}
                  onClick={() => alert(`${book.title} 일러스트 갤러리를 준비하고 있습니다.`)}
                  className="glass-soft disabled:opacity-30 rounded-xl py-3 text-[11px] font-semibold text-slate-700 dark:text-white hover:bg-black/[0.04] dark:hover:bg-white/[0.06] transition flex flex-col items-center gap-1.5"
                >
                  <Image className="w-4 h-4 text-amber-500" />
                  장면 갤러리
                </button>
                <button
                  disabled={!book.hasQuiz}
                  onClick={() => navigate(`/books/${book.id}/quiz`)}
                  className="glass-soft disabled:opacity-30 rounded-xl py-3 text-[11px] font-semibold text-slate-700 dark:text-white hover:bg-black/[0.04] dark:hover:bg-white/[0.06] transition flex flex-col items-center gap-1.5"
                >
                  <CheckSquare className="w-4 h-4 text-emerald-500" />
                  퀴즈 풀기
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Chapters Section */}
        <div className="glass rounded-3xl p-6 md:p-8 mb-12">
          <h3 className="font-serif text-lg font-semibold text-slate-800 dark:text-white mb-5 flex items-center gap-2">
            <BookOpen className="w-4 h-4 text-purple-500" />
            수록 목차
          </h3>
          <div className="divide-y divide-black/5 dark:divide-white/5">
            {book.chapters.map((chap, idx) => (
              <div
                key={idx}
                className="py-3.5 flex items-center justify-between text-xs md:text-sm text-slate-600 dark:text-white/70 font-light hover:text-slate-800 dark:hover:text-white transition duration-200"
              >
                <span>{chap}</span>
                <span className="text-[10px] font-mono text-slate-400 dark:text-white/35">Chapter {idx + 1}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default BookDetail;
