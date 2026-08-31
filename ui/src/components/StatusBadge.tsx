import { cn } from '@/lib/utils'

// One color vocabulary shared by every status shown anywhere in the app
// (orders, payments, shipments, products, customers) — see
// PHASE_13_GETTING_STARTED.md §3.3. Add new statuses here, not as one-off
// classes on a page.
const STATUS_STYLES: Record<string, string> = {
  // amber — needs attention / not yet resolved
  PENDING: 'bg-amber-500/15 text-amber-600 border-amber-500/25 dark:text-amber-400',
  PAYMENT_PENDING: 'bg-amber-500/15 text-amber-600 border-amber-500/25 dark:text-amber-400',
  OUT_OF_STOCK: 'bg-amber-500/15 text-amber-600 border-amber-500/25 dark:text-amber-400',
  SUSPENDED: 'bg-amber-500/15 text-amber-600 border-amber-500/25 dark:text-amber-400',
  // blue — actively in progress
  PROCESSING: 'bg-blue-500/15 text-blue-600 border-blue-500/25 dark:text-blue-400',
  CONFIRMED: 'bg-blue-500/15 text-blue-600 border-blue-500/25 dark:text-blue-400',
  VALIDATED: 'bg-blue-500/15 text-blue-600 border-blue-500/25 dark:text-blue-400',
  PAYMENT_APPROVED: 'bg-blue-500/15 text-blue-600 border-blue-500/25 dark:text-blue-400',
  // indigo — in transit
  SHIPPED: 'bg-indigo-500/15 text-indigo-600 border-indigo-500/25 dark:text-indigo-400',
  // emerald — terminal success / healthy
  DELIVERED: 'bg-emerald-500/15 text-emerald-600 border-emerald-500/25 dark:text-emerald-400',
  COMPLETED: 'bg-emerald-500/15 text-emerald-600 border-emerald-500/25 dark:text-emerald-400',
  ACTIVE: 'bg-emerald-500/15 text-emerald-600 border-emerald-500/25 dark:text-emerald-400',
  // red — terminal failure
  CANCELLED: 'bg-red-500/15 text-red-600 border-red-500/25 dark:text-red-400',
  FAILED: 'bg-red-500/15 text-red-600 border-red-500/25 dark:text-red-400',
  PAYMENT_REJECTED: 'bg-red-500/15 text-red-600 border-red-500/25 dark:text-red-400',
  DISCONTINUED: 'bg-red-500/15 text-red-600 border-red-500/25 dark:text-red-400',
  DELETED: 'bg-red-500/15 text-red-600 border-red-500/25 dark:text-red-400',
  // zinc — neutral/inactive fallback
  INACTIVE: 'bg-zinc-500/15 text-zinc-600 border-zinc-500/25 dark:text-zinc-400',
}

function formatStatusLabel(status: string): string {
  const lower = status.toLowerCase().replace(/_/g, ' ')
  return lower.charAt(0).toUpperCase() + lower.slice(1)
}

export function StatusBadge({ status, className }: { status: string; className?: string }) {
  const style = STATUS_STYLES[status] ?? STATUS_STYLES.INACTIVE
  return (
    <span
      className={cn('inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium', style, className)}
    >
      {formatStatusLabel(status)}
    </span>
  )
}
