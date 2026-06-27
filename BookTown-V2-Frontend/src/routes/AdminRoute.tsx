import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

export const AdminRoute: React.FC = () => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen dk-surface flex flex-col items-center justify-center relative selection:bg-purple-500 selection:text-white">
        <div className="dk-grain absolute inset-0 opacity-40" />
        <div className="relative z-10 text-center">
          <div className="w-10 h-10 rounded-full border-2 border-slate-300 dark:border-white/5 border-t-slate-800 dark:border-t-white/30 animate-spin mx-auto mb-4" />
          <div className="text-slate-500 dark:text-white/40 text-[10px] tracking-wider">권한을 확인하는 중입니다...</div>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // 임시 권한 해제: 일반 유저도 관리자 대시보드에 접근할 수 있도록 주석 처리
  /*
  if (user?.role !== 'ADMIN') {
    return <Navigate to="/403" replace />;
  }
  */

  return <Outlet />;
};

export default AdminRoute;
