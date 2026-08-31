// Lets the axios interceptor (a plain module, no React context) tell
// AuthContext the session died server-side (refresh token expired/revoked)
// so it can clear auth state and let RequireAuth's existing <Navigate>
// guard redirect — instead of a hard window.location reload racing the
// page's own error UI.
const SESSION_EXPIRED_EVENT = 'eop:session-expired'

export function emitSessionExpired(): void {
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
}

export function onSessionExpired(handler: () => void): () => void {
  window.addEventListener(SESSION_EXPIRED_EVENT, handler)
  return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handler)
}
