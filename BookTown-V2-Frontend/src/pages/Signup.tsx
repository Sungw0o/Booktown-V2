import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { DArrow, DkCover, DkMark, ThemeToggle } from '../components/Primitives';

// mock book data from unpack
const EX_BOOKS = [
  { id: 'pride', title: '오만과 편견', author: '제인 오스틴' },
  { id: 'gatsby', title: '위대한 개츠비', author: 'F. 스콧 피츠제럴드' },
];

export const Signup: React.FC = () => {
  const navigate = useNavigate();
  const { signup, login, isMockMode, toggleMockMode } = useAuth();
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSignupSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!nickname || !email || !password) {
      setErrorMsg('모든 필드를 기입해주세요.');
      return;
    }
    setErrorMsg(null);
    setSuccessMsg(null);
    setLoading(true);
    try {
      await signup(nickname, email, password);
      setSuccessMsg('회원가입이 완료되었습니다! 잠시 후 로그인 페이지로 이동합니다.');
      timerRef.current = setTimeout(() => {
        navigate('/login');
      }, 2000);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : '회원가입에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleSocialLogin = (provider: 'google' | 'kakao' | 'naver') => {
    if (isMockMode) {
      login(`${provider}@example.com`, 'social_password')
        .then(() => navigate('/'))
        .catch((err) => setErrorMsg(err instanceof Error ? err.message : '소셜 로그인에 실패했습니다.'));
    } else {
      const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'https://api.booktown.shop';
      window.location.href = `${apiBaseUrl}/api/v1/auth/oauth2/${provider}/login`;
    }
  };

  const formElement = (compact: boolean) => {
    const f = compact
      ? { h: 'text-[26px]', gap: 'gap-3', pad: 'py-2.5', label: 'text-[11px]' }
      : { h: 'text-[34px]', gap: 'gap-3.5', pad: 'py-3', label: 'text-[12px]' };

    return (
      <form onSubmit={handleSignupSubmit} className="w-full">
        {/* seg toggle */}
        <div className="glass-soft rounded-full p-1 flex mb-6">
          <button
            type="button"
            onClick={() => navigate('/login')}
            className="flex-1 py-2 rounded-full text-[12px] text-slate-500 dark:text-white/50 hover:text-slate-800 dark:hover:text-white transition"
          >
            로그인
          </button>
          <button
            type="button"
            className="flex-1 py-2 rounded-full text-[12px] bg-white dark:bg-white/10 text-[#0B0E14] dark:text-white font-medium shadow-sm transition"
          >
            회원가입
          </button>
        </div>

        <h2 className={`font-display ${f.h} leading-[1.05] text-slate-900 dark:text-white`}>
          책고을에 <em className="text-ac2">처음</em>이신가요
        </h2>
        <p className="text-[11px] text-slate-500 dark:text-white/45 font-mono uppercase tracking-[0.14em] mt-1.5">
          이메일로 30초 만에 시작
        </p>

        <div className={`mt-6 flex flex-col ${f.gap}`}>
          <label className="block">
            <span className={`${f.label} text-slate-500 dark:text-white/55`}>닉네임</span>
            <div className="mt-1.5 relative">
              <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 dark:text-white/35">
                <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                  <circle cx="12" cy="7" r="4" />
                </svg>
              </span>
              <input
                type="text"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                className={`w-full glass-soft rounded-xl pl-10 pr-3.5 ${f.pad} text-[14px] text-slate-800 dark:text-white/90 placeholder:text-slate-400 dark:placeholder:text-white/30 focus:outline-none focus:border-black/10 dark:focus:border-white/25`}
                placeholder="책고을 친구"
                required
              />
            </div>
          </label>

          <label className="block">
            <span className={`${f.label} text-slate-500 dark:text-white/55`}>이메일</span>
            <div className="mt-1.5 relative">
              <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 dark:text-white/35">
                <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="5" width="18" height="14" rx="2.5" />
                  <path d="m4 7 8 6 8-6" />
                </svg>
              </span>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={`w-full glass-soft rounded-xl pl-10 pr-3.5 ${f.pad} text-[14px] text-slate-800 dark:text-white/90 placeholder:text-slate-400 dark:placeholder:text-white/30 focus:outline-none focus:border-black/10 dark:focus:border-white/25`}
                placeholder="you@example.com"
                required
              />
            </div>
          </label>

          <label className="block">
            <span className={`${f.label} text-slate-500 dark:text-white/55`}>비밀번호</span>
            <div className="mt-1.5 relative">
              <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 dark:text-white/35">
                <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="4" y="10" width="16" height="10" rx="2.5" />
                  <path d="M8 10V7a4 4 0 0 1 8 0v3" />
                </svg>
              </span>
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={`w-full glass-soft rounded-xl pl-10 pr-10 ${f.pad} text-[14px] text-slate-800 dark:text-white/90 placeholder:text-slate-400 dark:placeholder:text-white/30 focus:outline-none focus:border-black/10 dark:focus:border-white/25`}
                placeholder="8자 이상, 영문+숫자"
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className={`absolute right-3 top-1/2 -translate-y-1/2 ${showPassword ? 'text-ac2' : 'text-slate-400 dark:text-white/35'} hover:text-slate-600 dark:hover:text-white/70`}
              >
                <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7Z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
              </button>
            </div>
            <div className="flex justify-between items-center mt-1.5">
              {errorMsg && <span className="text-[11px] text-red-400 font-medium">{errorMsg}</span>}
              {successMsg && <span className="text-[11px] text-green-400 font-medium">{successMsg}</span>}
              {!errorMsg && !successMsg && (
                <span className="text-[11px] text-slate-500 dark:text-white/35 font-light">영문·숫자 조합을 권장합니다</span>
              )}
            </div>
          </label>
        </div>

        <button
          type="submit"
          disabled={loading}
          className="w-full mt-5 py-3 rounded-full text-[13px] text-white font-medium glass-btn disabled:opacity-50"
          style={{
            background: 'linear-gradient(135deg,#7AA3D6,#3E6FA9)',
            boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.25), 0 4px 16px rgba(122,163,214,0.4)',
          }}
        >
          {loading ? '만드는 중...' : '계정 만들기'}{' '}
          <DArrow className="w-4 h-4 inline-block ml-1.5 align-[-2px]" />
        </button>

        <div className="flex items-center gap-3 my-5 text-[10px] text-slate-400 dark:text-white/30 font-mono uppercase tracking-wider">
          <div className="h-px flex-1 bg-black/10 dark:bg-white/10" />
          또는
          <div className="h-px flex-1 bg-black/10 dark:bg-white/10" />
        </div>
        <div className="grid grid-cols-3 gap-2">
          <button
            type="button"
            onClick={() => handleSocialLogin('google')}
            className="glass-soft rounded-full py-2.5 text-[12px] text-slate-700 dark:text-white/70 hover:bg-black/[0.04] dark:hover:bg-white/[0.06] transition flex items-center justify-center gap-1.5"
          >
            <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
              <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
              <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" fill="#FBBC05"/>
              <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" fill="#EA4335"/>
            </svg>
            Google
          </button>
          <button
            type="button"
            onClick={() => handleSocialLogin('kakao')}
            className="glass-soft rounded-full py-2.5 text-[12px] text-slate-700 dark:text-white/70 hover:bg-black/[0.04] dark:hover:bg-white/[0.06] transition flex items-center justify-center gap-1.5"
          >
            <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 3c-4.97 0-9 3.185-9 7.115 0 2.557 1.707 4.8 4.27 6.054-.3 1.1-.96 3.535-1.03 3.79-.05.18.06.18.1.15.3-.2 4.15-2.795 4.79-3.22.29.04.58.06.87.06 4.97 0 9-3.186 9-7.115C21 6.185 16.97 3 12 3z" fill="#3C1E1E"/>
            </svg>
            Kakao
          </button>
          <button
            type="button"
            onClick={() => handleSocialLogin('naver')}
            className="glass-soft rounded-full py-2.5 text-[12px] text-slate-700 dark:text-white/70 hover:bg-black/[0.04] dark:hover:bg-white/[0.06] transition flex items-center justify-center gap-1.5"
          >
            <svg className="w-3 h-3" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
              <path d="M16.2 3H22v18h-5.8l-8.4-12v12H2V3h5.8l8.4 12V3z" fill="#03C75A"/>
            </svg>
            Naver
          </button>
        </div>

        {/* Mock Mode Control Button (Development Only) */}
        {import.meta.env.DEV && (
          <div className="mt-8 flex justify-center">
            <button
              type="button"
              onClick={toggleMockMode}
              className={`px-3 py-1.5 rounded-full text-[10px] font-mono border transition ${
                isMockMode
                  ? 'bg-ac2/10 text-ac2 border-ac2/30 shadow-[0_0_12px_rgba(232,184,111,0.2)]'
                  : 'bg-black/5 dark:bg-white/5 text-slate-500 dark:text-white/40 border-black/10 dark:border-white/10'
              }`}
            >
              Mocking API: {isMockMode ? 'ON' : 'OFF'}
            </button>
          </div>
        )}
      </form>
    );
  };

  return (
    <div className="relative w-full min-h-screen dk-surface">
      <div className="dk-grain" />
      <div className="absolute top-4 right-4 z-50">
        <ThemeToggle />
      </div>

      {/* 1. Mobile View (md 미만) */}
      <div className="block md:hidden h-full flex flex-col">
        {/* cinematic header band */}
        <div className="relative h-[150px] shrink-0 overflow-hidden bg-slate-950">
          <div
            className="absolute inset-0"
            style={{
              background:
                'radial-gradient(80% 90% at 30% 0%, rgba(122,163,214,0.30), transparent 60%), radial-gradient(60% 80% at 90% 30%, rgba(232,184,111,0.16), transparent 60%)',
            }}
          />
          <div className="absolute right-3 top-8 w-[72px] h-[104px] rotate-[8deg] opacity-90">
            <DkCover book={EX_BOOKS[0]} />
          </div>
          <div className="absolute right-[58px] top-12 w-[64px] h-[92px] -rotate-[10deg] opacity-60">
            <DkCover book={EX_BOOKS[1]} />
          </div>
          <div className="relative px-6 pt-6">
            <DkMark size={24} />
            <div className="font-display text-[20px] mt-5 leading-[1.1] max-w-[180px] text-white">
              고전의 첫 장을<br />
              <em className="text-ac2">다시</em> 펼치는 곳
            </div>
          </div>
        </div>

        {/* Form area */}
        <div className="flex-1 overflow-y-auto hide-scroll px-6 pt-1 pb-8">
          {formElement(true)}
        </div>
      </div>

      {/* 2. Desktop View (md 이상) */}
      <div className="hidden md:grid h-screen grid-cols-[1.05fr_0.95fr]">
        {/* cinematic aside */}
        <div className="relative overflow-hidden border-r border-black/5 dark:border-white/8 p-12 flex flex-col bg-slate-950">
          <div
            className="absolute inset-0"
            style={{
              background:
                'radial-gradient(70% 60% at 15% 10%, rgba(122,163,214,0.22), transparent 60%), radial-gradient(60% 60% at 100% 100%, rgba(232,184,111,0.12), transparent 60%)',
            }}
          />
          <div className="relative z-10">
            <DkMark size={26} />
            <div className="font-mono text-[10px] uppercase tracking-[0.2em] text-white/45 mt-10">
              A reading companion for the classics
            </div>
            <h1 className="font-display text-[52px] leading-[1.05] mt-4 max-w-[14ch] text-white">
              고전의 첫 장을<br />
              <em className="text-ac2">다시</em> 펼치는 곳.
            </h1>
            <p className="text-[13px] text-white/55 mt-5 max-w-[34ch] leading-[1.7] font-light">
              AI 요약 · 장면 일러스트 · 이해도 퀴즈가 원문 문맥을 근거로 함께합니다.
            </p>
          </div>

          {/* floating covers */}
          <div className="absolute right-[-30px] bottom-6 flex gap-4 rotate-[-8deg] opacity-90 pointer-events-none">
            <div className="w-[120px] h-[172px]">
              <DkCover book={EX_BOOKS[0]} />
            </div>
            <div className="w-[120px] h-[172px]">
              <DkCover book={EX_BOOKS[1]} />
            </div>
          </div>
        </div>

        {/* Form area */}
        <div className="grid place-items-center p-10 overflow-y-auto">
          <div className="w-full max-w-[360px]">{formElement(false)}</div>
        </div>
      </div>
    </div>
  );
};
export default Signup;
