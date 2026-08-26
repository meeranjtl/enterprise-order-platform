import { Outlet } from 'react-router-dom'

import { Sidebar } from '@/components/layout/Sidebar'
import { TopBar } from '@/components/layout/TopBar'

export function AppShell() {
  return (
    <div className="flex min-h-svh">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar />
        <main className="flex-1 overflow-x-hidden p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
