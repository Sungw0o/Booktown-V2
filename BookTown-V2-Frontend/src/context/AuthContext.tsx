import React, { useState, useCallback, useEffect } from 'react';
import client, { setAccessToken } from '../api/client';
import { AuthContext } from './AuthContextObject';
import type { User } from './AuthContextObject';

interface AxiosErrorResponse {
  response?: {
    data?: {
      message?: string;
      error?: {
        message?: string;
      };
    };
  };
}

interface AuthTokenPayload {
  accessToken: string;
  accessTokenExpiresInMs: number;
}

type TokenReissuedEventDetail = AuthTokenPayload;

const MOCK_ACCESS_TOKEN_EXPIRES_IN_MS = 60 * 60 * 1000;

const extractErrorMessage = (error: unknown, fallback: string): string => {
  const axiosError = error as AxiosErrorResponse;
  return axiosError.response?.data?.error?.message
    ?? axiosError.response?.data?.message
    ?? fallback;
};

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [isMockMode, setIsMockMode] = useState<boolean>(() => {
    if (!import.meta.env.DEV) return false;
    const savedMockMode = localStorage.getItem('bt_mock_mode');
    return savedMockMode !== null ? savedMockMode === 'true' : true;
  });

  const [user, setUser] = useState<User | null>(null);
  const [accessTokenState, setAccessTokenState] = useState<string | null>(null);
  const [sessionExpiresAt, setSessionExpiresAt] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const applyToken = useCallback((token: string, expiresInMs: number) => {
    setAccessTokenState(token);
    setAccessToken(token);
    setSessionExpiresAt(Date.now() + expiresInMs);
  }, []);

  const clearAuthState = useCallback(() => {
    setAccessTokenState(null);
    setAccessToken(null);
    setSessionExpiresAt(null);
    setUser(null);
  }, []);

  useEffect(() => {
    const initAuth = async () => {
      if (isMockMode) {
        if (import.meta.env.DEV) {
          const savedToken = localStorage.getItem('bt_mock_token');
          const savedUser = localStorage.getItem('bt_mock_user');
          if (savedToken && savedUser) {
            try {
              const parsedUser = JSON.parse(savedUser);
              applyToken(savedToken, MOCK_ACCESS_TOKEN_EXPIRES_IN_MS);
              setUser(parsedUser);
            } catch (e) {
              console.error('Failed to parse saved mock user:', e);
              localStorage.removeItem('bt_mock_token');
              localStorage.removeItem('bt_mock_user');
            }
          }
        }
        setIsLoading(false);
        return;
      }

      try {
        const response = await client.post('/auth/reissue', {}, { withCredentials: true });
        const { accessToken: token, accessTokenExpiresInMs } = response.data.data as AuthTokenPayload;
        applyToken(token, accessTokenExpiresInMs);

        const userResponse = await client.get('/users/me');
        const { nickname, email, role } = userResponse.data.data;
        setUser({ nickname, email, role: role || 'USER' });
      } catch (error) {
        console.warn('Silent refresh failed or unauthorized:', error);
        clearAuthState();
      } finally {
        setIsLoading(false);
      }
    };

    initAuth();
  }, [applyToken, clearAuthState, isMockMode]);

  useEffect(() => {
    const handleTokenReissued = (event: Event) => {
      const { accessToken: token, accessTokenExpiresInMs } = (event as CustomEvent<TokenReissuedEventDetail>).detail;
      if (token && accessTokenExpiresInMs) {
        applyToken(token, accessTokenExpiresInMs);
      }
    };

    window.addEventListener('bt-token-reissued', handleTokenReissued);
    return () => window.removeEventListener('bt-token-reissued', handleTokenReissued);
  }, [applyToken]);

  const toggleMockMode = () => {
    if (!import.meta.env.DEV) return;
    const nextMode = !isMockMode;
    setIsMockMode(nextMode);
    localStorage.setItem('bt_mock_mode', String(nextMode));
    clearAuthState();
    localStorage.removeItem('bt_mock_token');
    localStorage.removeItem('bt_mock_user');
  };

  const login = async (email: string, password: string, turnstileToken?: string) => {
    if (isMockMode) {
      await new Promise((resolve) => setTimeout(resolve, 800));
      if (!email || !password) {
        throw new Error('이메일과 비밀번호를 입력해주세요.');
      }
      const mockToken = 'mock_jwt_access_token_' + Math.random().toString(36).substring(7);
      const mockRole = email.includes('admin') ? 'ADMIN' : 'USER';
      const mockUser = { nickname: mockRole === 'ADMIN' ? '관리자' : '민지', email, role: mockRole };
      
      applyToken(mockToken, MOCK_ACCESS_TOKEN_EXPIRES_IN_MS);
      setUser(mockUser);

      if (import.meta.env.DEV) {
        localStorage.setItem('bt_mock_token', mockToken);
        localStorage.setItem('bt_mock_user', JSON.stringify(mockUser));
      }
      return;
    }

    try {
      const response = await client.post('/auth/login', { email, password, turnstileToken });
      const { accessToken: token, accessTokenExpiresInMs } = response.data.data as AuthTokenPayload;
      applyToken(token, accessTokenExpiresInMs);

      const userResponse = await client.get('/users/me');
      const { nickname, email: profileEmail, role } = userResponse.data.data;
      const realUser = { nickname, email: profileEmail, role: role || 'USER' };
      setUser(realUser);
    } catch (error: unknown) {
      const errorMsg = extractErrorMessage(error, '로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.');
      throw new Error(errorMsg, { cause: error });
    }
  };

  const signup = async (nickname: string, email: string, password: string, turnstileToken?: string) => {
    if (isMockMode) {
      await new Promise((resolve) => setTimeout(resolve, 800));
      if (!nickname || !email || !password) {
        throw new Error('모든 필드를 입력해 주세요.');
      }
      const mockToken = 'mock_jwt_access_token_' + Math.random().toString(36).substring(7);
      const mockUser = { nickname, email, role: 'USER' };

      applyToken(mockToken, MOCK_ACCESS_TOKEN_EXPIRES_IN_MS);
      setUser(mockUser);

      if (import.meta.env.DEV) {
        localStorage.setItem('bt_mock_token', mockToken);
        localStorage.setItem('bt_mock_user', JSON.stringify(mockUser));
      }
      return;
    }

    try {
      const response = await client.post('/auth/signup', { nickname, email, password, turnstileToken });
      const { accessToken: token, accessTokenExpiresInMs } = response.data.data as AuthTokenPayload;
      applyToken(token, accessTokenExpiresInMs);

      const userResponse = await client.get('/users/me');
      const { nickname: profileNickname, email: profileEmail, role } = userResponse.data.data;
      setUser({ nickname: profileNickname, email: profileEmail, role: role || 'USER' });
    } catch (error: unknown) {
      const errorMsg = extractErrorMessage(error, '회원가입에 실패했습니다.');
      throw new Error(errorMsg, { cause: error });
    }
  };

  const extendSession = async () => {
    if (isMockMode) {
      const token = accessTokenState ?? 'mock_jwt_access_token_' + Math.random().toString(36).substring(7);
      applyToken(token, MOCK_ACCESS_TOKEN_EXPIRES_IN_MS);
      if (import.meta.env.DEV) {
        localStorage.setItem('bt_mock_token', token);
      }
      return;
    }

    const response = await client.post('/auth/reissue', {}, { withCredentials: true });
    const { accessToken: token, accessTokenExpiresInMs } = response.data.data as AuthTokenPayload;
    applyToken(token, accessTokenExpiresInMs);
  };

  const logout = async () => {
    if (isMockMode) {
      clearAuthState();
      if (import.meta.env.DEV) {
        localStorage.removeItem('bt_mock_token');
        localStorage.removeItem('bt_mock_user');
      }
      return;
    }

    try {
      await client.post('/auth/logout', {}, { withCredentials: true });
    } catch (error) {
      console.error('Server logout failed:', error);
    } finally {
      clearAuthState();
    }
  };

  const isAuthenticated = !!accessTokenState;

  return (
    <AuthContext.Provider
      value={{
        accessToken: accessTokenState,
        sessionExpiresAt,
        isAuthenticated,
        user,
        isMockMode,
        isLoading,
        toggleMockMode,
        login,
        signup,
        extendSession,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
