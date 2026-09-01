import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { decodeJwt } from '@/lib/jwt'
import { onSessionExpired } from '@/lib/sessionEvents'
import { tokenStore } from '@/lib/tokenStore'
import * as authApi from '@/services/authApi'
import type { AuthUser, LoginRequest, RegisterRequest, Role } from '@/types/auth'

interface AuthContextValue {
  user: AuthUser | null
  isAuthenticated: boolean
  /** True only while the app is attempting a silent refresh on initial load. */
  isLoading: boolean
  /** True once, right after the session died server-side (vs. a normal logout) — LoginPage uses it to explain why the user landed there. */
  sessionExpired: boolean
  acknowledgeSessionExpired: () => void
  login: (payload: LoginRequest) => Promise<void>
  register: (payload: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
  hasRole: (role: Role) => boolean
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)

function userFromAccessToken(accessToken: string): AuthUser {
  const payload = decodeJwt(accessToken)
  return {
    id: payload.sub,
    email: payload.email ?? '',
    roles: (payload.roles ?? []) as Role[],
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [sessionExpired, setSessionExpired] = useState(false)

  useEffect(() => {
    return onSessionExpired(() => {
      tokenStore.clear()
      setUser(null)
      setSessionExpired(true)
    })
  }, [])

  const acknowledgeSessionExpired = useCallback(() => setSessionExpired(false), [])

  useEffect(() => {
    const storedRefreshToken = tokenStore.getRefreshToken()
    if (!storedRefreshToken) {
      setIsLoading(false)
      return
    }

    authApi
      .refresh(storedRefreshToken)
      .then((tokens) => {
        tokenStore.setAccessToken(tokens.accessToken)
        tokenStore.setRefreshToken(tokens.refreshToken)
        setUser(userFromAccessToken(tokens.accessToken))
      })
      .catch(() => {
        tokenStore.clear()
      })
      .finally(() => setIsLoading(false))
  }, [])

  const login = useCallback(async (payload: LoginRequest) => {
    const tokens = await authApi.login(payload)
    tokenStore.setAccessToken(tokens.accessToken)
    tokenStore.setRefreshToken(tokens.refreshToken)
    setUser(userFromAccessToken(tokens.accessToken))
    setSessionExpired(false)
  }, [])

  const register = useCallback(async (payload: RegisterRequest) => {
    const tokens = await authApi.register(payload)
    tokenStore.setAccessToken(tokens.accessToken)
    tokenStore.setRefreshToken(tokens.refreshToken)
    setUser(userFromAccessToken(tokens.accessToken))
  }, [])

  const logout = useCallback(async () => {
    const currentRefreshToken = tokenStore.getRefreshToken()
    tokenStore.clear()
    setUser(null)
    if (currentRefreshToken) {
      // Best-effort — the session is already gone client-side either way.
      await authApi.logout(currentRefreshToken).catch(() => undefined)
    }
  }, [])

  const hasRole = useCallback((role: Role) => user?.roles.includes(role) ?? false, [user])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isLoading,
      sessionExpired,
      acknowledgeSessionExpired,
      login,
      register,
      logout,
      hasRole,
    }),
    [user, isLoading, sessionExpired, acknowledgeSessionExpired, login, register, logout, hasRole],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
