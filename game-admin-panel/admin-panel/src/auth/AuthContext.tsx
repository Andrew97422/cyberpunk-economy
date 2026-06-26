import { createContext, useContext, useState, ReactNode } from 'react'
import client from '../api/client'
import { ENDPOINTS } from '../api/config'
import type { AdminLoginRequest, AuthResponse, MeResponse } from '../types'

interface AuthUser {
  accountId: number
  publicName: string
  role: string
}

interface AuthContextType {
  user: AuthUser | null
  login: (req: AdminLoginRequest) => Promise<void>
  logout: () => Promise<void>
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthContextType | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => {
    const token = localStorage.getItem('token')
    const stored = localStorage.getItem('user')
    if (token && stored) {
      try { return JSON.parse(stored) } catch { return null }
    }
    return null
  })

  const login = async (req: AdminLoginRequest) => {
    const res = await client.post<AuthResponse>(ENDPOINTS.adminLogin, req)
    const { token, accountId, publicName, role } = res.data
    localStorage.setItem('token', token)
    const u = { accountId, publicName, role }
    localStorage.setItem('user', JSON.stringify(u))
    setUser(u)
  }

  const logout = async () => {
    try { await client.post(ENDPOINTS.logout) } catch { /* ignore */ }
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, login, logout, isAuthenticated: !!user }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside AuthProvider')
  return ctx
}
