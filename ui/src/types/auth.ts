export type Role = 'CUSTOMER' | 'ADMIN'

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName?: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export interface AuthUser {
  id: string
  email: string
  roles: Role[]
}
