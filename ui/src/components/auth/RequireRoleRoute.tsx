import { Navigate, Outlet } from 'react-router-dom'

import { useAuth } from '@/hooks/useAuth'
import type { Role } from '@/types/auth'

/**
 * Full-page route guard (vs. RequireRole, which hides/swaps inline controls).
 * UX-only, same as RequireRole — the server's @PreAuthorize remains the
 * actual enforcement.
 */
export function RequireRoleRoute({ role }: { role: Role }) {
  const { hasRole } = useAuth()

  if (!hasRole(role)) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
