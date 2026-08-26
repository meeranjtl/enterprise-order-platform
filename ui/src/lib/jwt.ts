export interface JwtPayload {
  sub: string
  roles?: string[]
  email?: string
  type?: 'access' | 'refresh'
  exp: number
  iat?: number
}

/**
 * Decodes a JWT payload for UI purposes only (role-gating, expiry display).
 * Never treat this as verified — the server is the actual authority.
 */
export function decodeJwt(token: string): JwtPayload {
  const [, payload] = token.split('.')
  if (!payload) {
    throw new Error('Malformed JWT: missing payload segment')
  }
  const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
  const json = atob(normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '='))
  return JSON.parse(json) as JwtPayload
}

export function isExpired(payload: JwtPayload): boolean {
  return Date.now() >= payload.exp * 1000
}
