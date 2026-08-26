import type { ReactNode } from 'react'

import { useAuth } from '@/hooks/useAuth'
import type { Role } from '@/types/auth'

interface RequireRoleProps {
  role: Role
  children: ReactNode
  /** Rendered instead of `children` when the user lacks the role. Defaults to nothing. */
  fallback?: ReactNode
}

/**
 * UX-only gating — hides or swaps controls the user's role can't use.
 * The server's @PreAuthorize rules (Phase 12) remain the actual enforcement;
 * never treat this as a security boundary.
 */
export function RequireRole({ role, children, fallback = null }: RequireRoleProps) {
  const { hasRole } = useAuth()
  return hasRole(role) ? <>{children}</> : <>{fallback}</>
}
