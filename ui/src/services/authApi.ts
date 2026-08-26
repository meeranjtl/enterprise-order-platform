import axios from 'axios'

import { unwrap } from '@/lib/unwrap'
import type { BaseResponse } from '@/types/api'
import type { LoginRequest, RefreshRequest, RegisterRequest, TokenResponse } from '@/types/auth'

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

// Deliberately a bare axios instance, not `apiClient` — the 401 interceptor
// on `apiClient` calls `refresh()` from this module, so routing auth calls
// back through `apiClient` would create a refresh-triggers-refresh loop.
const authClient = axios.create({
  baseURL: API_URL,
  headers: { 'Content-Type': 'application/json' },
})

export async function login(payload: LoginRequest): Promise<TokenResponse> {
  const { data } = await authClient.post<BaseResponse<TokenResponse>>('/api/auth/login', payload)
  return unwrap(data)
}

export async function register(payload: RegisterRequest): Promise<TokenResponse> {
  const { data } = await authClient.post<BaseResponse<TokenResponse>>('/api/auth/register', payload)
  return unwrap(data)
}

export async function refresh(refreshToken: string): Promise<TokenResponse> {
  const body: RefreshRequest = { refreshToken }
  const { data } = await authClient.post<BaseResponse<TokenResponse>>('/api/auth/refresh', body)
  return unwrap(data)
}

export async function logout(refreshToken: string): Promise<void> {
  const body: RefreshRequest = { refreshToken }
  await authClient.post<BaseResponse<void>>('/api/auth/logout', body)
}
