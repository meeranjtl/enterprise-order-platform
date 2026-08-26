import { NavLink } from 'react-router-dom'

import { navItems } from '@/components/layout/nav-items'
import { useAuth } from '@/hooks/useAuth'
import { cn } from '@/lib/utils'

interface NavListProps {
  collapsed?: boolean
  onNavigate?: () => void
}

export function NavList({ collapsed = false, onNavigate }: NavListProps) {
  const { hasRole } = useAuth()

  return (
    <nav className="flex flex-col gap-1 px-2">
      {navItems
        .filter((item) => !item.requiredRole || hasRole(item.requiredRole))
        .map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            onClick={onNavigate}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                isActive
                  ? 'bg-primary/10 text-primary'
                  : 'text-muted-foreground hover:bg-muted hover:text-foreground',
              )
            }
            title={collapsed ? item.label : undefined}
          >
            <item.icon className="size-4 shrink-0" />
            {!collapsed && <span>{item.label}</span>}
          </NavLink>
        ))}
    </nav>
  )
}
