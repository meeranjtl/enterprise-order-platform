import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AxiosError } from 'axios'
import { ArrowLeft } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { OrderStatusTimeline } from '@/components/orders/OrderStatusTimeline'
import { StatusBadge } from '@/components/StatusBadge'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cancelOrder, getOrder } from '@/services/orderApi'
import type { BaseResponse } from '@/types/api'

const currencyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const CANCELLABLE_STATUSES = new Set(['PENDING', 'VALIDATED', 'PAYMENT_PENDING'])

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const orderId = Number(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const orderQuery = useQuery({
    queryKey: ['orders', orderId],
    queryFn: () => getOrder(orderId),
    enabled: Number.isFinite(orderId),
  })

  const cancelMutation = useMutation({
    mutationFn: () => cancelOrder(orderId),
    onSuccess: () => {
      toast.success('Order cancelled')
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      navigate('/orders')
    },
    onError: (err) => {
      const axiosError = err as AxiosError<BaseResponse<unknown>>
      toast.error('Failed to cancel order', {
        description: axiosError.response?.data?.error?.message,
      })
    },
  })

  if (orderQuery.isLoading) {
    return <Skeleton className="h-64 w-full" />
  }

  if (orderQuery.isError || !orderQuery.data) {
    return <p className="text-sm text-destructive">Failed to load this order.</p>
  }

  const order = orderQuery.data
  const canCancel = CANCELLABLE_STATUSES.has(order.status)

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon-sm" asChild>
            <Link to="/orders">
              <ArrowLeft />
            </Link>
          </Button>
          <div className="min-w-0">
            <h1 className="truncate text-2xl font-semibold tracking-tight">{order.orderNumber}</h1>
            <p className="text-sm text-muted-foreground">Order #{order.id}</p>
          </div>
        </div>
        <div className="flex items-center gap-2 sm:ml-auto">
          <StatusBadge status={order.status} />
          {canCancel && (
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button variant="outline" size="sm">
                  Cancel order
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Cancel this order?</AlertDialogTitle>
                  <AlertDialogDescription>This can't be undone.</AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Keep order</AlertDialogCancel>
                  <AlertDialogAction
                    variant="destructive"
                    onClick={() => cancelMutation.mutate()}
                    disabled={cancelMutation.isPending}
                  >
                    {cancelMutation.isPending ? 'Cancelling…' : 'Cancel order'}
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          )}
        </div>
      </div>

      <Card>
        <CardContent className="py-6">
          <OrderStatusTimeline status={order.status} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Items</CardTitle>
        </CardHeader>
        <CardContent>
          {/* Stacked cards below sm; table from sm up — same data, two renderings. */}
          <div className="flex flex-col gap-2 sm:hidden">
            {order.items.map((item) => (
              <div key={item.id} className="flex flex-col gap-1 rounded-lg border p-3 text-sm">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium">{item.productName}</span>
                  <span className="shrink-0">{currencyFormatter.format(item.lineTotal)}</span>
                </div>
                <div className="flex items-center justify-between text-muted-foreground">
                  <span className="font-mono text-xs">{item.productSku}</span>
                  <span>
                    {item.quantity} × {currencyFormatter.format(item.unitPrice)}
                  </span>
                </div>
              </div>
            ))}
            <div className="mt-2 grid gap-1 border-t pt-2 text-sm">
              <div className="flex justify-between text-muted-foreground">
                <span>Subtotal</span>
                <span>{currencyFormatter.format(order.subtotal)}</span>
              </div>
              <div className="flex justify-between text-muted-foreground">
                <span>Tax</span>
                <span>{currencyFormatter.format(order.tax)}</span>
              </div>
              <div className="flex justify-between text-muted-foreground">
                <span>Shipping</span>
                <span>{currencyFormatter.format(order.shippingCost)}</span>
              </div>
              <div className="flex justify-between font-medium">
                <span>Total</span>
                <span>{currencyFormatter.format(order.totalAmount)}</span>
              </div>
            </div>
          </div>

          <div className="hidden overflow-x-auto sm:block">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>SKU</TableHead>
                  <TableHead>Product</TableHead>
                  <TableHead className="text-right">Qty</TableHead>
                  <TableHead className="text-right">Unit price</TableHead>
                  <TableHead className="text-right">Line total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {order.items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-mono text-xs">{item.productSku}</TableCell>
                    <TableCell>{item.productName}</TableCell>
                    <TableCell className="text-right">{item.quantity}</TableCell>
                    <TableCell className="text-right">{currencyFormatter.format(item.unitPrice)}</TableCell>
                    <TableCell className="text-right">{currencyFormatter.format(item.lineTotal)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
              <TableFooter>
                <TableRow>
                  <TableCell colSpan={4}>Subtotal</TableCell>
                  <TableCell className="text-right">{currencyFormatter.format(order.subtotal)}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell colSpan={4}>Tax</TableCell>
                  <TableCell className="text-right">{currencyFormatter.format(order.tax)}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell colSpan={4}>Shipping</TableCell>
                  <TableCell className="text-right">{currencyFormatter.format(order.shippingCost)}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell colSpan={4} className="font-medium">
                    Total
                  </TableCell>
                  <TableCell className="text-right font-medium">{currencyFormatter.format(order.totalAmount)}</TableCell>
                </TableRow>
              </TableFooter>
            </Table>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
