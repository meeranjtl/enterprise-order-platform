const REFRESH_TOKEN_KEY = 'eop_refresh_token'

// The access token lives in memory only (not localStorage) to limit XSS
// blast radius — see PHASE_13_GETTING_STARTED.md §3.4. It's naturally lost
// on a full page reload; AuthProvider re-derives it from the refresh token.
let accessToken: string | null = null

export const tokenStore = {
  getAccessToken(): string | null {
    return accessToken
  },
  setAccessToken(token: string | null): void {
    accessToken = token
  },
  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
  },
  setRefreshToken(token: string | null): void {
    if (token) {
      localStorage.setItem(REFRESH_TOKEN_KEY, token)
    } else {
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
  },
  clear(): void {
    accessToken = null
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  },
}
