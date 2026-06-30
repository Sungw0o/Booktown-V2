import React, { useState, useEffect } from 'react';
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
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    const initAuth = async () => {
      if (isMockMode) {
        if (import.meta.env.DEV) {
          const savedToken = localStorage.getItem('bt_mock_token');
          const savedUser = localStorage.getItem('bt_mock_user');
          if (savedToken && savedUser) {
            try {
              const parsedUser = JSON.parse(savedUser);
              setAccessTokenState(savedToken);
              setAccessToken(savedToken);
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
        const { accessToken: token } = response.data.data;
        setAccessTokenState(token);
        setAccessToken(token);

        const userResponse = await client.get('/users/me');
        const { nickname, email, role } = userResponse.data.data;
        setUser({ nickname, email, role: role || 'USER' });
      } catch (error) {
        console.warn('Silent refresh failed or unauthorized:', error);
        setAccessTokenState(null);
        setAccessToken(null);
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    };

    initAuth();
  }, [isMockMode]);

  const toggleMockMode = () => {
    if (!import.meta.env.DEV) return;
    const nextMode = !isMockMode;
    setIsMockMode(nextMode);
    localStorage.setItem('bt_mock_mode', String(nextMode));
    setAccessTokenState(null);
    setAccessToken(null);
    setUser(null);
    localStorage.removeItem('bt_mock_token');
    localStorage.removeItem('bt_mock_user');
  };

  const login = async (email: string, password: string) => {
    if (isMockMode) {
      await new Promise((resolve) => setTimeout(resolve, 800));
      if (!email || !password) {
        throw new Error('이메일과 비밀번호를 입력해주세요.');
      }
      const mockToken = 'mock_jwt_access_token_' + Math.random().toString(36).substring(7);
      const mockRole = email.includes('admin') ? 'ADMIN' : 'USER';
      const mockUser = { nickname: mockRole === 'ADMIN' ? '관리자' : '민지', email, role: mockRole };
      
      setAccessTokenState(mockToken);
      setAccessToken(mockToken);
      setUser(mockUser);

      if (import.meta.env.DEV) {
        localStorage.setItem('bt_mock_token', mockToken);
        localStorage.setItem('bt_mock_user', JSON.stringify(mockUser));
      }
      return;
    }

    try {
      const response = await client.post('/auth/login', { email, password });
      const { accessToken: token } = response.data.data;
      setAccessTokenState(token);
      setAccessToken(token);

      const userResponse = await client.get('/users/me');
      const { nickname, email: profileEmail, role } = userResponse.data.data;
      const realUser = { nickname, email: profileEmail, role: role || 'USER' };
      setUser(realUser);
    } catch (error: unknown) {
      const errorMsg = extractErrorMessage(error, '로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.');
      throw new Error(errorMsg, { cause: error });
    }
  };

  const signup = async (nickname: string, email: string, password: string) => {
    if (isMockMode) {
      await new Promise((resolve) => setTimeout(resolve, 800));
      if (!nickname || !email || !password) {
        throw new Error('모든 필드를 입력해 주세요.');
      }
      const mockToken = 'mock_jwt_access_token_' + Math.random().toString(36).substring(7);
      const mockUser = { nickname, email, role: 'USER' };

      setAccessTokenState(mockToken);
      setAccessToken(mockToken);
      setUser(mockUser);

      if (import.meta.env.DEV) {
        localStorage.setItem('bt_mock_token', mockToken);
        localStorage.setItem('bt_mock_user', JSON.stringify(mockUser));
      }
      return;
    }

    try {
      const response = await client.post('/auth/signup', { nickname, email, password });
      const { accessToken: token } = response.data.data;
      setAccessTokenState(token);
      setAccessToken(token);

      const userResponse = await client.get('/users/me');
      const { nickname: profileNickname, email: profileEmail, role } = userResponse.data.data;
      setUser({ nickname: profileNickname, email: profileEmail, role: role || 'USER' });
    } catch (error: unknown) {
      const errorMsg = extractErrorMessage(error, '회원가입에 실패했습니다.');
      throw new Error(errorMsg, { cause: error });
    }
  };

  const logout = async () => {
    if (isMockMode) {
      setAccessTokenState(null);
      setAccessToken(null);
      setUser(null);
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
      setAccessTokenState(null);
      setAccessToken(null);
      setUser(null);
    }
  };

  const isAuthenticated = !!accessTokenState;

  return (
    <AuthContext.Provider
      value={{
        accessToken: accessTokenState,
        isAuthenticated,
        user,
        isMockMode,
        isLoading,
        toggleMockMode,
        login,
        signup,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
