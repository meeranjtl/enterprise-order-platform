import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

import { emitSessionExpired } from '@/lib/sessionEvents'
import { tokenStore } from '@/lib/tokenStore'
import * as authApi from '@/services/authApi'
import type { BaseResponse } from '@/types/api'

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

// Every resource call goes through the gateway at :8080 — never a service
// port directly (see docs/architecture.md's topology). Note the gateway
// does NOT put a uniform /api/v1 prefix on everything: /api/auth/** has no
// /v1 segment (see gateway application.yml), while every other route is
// /api/v1/{resource}/**. Callers in services/*.ts must include their own
// full path.
export const apiClient = axios.create({
  baseURL: API_URL,
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.request.use((config) => {
  const token = tokenStore.getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

type RetryableRequestConfig = InternalAxiosRequestConfig & { _retry?: boolean }

// Single in-flight refresh shared by every caller that hits a 401 at the
// same time — the backend has a single-active-session refresh model
// (Phase 12), so two concurrent refresh calls would have the second one
// invalidate the token pair the first one just issued.
let refreshPromise: Promise<string | null> | null = null

function refreshAccessToken(): Promise<string | null> {
  const currentRefreshToken = tokenStore.getRefreshToken()
  if (!currentRefreshToken) {
    return Promise.resolve(null)
  }

  if (!refreshPromise) {
    refreshPromise = authApi
      .refresh(currentRefreshToken)
      .then((tokens) => {
        tokenStore.setAccessToken(tokens.accessToken)
        tokenStore.setRefreshToken(tokens.refreshToken)
        return tokens.accessToken
      })
      .catch(() => {
        tokenStore.clear()
        return null
      })
      .finally(() => {
        refreshPromise = null
      })
  }

  return refreshPromise
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<BaseResponse<unknown>>) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined
    const isAuthEndpoint = originalRequest?.url?.includes('/api/auth/')

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry && !isAuthEndpoint) {
      originalRequest._retry = true
      const newAccessToken = await refreshAccessToken()

      if (newAccessToken) {
        originalRequest.headers.set('Authorization', `Bearer ${newAccessToken}`)
        return apiClient(originalRequest)
      }

      // Refresh failed (expired/revoked) — no session to salvage. Let
      // AuthContext clear itself so RequireAuth's <Navigate> redirects
      // cleanly, instead of a hard reload racing this request's own
      // error state onto the screen first.
      emitSessionExpired()
    }

    return Promise.reject(error)
  },
)
