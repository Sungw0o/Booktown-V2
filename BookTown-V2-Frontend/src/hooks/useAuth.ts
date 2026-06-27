import { useContext } from 'react';
import { AuthContext } from '../context/AuthContextObject';
import type { AuthContextType } from '../context/AuthContextObject';

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default useAuth;
