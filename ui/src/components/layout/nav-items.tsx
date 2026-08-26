import { Activity, CreditCard, LayoutDashboard, Package, Radio, ShoppingCart, Users } from 'lucide-react'

import type { Role } from '@/types/auth'

export interface NavItem {
  label: string
  to: string
  icon: typeof LayoutDashboard
  requiredRole?: Role
}

export const navItems: NavItem[] = [
  { label: 'Dashboard', to: '/', icon: LayoutDashboard },
  { label: 'Customers', to: '/customers', icon: Users, requiredRole: 'ADMIN' },
  { label: 'Products', to: '/products', icon: Package },
  { label: 'Orders', to: '/orders', icon: ShoppingCart },
  { label: 'Payments', to: '/payments', icon: CreditCard },
  { label: 'Kafka Events', to: '/kafka-events', icon: Radio, requiredRole: 'ADMIN' },
  { label: 'Health', to: '/health', icon: Activity, requiredRole: 'ADMIN' },
]
