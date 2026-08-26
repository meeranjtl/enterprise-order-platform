import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { CreateOrderDialog } from '@/components/orders/CreateOrderDialog'
import { DataTablePagination } from '@/components/DataTablePagination'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useAuth } from '@/hooks/useAuth'
import { listOrders, listOrdersByCustomer } from '@/services/orderApi'

const PAGE_SIZE = 10
const ALL_STATUSES = 'all'
const currencyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

export default function OrdersPage() {
  const { user, hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')
  const [status, setStatus] = useState(ALL_STATUSES)
  const [page, setPage] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)

  const ordersQuery = useQuery({
    queryKey: ['orders', { scope: isAdmin ? 'all' : user?.id, status, page }],
    queryFn: () => {
      const params = { status: status === ALL_STATUSES ? undefined : status, page, size: PAGE_SIZE }
      return isAdmin ? listOrders(params) : listOrdersByCustomer(Number(user!.id), params)
    },
    enabled: !!user,
    placeholderData: (previous) => previous,
  })

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Orders</h1>
          <p className="text-sm text-muted-foreground">
            {isAdmin ? 'All orders across the platform.' : 'Your order history.'}
          </p>
        </div>
        {!isAdmin && (
          <Button onClick={() => setCreateOpen(true)}>
            <Plus /> New order
          </Button>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Filters</CardTitle>
        </CardHeader>
        <CardContent>
          <Select
            value={status}
            onValueChange={(value) => {
              setStatus(value)
              setPage(0)
            }}
          >
            <SelectTrigger className="w-full sm:w-64">
              <SelectValue placeholder="All statuses" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_STATUSES}>All statuses</SelectItem>
              <SelectItem value="PENDING">Pending</SelectItem>
              <SelectItem value="VALIDATED">Validated</SelectItem>
              <SelectItem value="PAYMENT_PENDING">Payment pending</SelectItem>
              <SelectItem value="PAYMENT_APPROVED">Payment approved</SelectItem>
              <SelectItem value="PAYMENT_REJECTED">Payment rejected</SelectItem>
              <SelectItem value="SHIPPED">Shipped</SelectItem>
              <SelectItem value="COMPLETED">Completed</SelectItem>
              <SelectItem value="CANCELLED">Cancelled</SelectItem>
              <SelectItem value="FAILED">Failed</SelectItem>
            </SelectContent>
          </Select>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="flex flex-col gap-3">
          {ordersQuery.isLoading && (
            <div className="flex flex-col gap-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          )}

          {ordersQuery.isError && (
            <p className="py-8 text-center text-sm text-destructive">Failed to load orders. Please try again.</p>
          )}

          {ordersQuery.data?.content.length === 0 && (
            <p className="py-8 text-center text-sm text-muted-foreground">No orders match these filters.</p>
          )}

          {/* Stacked cards below sm; table from sm up — same data, two renderings. */}
          {ordersQuery.data && ordersQuery.data.content.length > 0 && (
            <div className="flex flex-col gap-2 sm:hidden">
              {ordersQuery.data.content.map((order) => (
                <Link
                  key={order.id}
                  to={`/orders/${order.id}`}
                  className="flex flex-col gap-1 rounded-lg border p-3 text-sm hover:bg-accent"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium text-primary">{order.orderNumber}</span>
                    <StatusBadge status={order.status} />
                  </div>
                  <div className="flex items-center justify-between text-muted-foreground">
                    <span>
                      {isAdmin && `Customer #${order.customerId} · `}
                      {order.items?.length ?? 0} item{order.items?.length === 1 ? '' : 's'}
                    </span>
                    <span className="font-medium text-foreground">{currencyFormatter.format(order.totalAmount)}</span>
                  </div>
                </Link>
              ))}
            </div>
          )}

          {ordersQuery.data && ordersQuery.data.content.length > 0 && (
            <div className="hidden overflow-x-auto sm:block">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Order #</TableHead>
                    {isAdmin && <TableHead>Customer</TableHead>}
                    <TableHead>Items</TableHead>
                    <TableHead className="text-right">Total</TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {ordersQuery.data.content.map((order) => (
                    <TableRow key={order.id}>
                      <TableCell className="font-medium">
                        <Link to={`/orders/${order.id}`} className="text-primary hover:underline">
                          {order.orderNumber}
                        </Link>
                      </TableCell>
                      {isAdmin && <TableCell className="text-muted-foreground">{order.customerId}</TableCell>}
                      <TableCell className="text-muted-foreground">{order.items?.length ?? 0}</TableCell>
                      <TableCell className="text-right">{currencyFormatter.format(order.totalAmount)}</TableCell>
                      <TableCell>
                        <StatusBadge status={order.status} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          {ordersQuery.data && (
            <DataTablePagination
              page={ordersQuery.data.number}
              totalPages={ordersQuery.data.totalPages}
              totalElements={ordersQuery.data.totalElements}
              onPageChange={setPage}
            />
          )}
        </CardContent>
      </Card>

      <CreateOrderDialog open={createOpen} onOpenChange={setCreateOpen} />
    </div>
  )
}
