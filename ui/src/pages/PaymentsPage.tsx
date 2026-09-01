import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AxiosError } from 'axios'
import { Search } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'

import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/hooks/useAuth'
import { getPayment, refundPayment, retryPayment } from '@/services/paymentApi'
import type { BaseResponse } from '@/types/api'

const currencyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

export default function PaymentsPage() {
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')
  const queryClient = useQueryClient()
  const [input, setInput] = useState('')
  const [lookupId, setLookupId] = useState<number | null>(null)

  const paymentQuery = useQuery({
    queryKey: ['payments', lookupId],
    queryFn: () => getPayment(lookupId!),
    enabled: lookupId !== null,
    retry: false,
  })

  const retryMutation = useMutation({
    mutationFn: () => retryPayment(lookupId!),
    onSuccess: () => {
      toast.success('Payment retried')
      queryClient.invalidateQueries({ queryKey: ['payments', lookupId] })
    },
    onError: (err) => {
      const axiosError = err as AxiosError<BaseResponse<unknown>>
      toast.error('Retry failed', { description: axiosError.response?.data?.error?.message })
    },
  })

  const refundMutation = useMutation({
    mutationFn: () => refundPayment(lookupId!),
    onSuccess: () => {
      toast.success('Payment refunded')
      queryClient.invalidateQueries({ queryKey: ['payments', lookupId] })
    },
    onError: (err) => {
      const axiosError = err as AxiosError<BaseResponse<unknown>>
      toast.error('Refund failed', { description: axiosError.response?.data?.error?.message })
    },
  })

  function handleLookup() {
    const id = Number(input)
    if (!id) {
      toast.error('Enter a valid payment ID')
      return
    }
    setLookupId(id)
  }

  const payment = paymentQuery.data

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Payments</h1>
        <p className="text-sm text-muted-foreground">
          {isAdmin
            ? "Support tool — look up a payment directly by ID to inspect its status or retry/refund it. Most of the time you'll get here from an order's detail page, which shows its payment automatically."
            : "Look up a payment by ID to view its status. You'll usually get here from an order's detail page, which shows its payment automatically."}
        </p>
      </div>

      <Card>
        <CardContent className="flex gap-2 pt-6">
          <Input
            placeholder="Payment ID"
            value={input}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && handleLookup()}
            className="max-w-xs"
          />
          <Button onClick={handleLookup}>
            <Search /> Look up
          </Button>
        </CardContent>
      </Card>

      {paymentQuery.isLoading && (
        <Card>
          <CardContent className="py-6 text-sm text-muted-foreground">Loading…</CardContent>
        </Card>
      )}

      {paymentQuery.isError && (
        <Card>
          <CardContent className="py-6 text-sm text-destructive">No payment found with that ID.</CardContent>
        </Card>
      )}

      {payment && (
        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <div>
              <CardTitle>Payment #{payment.id}</CardTitle>
              <CardDescription>Order #{payment.orderId}</CardDescription>
            </div>
            <StatusBadge status={payment.status} />
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            <p>
              <span className="text-muted-foreground">Amount: </span>
              {currencyFormatter.format(payment.amount)}
            </p>
            <p>
              <span className="text-muted-foreground">Method: </span>
              {payment.method.replace('_', ' ')}
            </p>
            {payment.transactionId && (
              <p>
                <span className="text-muted-foreground">Transaction ID: </span>
                <span className="font-mono text-xs">{payment.transactionId}</span>
              </p>
            )}
            {payment.failureReason && (
              <p>
                <span className="text-muted-foreground">Failure reason: </span>
                {payment.failureReason}
              </p>
            )}
            {typeof payment.retryCount === 'number' && payment.retryCount > 0 && (
              <p>
                <span className="text-muted-foreground">Retry count: </span>
                {payment.retryCount}
              </p>
            )}

            {isAdmin && (payment.status === 'FAILED' || payment.status === 'COMPLETED') && (
              <div className="mt-2 flex gap-2">
                {payment.status === 'FAILED' && (
                  <Button size="sm" onClick={() => retryMutation.mutate()} disabled={retryMutation.isPending}>
                    {retryMutation.isPending ? 'Retrying…' : 'Retry payment'}
                  </Button>
                )}
                {payment.status === 'COMPLETED' && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => refundMutation.mutate()}
                    disabled={refundMutation.isPending}
                  >
                    {refundMutation.isPending ? 'Refunding…' : 'Refund payment'}
                  </Button>
                )}
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  )
}
