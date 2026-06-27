import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { getBooks } from '../api/bookApi';
import type { Book } from '../api/bookApi';
import { DkTopNav, DkCover, DkBadge } from '../components/Primitives';
import { Search, SlidersHorizontal, Loader2, BookOpen } from 'lucide-react';

export const BookHome: React.FC = () => {
  const { user, logout, isMockMode } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  // URL 쿼리 스트링에서 상태 복원
  const genreParam = searchParams.get('genre') || '전체';
  const queryParam = searchParams.get('q') || '';
  const sortParam = searchParams.get('sort') || 'latest';

  const [books, setBooks] = useState<Book[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 검색창 입력값 상태
  const [searchInput, setSearchInput] = useState(queryParam);

  const genres = ['전체', '소설', '희곡'];

  useEffect(() => {
    const fetchBooksData = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await getBooks(isMockMode, {
          genre: genreParam,
          q: queryParam,
        });

        // 정렬 조건 적용
        const sortedData = [...data];
        if (sortParam === 'latest') {
          sortedData.sort((a, b) => b.title.localeCompare(a.title));
        } else if (sortParam === 'popular') {
          sortedData.sort((a, b) => b.id.localeCompare(a.id));
        }

        setBooks(sortedData);
      } catch (err) {
        setError(err instanceof Error ? err.message : '도서 목록을 가져오는 데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchBooksData();
  }, [genreParam, queryParam, sortParam, isMockMode]);

  // 검색 제출 핸들러
  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const nextParams = new URLSearchParams(searchParams);
    if (searchInput.trim()) {
      nextParams.set('q', searchInput);
    } else {
      nextParams.delete('q');
    }
    nextParams.set('page', '1');
    setSearchParams(nextParams);
  };

  // 장르 변경 핸들러
  const handleGenreChange = (genre: string) => {
    const nextParams = new URLSearchParams(searchParams);
    if (genre !== '전체') {
      nextParams.set('genre', genre);
    } else {
      nextParams.delete('genre');
    }
    nextParams.set('page', '1');
    setSearchParams(nextParams);
  };

  // 정렬 변경 핸들러
  const handleSortChange = (sort: string) => {
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('sort', sort);
    setSearchParams(nextParams);
  };

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
      <div className="absolute top-[15%] left-[20%] w-[400px] h-[400px] rounded-full bg-purple-600/5 dark:bg-purple-900/10 blur-[130px] pointer-events-none animate-pulse" />
      <div className="absolute bottom-[20%] right-[10%] w-[350px] h-[350px] rounded-full bg-amber-600/5 dark:bg-amber-900/10 blur-[120px] pointer-events-none animate-pulse delay-1000" />

      <div className="w-full max-w-6xl mx-auto z-10 flex-1 flex flex-col">
        {/* Header Title */}
        <div className="mb-10 text-left">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-purple-500/20 bg-purple-500/5 text-purple-600 dark:text-purple-400 text-xs font-semibold uppercase tracking-wider mb-3 font-mono">
            <BookOpen className="w-3.5 h-3.5" />
            Library Explorer
          </div>
          <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-slate-900 via-slate-800 to-purple-800 dark:from-white dark:via-slate-200 dark:to-purple-400 font-display">
            고전도서 서재
          </h1>
          <p className="text-slate-500 dark:text-slate-400 text-sm mt-1.5 font-light">
            시간을 이겨낸 명작들과 요약, 일러스트, 퀴즈로 만나는 독서 학습 경험
          </p>
        </div>

        {/* Filter & Search Bar */}
        <div className="glass rounded-2xl p-4 md:p-6 mb-8 flex flex-col md:flex-row md:items-center justify-between gap-4">
          {/* Genre Tabs */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 md:pb-0">
            {genres.map((g) => {
              const active = genreParam === g;
              return (
                <button
                  key={g}
                  onClick={() => handleGenreChange(g)}
                  className={`px-4 py-2 rounded-full text-[12px] transition-all font-medium ${
                    active
                      ? 'bg-[#0B0E14] text-white dark:bg-white dark:text-[#0B0E14] shadow-sm'
                      : 'glass-soft text-slate-500 dark:text-white/50 hover:text-slate-800 dark:hover:text-white'
                  }`}
                >
                  {g}
                </button>
              );
            })}
          </div>

          {/* Search & Sort Controls */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
            {/* Sort Select */}
            <div className="relative flex items-center">
              <SlidersHorizontal className="w-3.5 h-3.5 absolute left-3 text-slate-400 dark:text-white/40" />
              <select
                value={sortParam}
                onChange={(e) => handleSortChange(e.target.value)}
                className="glass-soft rounded-full pl-9 pr-8 py-2 text-[12px] text-slate-700 dark:text-white/80 appearance-none focus:outline-none focus:border-black/10 dark:focus:border-white/20 font-medium cursor-pointer"
              >
                <option value="latest">최신순</option>
                <option value="popular">인기순</option>
              </select>
            </div>

            {/* Search Input Form */}
            <form onSubmit={handleSearchSubmit} className="relative flex-1 sm:w-64">
              <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 dark:text-white/35" />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="도서명, 작가명 검색..."
                className="w-full glass-soft rounded-full pl-9 pr-4 py-2 text-[12px] text-slate-800 dark:text-white placeholder:text-slate-400 dark:placeholder:text-white/30 focus:outline-none focus:border-black/10 dark:focus:border-white/20"
              />
            </form>
          </div>
        </div>

        {/* Book Grid Area */}
        {loading ? (
          <div className="flex-1 flex flex-col items-center justify-center py-20 gap-3">
            <Loader2 className="w-9 h-9 text-purple-500 animate-spin" />
            <p className="text-slate-400 dark:text-white/35 text-xs">서재에서 책을 꺼내오고 있습니다...</p>
          </div>
        ) : error ? (
          <div className="glass rounded-xl p-8 text-center text-red-500 dark:text-red-400">
            {error}
          </div>
        ) : books.length === 0 ? (
          <div className="glass rounded-2xl p-16 text-center flex-1 flex flex-col items-center justify-center">
            <BookOpen className="w-12 h-12 text-slate-300 dark:text-white/10 mb-4" />
            <p className="text-slate-600 dark:text-white/80 font-medium text-sm">해당 조건에 부합하는 도서가 없습니다.</p>
            <p className="text-slate-400 dark:text-white/30 text-xs mt-1">검색어나 필터를 변경해 다른 명작들을 탐색해 보세요.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-6 md:gap-8 mb-12">
            {books.map((book) => (
              <div
                key={book.id}
                onClick={() => navigate(`/books/${book.id}`)}
                className="glass rounded-2xl p-5 hover:scale-[1.02] active:scale-[0.99] transition-all duration-300 shadow-xl shadow-purple-950/2 dark:shadow-purple-950/10 cursor-pointer flex flex-col relative group"
              >
                {/* Book cover area */}
                <div className="aspect-[4/5] rounded-xl overflow-hidden mb-5 relative bg-slate-900/40 border border-black/5 dark:border-white/5">
                  <DkCover book={{ id: book.id, title: book.title, author: book.author }} className="w-full h-full object-cover transform group-hover:scale-105 transition duration-500" />
                </div>

                {/* Title & Author */}
                <div className="flex-1">
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] text-purple-500 dark:text-purple-400 font-mono tracking-wider font-semibold uppercase">{book.genre}</span>
                    {book.isBookmarked && (
                      <span className="text-[11px] text-rose-500 dark:text-rose-400 font-semibold flex items-center gap-1 font-sans">
                        <span className="animate-pulse">❤️</span> 찜함
                      </span>
                    )}
                  </div>
                  <h3 className="font-serif text-[17px] font-medium text-slate-800 dark:text-white mt-1 group-hover:text-purple-600 dark:group-hover:text-purple-400 transition">
                    {book.title}
                  </h3>
                  <p className="text-[12px] text-slate-500 dark:text-white/40 font-light mt-0.5">{book.author}</p>
                </div>

                {/* Badges footer */}
                <div className="flex flex-wrap items-center gap-1.5 border-t border-black/5 dark:border-white/5 pt-4 mt-4 shrink-0">
                  {book.hasSummary && <DkBadge kind="요약" size="xs" />}
                  {book.hasIllust && <DkBadge kind="일러스트" size="xs" />}
                  {book.hasQuiz && <DkBadge kind="퀴즈" size="xs" />}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default BookHome;
