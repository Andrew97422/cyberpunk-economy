import { createContext, useContext, useState, ReactNode } from 'react';
import client, { TOKEN_KEY, USER_KEY } from '../api/client';
import { ENDPOINTS } from '../api/config';
import type { AuthResponse, AuthUser } from '../types';

interface AuthContextType {
  user: AuthUser | null;
  isAuthenticated: boolean;
  loginAdmin: (publicName: string, password: string) => Promise<AuthUser>;
  loginPlayer: (pin: string) => Promise<AuthUser>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | null>(null);

function readStoredUser(): AuthUser | null {
  const token = localStorage.getItem(TOKEN_KEY);
  const raw = localStorage.getItem(USER_KEY);
  if (!token || !raw) return null;
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

function persist(res: AuthResponse): AuthUser {
  const user: AuthUser = {
    accountId: res.accountId,
    publicName: res.publicName,
    role: res.role,
    sessionId: res.sessionId,
  };
  localStorage.setItem(TOKEN_KEY, res.token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  return user;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => readStoredUser());

  const loginAdmin = async (publicName: string, password: string) => {
    const res = await client.post<AuthResponse>(ENDPOINTS.adminLogin, { publicName, password });
    const u = persist(res.data);
    setUser(u);
    return u;
  };

  const loginPlayer = async (pin: string) => {
    const res = await client.post<AuthResponse>(ENDPOINTS.playerLogin, { pin });
    const u = persist(res.data);
    setUser(u);
    return u;
  };

  const logout = async () => {
    try {
      await client.post(ENDPOINTS.logout);
    } catch {
      /* ignore — token may already be invalid */
    }
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, loginAdmin, loginPlayer, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
