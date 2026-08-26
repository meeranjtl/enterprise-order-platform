import { Menu } from 'lucide-react'
import { useState } from 'react'

import { NavList } from '@/components/layout/NavList'
import { ThemeToggle } from '@/components/theme-toggle'
import { UserMenu } from '@/components/layout/UserMenu'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'

export function TopBar() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  return (
    <header className="sticky top-0 z-40 flex h-14 items-center gap-2 border-b bg-background/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <Sheet open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
        <SheetTrigger asChild>
          <Button variant="ghost" size="icon" className="md:hidden" aria-label="Open navigation">
            <Menu />
          </Button>
        </SheetTrigger>
        <SheetContent side="left" className="w-64 p-0">
          <SheetHeader className="border-b">
            <SheetTitle>Order Platform</SheetTitle>
          </SheetHeader>
          <div className="py-3">
            <NavList onNavigate={() => setMobileNavOpen(false)} />
          </div>
        </SheetContent>
      </Sheet>

      <div className="flex-1" />

      <ThemeToggle />
      <UserMenu />
    </header>
  )
}
