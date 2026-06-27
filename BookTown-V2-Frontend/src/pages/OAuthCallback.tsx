import React, { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

export const OAuthSuccess: React.FC = () => {
  useEffect(() => {
    // 백엔드가 심어준 쿠키(refreshToken)를 바탕으로 세션을 갱신하기 위해
    // 페이지 전체를 새로고침하며 /로 이동합니다.
    const timer = setTimeout(() => {
      window.location.replace('/');
    }, 1000);
    return () => clearTimeout(timer);
  }, []);

  return (
    <div className="min-h-screen dk-surface flex flex-col items-center justify-center relative selection:bg-purple-500 selection:text-white">
      <div className="dk-grain absolute inset-0 opacity-40" />
      <div className="relative z-10 text-center">
        <div className="w-12 h-12 rounded-full border-2 border-purple-500/20 border-t-purple-500 animate-spin mx-auto mb-6" />
        <h2 className="text-xl font-medium text-slate-800 dark:text-white mb-2">소셜 로그인 성공</h2>
        <p className="text-slate-500 dark:text-white/40 text-sm">잠시 후 안전하게 메인 화면으로 이동합니다...</p>
      </div>
    </div>
  );
};

export const OAuthFailure: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const reason = searchParams.get('reason') || 'unknown';

  useEffect(() => {
    const timer = setTimeout(() => {
      navigate('/login', { replace: true });
    }, 3500);
    return () => clearTimeout(timer);
  }, [navigate]);

  const getErrorMessage = (errReason: string) => {
    switch (errReason) {
      case 'user_not_found':
        return '등록되지 않은 사용자입니다. 회원가입을 먼저 진행해 주세요.';
      case 'unauthorized':
        return '인증에 실패했습니다. 올바른 계정으로 시도해 주세요.';
      default:
        return '소셜 로그인 중 오류가 발생했습니다. 다시 시도해 주세요.';
    }
  };

  return (
    <div className="min-h-screen dk-surface flex flex-col items-center justify-center relative selection:bg-purple-500 selection:text-white">
      <div className="dk-grain absolute inset-0 opacity-40" />
      <div className="relative z-10 text-center max-w-md px-6">
        <div className="w-12 h-12 rounded-full bg-red-500/10 flex items-center justify-center mx-auto mb-6 text-red-500 text-xl font-bold">
          !
        </div>
        <h2 className="text-xl font-medium text-slate-800 dark:text-white mb-2">소셜 로그인 실패</h2>
        <p className="text-red-500 dark:text-red-400 text-sm mb-4">{getErrorMessage(reason)}</p>
        <p className="text-slate-500 dark:text-white/40 text-xs">잠시 후 로그인 화면으로 돌아갑니다...</p>
      </div>
    </div>
  );
};
