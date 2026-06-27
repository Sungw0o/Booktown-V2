import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import { ThemeProvider } from '../context/ThemeContext';
import { useAuth } from '../hooks/useAuth';
import ProtectedRoute from './ProtectedRoute';
import PublicRoute from './PublicRoute';
import AdminRoute from './AdminRoute';
import AdminDashboard from '../pages/AdminDashboard';
import Login from '../pages/Login';
import Signup from '../pages/Signup';
import BookHome from '../pages/BookHome';
import BookDetail from '../pages/BookDetail';
import { OAuthSuccess, OAuthFailure } from '../pages/OAuthCallback';
import { NotFoundPage, ForbiddenPage } from '../pages/ErrorPages';
import { QuizPage, QuizResultPage } from '../pages/QuizPages';
import { CustomCursor } from '../components/Primitives';


const AppRouterBody: React.FC = () => {
  const { isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen dk-surface flex flex-col items-center justify-center relative selection:bg-purple-500 selection:text-white">
        <div className="dk-grain absolute inset-0 opacity-40" />
        <div className="relative z-10 text-center">
          <div className="w-10 h-10 rounded-full border-2 border-slate-300 dark:border-white/5 border-t-slate-800 dark:border-t-white/30 animate-spin mx-auto mb-4" />
          <div className="text-slate-500 dark:text-white/40 text-[10px] tracking-wider">잠시만 기다려 주세요...</div>
        </div>
      </div>
    );
  }

  return (
    <BrowserRouter>
      <Routes>
        {/* Public Routes */}
        <Route element={<PublicRoute />}>
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
        </Route>

        {/* OAuth Callback Routes */}
        <Route path="/oauth/success" element={<OAuthSuccess />} />
        <Route path="/oauth/failure" element={<OAuthFailure />} />

        {/* Protected Routes */}
        <Route element={<ProtectedRoute />}>
          <Route path="/health" element={<Navigate to="/admin" replace />} />
          <Route path="/books/:bookId" element={<BookDetail />} />
          <Route path="/books/:bookId/quiz" element={<QuizPage />} />
          <Route path="/books/:bookId/quiz/result" element={<QuizResultPage />} />
          <Route path="/" element={<BookHome />} />
        </Route>

        {/* Admin Routes */}
        <Route element={<AdminRoute />}>
          <Route path="/admin" element={<AdminDashboard />} />
        </Route>

        {/* Error Fallback Routes */}
        <Route path="/404" element={<NotFoundPage />} />
        <Route path="/403" element={<ForbiddenPage />} />
        <Route path="*" element={<Navigate to="/404" replace />} />
      </Routes>
    </BrowserRouter>
  );
};

const AppRoutes: React.FC = () => {
  return (
    <AuthProvider>
      <ThemeProvider>
        <CustomCursor />
        <AppRouterBody />
      </ThemeProvider>
    </AuthProvider>
  );
};

export default AppRoutes;
