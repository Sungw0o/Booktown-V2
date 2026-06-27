import { createContext } from 'react';

export interface User {
  nickname: string;
  email: string;
  role: string;
}

export interface AuthContextType {
  accessToken: string | null;
  isAuthenticated: boolean;
  user: User | null;
  isMockMode: boolean;
  isLoading: boolean;
  toggleMockMode: () => void;
  login: (email: string, password: string) => Promise<void>;
  signup: (nickname: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);
