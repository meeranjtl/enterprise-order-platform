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
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useAuth } from '@/hooks/useAuth'
import { cancelOrder, getOrder } from '@/services/orderApi'
import { getPaymentByOrderId, refundPayment, retryPayment } from '@/services/paymentApi'
import { deliverShipment, getShipmentByOrderId } from '@/services/shipmentApi'
import type { BaseResponse } from '@/types/api'
import type { OrderStatus } from '@/types/order'

const currencyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const CANCELLABLE_STATUSES = new Set(['PENDING', 'VALIDATED', 'PAYMENT_PENDING'])
const TERMINAL_STATUSES = new Set<OrderStatus>(['CANCELLED', 'FAILED', 'COMPLETED'])
const SHIPMENT_ELIGIBLE_STATUSES = new Set<OrderStatus>(['PAYMENT_APPROVED', 'SHIPPED', 'COMPLETED'])

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const orderId = Number(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')

  const orderQuery = useQuery({
    queryKey: ['orders', orderId],
    queryFn: () => getOrder(orderId),
    enabled: Number.isFinite(orderId),
    // The saga advances the order asynchronously (validation → payment →
    // shipping → delivery) — poll while it's still in flight so the page
    // reflects progress without a manual refresh.
    refetchInterval: (query) => {
      const currentStatus = query.state.data?.status
      return currentStatus && !TERMINAL_STATUSES.has(currentStatus) ? 4000 : false
    },
  })
  const order = orderQuery.data

  const paymentQuery = useQuery({
    queryKey: ['payments', 'order', orderId],
    queryFn: () => getPaymentByOrderId(orderId),
    enabled: Number.isFinite(orderId) && order !== undefined && order.status !== 'PENDING',
    retry: false,
  })

  const shipmentQuery = useQuery({
    queryKey: ['shipments', 'order', orderId],
    queryFn: () => getShipmentByOrderId(orderId),
    enabled: Number.isFinite(orderId) && order !== undefined && SHIPMENT_ELIGIBLE_STATUSES.has(order.status),
    retry: false,
    refetchInterval: order?.status === 'SHIPPED' ? 4000 : false,
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

  const deliverMutation = useMutation({
    mutationFn: () => deliverShipment(shipmentQuery.data!.id),
    onSuccess: () => {
      toast.success('Shipment marked delivered', { description: 'The order will complete shortly.' })
      queryClient.invalidateQueries({ queryKey: ['shipments', 'order', orderId] })
      queryClient.invalidateQueries({ queryKey: ['orders', orderId] })
    },
    onError: (err) => {
      const axiosError = err as AxiosError<BaseResponse<unknown>>
      toast.error('Failed to mark delivered', { description: axiosError.response?.data?.error?.message })
    },
  })

  const retryPaymentMutation = useMutation({
    mutationFn: () => retryPayment(paymentQuery.data!.id),
    onSuccess: () => {
      toast.success('Payment retried')
      queryClient.invalidateQueries({ queryKey: ['payments', 'order', orderId] })
    },
    onError: (err) => {
      const axiosError = err as AxiosError<BaseResponse<unknown>>
      toast.error('Retry failed', { description: axiosError.response?.data?.error?.message })
    },
  })

  const refundPaymentMutation = useMutation({
    mutationFn: () => refundPayment(paymentQuery.data!.id),
    onSuccess: () => {
      toast.success('Payment refunded')
      queryClient.invalidateQueries({ queryKey: ['payments', 'order', orderId] })
    },
    onError: (err) => {
      const axiosError = err as AxiosError<BaseResponse<unknown>>
      toast.error('Refund failed', { description: axiosError.response?.data?.error?.message })
    },
  })

  if (orderQuery.isLoading) {
    return <Skeleton className="h-64 w-full" />
  }

  if (orderQuery.isError || !order) {
    return <p className="text-sm text-destructive">Failed to load this order.</p>
  }

  const canCancel = CANCELLABLE_STATUSES.has(order.status)
  const payment = paymentQuery.data
  const shipment = shipmentQuery.data

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

      {(payment || shipment) && (
        <div className="grid gap-4 sm:grid-cols-2">
          {payment && (
            <Card>
              <CardHeader className="flex flex-row items-center justify-between">
                <div>
                  <CardTitle className="text-base">Payment</CardTitle>
                  <CardDescription>{payment.method.replace('_', ' ')}</CardDescription>
                </div>
                <StatusBadge status={payment.status} />
              </CardHeader>
              <CardContent className="grid gap-1.5 text-sm">
                <p>
                  <span className="text-muted-foreground">Amount: </span>
                  {currencyFormatter.format(payment.amount)}
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
                {isAdmin && (payment.status === 'FAILED' || payment.status === 'COMPLETED') && (
                  <div className="mt-1 flex gap-2">
                    {payment.status === 'FAILED' && (
                      <Button
                        size="sm"
                        onClick={() => retryPaymentMutation.mutate()}
                        disabled={retryPaymentMutation.isPending}
                      >
                        {retryPaymentMutation.isPending ? 'Retrying…' : 'Retry payment'}
                      </Button>
                    )}
                    {payment.status === 'COMPLETED' && (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => refundPaymentMutation.mutate()}
                        disabled={refundPaymentMutation.isPending}
                      >
                        {refundPaymentMutation.isPending ? 'Refunding…' : 'Refund payment'}
                      </Button>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {shipment && (
            <Card>
              <CardHeader className="flex flex-row items-center justify-between">
                <div>
                  <CardTitle className="text-base">Shipment</CardTitle>
                  <CardDescription>
                    {shipment.trackingNumber ? `Tracking ${shipment.trackingNumber}` : 'No tracking number yet'}
                  </CardDescription>
                </div>
                <StatusBadge status={shipment.status} />
              </CardHeader>
              <CardContent className="grid gap-1.5 text-sm">
                {shipment.shippedAt && (
                  <p>
                    <span className="text-muted-foreground">Shipped: </span>
                    {new Date(shipment.shippedAt).toLocaleString()}
                  </p>
                )}
                {shipment.deliveredAt && (
                  <p>
                    <span className="text-muted-foreground">Delivered: </span>
                    {new Date(shipment.deliveredAt).toLocaleString()}
                  </p>
                )}
                {isAdmin && shipment.status === 'SHIPPED' && (
                  <Button
                    size="sm"
                    className="mt-1 w-fit"
                    onClick={() => deliverMutation.mutate()}
                    disabled={deliverMutation.isPending}
                  >
                    {deliverMutation.isPending ? 'Marking delivered…' : 'Mark as delivered'}
                  </Button>
                )}
              </CardContent>
            </Card>
          )}
        </div>
      )}

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
