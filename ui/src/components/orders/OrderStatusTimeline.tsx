import { Check, X } from 'lucide-react'

import { cn } from '@/lib/utils'
import type { OrderStatus } from '@/types/order'

const HAPPY_PATH: { status: OrderStatus; label: string }[] = [
  { status: 'PENDING', label: 'Pending' },
  { status: 'VALIDATED', label: 'Validated' },
  { status: 'PAYMENT_APPROVED', label: 'Payment approved' },
  { status: 'SHIPPED', label: 'Shipped' },
  { status: 'COMPLETED', label: 'Completed' },
]

const FAILURE_STATUSES: OrderStatus[] = ['CANCELLED', 'FAILED', 'PAYMENT_REJECTED']

export function OrderStatusTimeline({ status }: { status: OrderStatus }) {
  if (FAILURE_STATUSES.includes(status)) {
    return (
      <div className="flex items-center gap-2 text-sm text-destructive">
        <span className="flex size-6 items-center justify-center rounded-full bg-destructive/15">
          <X className="size-3.5" />
        </span>
        Order {status === 'PAYMENT_REJECTED' ? 'payment was rejected' : status.toLowerCase()}
      </div>
    )
  }

  // PAYMENT_PENDING sits between VALIDATED and PAYMENT_APPROVED on the happy
  // path but isn't itself a step — treat it as "still on VALIDATED".
  const effectiveStatus = status === 'PAYMENT_PENDING' ? 'VALIDATED' : status
  const currentIndex = HAPPY_PATH.findIndex((step) => step.status === effectiveStatus)

  return (
    <div className="flex items-center overflow-x-auto pb-1">
      {HAPPY_PATH.map((step, index) => {
        const isComplete = index <= currentIndex
        const isLast = index === HAPPY_PATH.length - 1
        return (
          <div key={step.status} className="flex shrink-0 items-center sm:flex-1 sm:last:flex-none">
            <div className="flex shrink-0 flex-col items-center gap-1.5">
              <span
                className={cn(
                  'flex size-6 items-center justify-center rounded-full border text-xs',
                  isComplete
                    ? 'border-primary bg-primary text-primary-foreground'
                    : 'border-border bg-background text-muted-foreground',
                )}
              >
                {isComplete ? <Check className="size-3.5" /> : index + 1}
              </span>
              <span className={cn('text-xs whitespace-nowrap', isComplete ? 'text-foreground' : 'text-muted-foreground')}>
                {step.label}
              </span>
            </div>
            {!isLast && (
              <div className={cn('mx-2 h-px w-8 sm:w-auto sm:flex-1', index < currentIndex ? 'bg-primary' : 'bg-border')} />
            )}
          </div>
        )
      })}
    </div>
  )
}
