import { ChevronsLeft, ChevronsRight } from 'lucide-react'
import { useState } from 'react'

import { NavList } from '@/components/layout/NavList'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export function Sidebar() {
  const [collapsed, setCollapsed] = useState(false)

  return (
    <aside
      className={cn(
        'sticky top-0 hidden h-svh shrink-0 flex-col border-r bg-sidebar text-sidebar-foreground transition-[width] duration-200 md:flex',
        collapsed ? 'w-16' : 'w-56',
      )}
    >
      <div className="flex h-14 items-center gap-2 border-b px-4">
        <div className="flex size-6 shrink-0 items-center justify-center rounded-md bg-primary text-xs font-bold text-primary-foreground">
          E
        </div>
        {!collapsed && <span className="truncate text-sm font-semibold">Order Platform</span>}
      </div>

      <div className="flex-1 overflow-y-auto py-3">
        <NavList collapsed={collapsed} />
      </div>

      <div className="border-t p-2">
        <Button
          variant="ghost"
          size="icon"
          className="w-full"
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          onClick={() => setCollapsed((value) => !value)}
        >
          {collapsed ? <ChevronsRight /> : <ChevronsLeft />}
        </Button>
      </div>
    </aside>
  )
}
