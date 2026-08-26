import { useQuery } from '@tanstack/react-query'
import { CircleDot, DollarSign, ShoppingCart, Users } from 'lucide-react'
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Link } from 'react-router-dom'

import { StatusBadge } from '@/components/StatusBadge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useAuth } from '@/hooks/useAuth'
import { getDailyMetrics, getSummary } from '@/services/analyticsApi'
import { getSystemHealth } from '@/services/healthApi'
import { listOrders, listOrdersByCustomer } from '@/services/orderApi'
import { getProduct } from '@/services/productApi'

const currencyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const compactCurrencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
})
const dateFormatter = new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' })

function StatCard({
  label,
  value,
  icon: Icon,
  isLoading,
}: {
  label: string
  value: string
  icon: typeof DollarSign
  isLoading: boolean
}) {
  return (
    <Card>
      <CardContent className="flex items-center justify-between py-4">
        <div>
          <p className="text-sm text-muted-foreground">{label}</p>
          {isLoading ? <Skeleton className="mt-1 h-7 w-20" /> : <p className="text-2xl font-semibold tracking-tight">{value}</p>}
        </div>
        <div className="rounded-full bg-primary/10 p-2 text-primary">
          <Icon className="h-5 w-5" />
        </div>
      </CardContent>
    </Card>
  )
}

export default function DashboardPage() {
  const { user, hasRole } = useAuth()
  const isAdmin = hasRole('ADMIN')

  const summaryQuery = useQuery({ queryKey: ['analytics', 'summary'], queryFn: getSummary })
  const dailyMetricsQuery = useQuery({ queryKey: ['analytics', 'daily-metrics'], queryFn: () => getDailyMetrics() })

  const recentOrdersQuery = useQuery({
    queryKey: ['orders', 'recent', isAdmin ? 'all' : user?.id],
    queryFn: () =>
      isAdmin
        ? listOrders({ page: 0, size: 5 })
        : listOrdersByCustomer(Number(user!.id), { page: 0, size: 5 }),
    enabled: !!user,
  })

  const topProducts = summaryQuery.data?.topProducts ?? []
  const topProductDetailsQuery = useQuery({
    queryKey: ['products', 'top', topProducts.map((p) => p.productId)],
    queryFn: () => Promise.all(topProducts.map((p) => getProduct(p.productId).catch(() => null))),
    enabled: topProducts.length > 0,
  })

  const healthQuery = useQuery({
    queryKey: ['system', 'health'],
    queryFn: getSystemHealth,
    enabled: isAdmin,
    refetchInterval: 15000,
  })

  const chartData = (dailyMetricsQuery.data ?? []).map((metric) => ({
    date: dateFormatter.format(new Date(metric.metricDate)),
    revenue: metric.totalRevenue,
  }))

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="text-sm text-muted-foreground">Welcome back, {user?.email}.</p>
      </div>

      {summaryQuery.isError && (
        <p className="text-sm text-destructive">Failed to load analytics summary. Please try again.</p>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label="Total orders"
          value={String(summaryQuery.data?.totalOrders ?? 0)}
          icon={ShoppingCart}
          isLoading={summaryQuery.isLoading}
        />
        <StatCard
          label="Total revenue"
          value={currencyFormatter.format(summaryQuery.data?.totalRevenue ?? 0)}
          icon={DollarSign}
          isLoading={summaryQuery.isLoading}
        />
        <StatCard
          label="Active customers"
          value={String(summaryQuery.data?.distinctCustomers ?? 0)}
          icon={Users}
          isLoading={summaryQuery.isLoading}
        />
        <StatCard
          label="Avg order value"
          value={currencyFormatter.format(summaryQuery.data?.avgOrderValue ?? 0)}
          icon={CircleDot}
          isLoading={summaryQuery.isLoading}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-base">Revenue (last 30 days)</CardTitle>
          </CardHeader>
          <CardContent className="h-64">
            {dailyMetricsQuery.isLoading ? (
              <Skeleton className="h-full w-full" />
            ) : dailyMetricsQuery.isError ? (
              <div className="flex h-full items-center justify-center text-sm text-destructive">
                Failed to load revenue data.
              </div>
            ) : chartData.length === 0 ? (
              <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
                No revenue data yet.
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartData} margin={{ left: -20, right: 10, top: 10 }}>
                  <defs>
                    <linearGradient id="revenueFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="var(--primary)" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="var(--primary)" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <XAxis dataKey="date" fontSize={12} tickLine={false} axisLine={false} />
                  <YAxis fontSize={12} tickLine={false} axisLine={false} tickFormatter={(v) => compactCurrencyFormatter.format(v)} />
                  <Tooltip
                    formatter={(value) => currencyFormatter.format(Number(value))}
                    contentStyle={{ background: 'var(--popover)', border: '1px solid var(--border)', borderRadius: 8 }}
                  />
                  <Area type="monotone" dataKey="revenue" stroke="var(--primary)" fill="url(#revenueFill)" strokeWidth={2} />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Top products</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {summaryQuery.isLoading &&
              Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-8 w-full" />)}

            {!summaryQuery.isLoading && topProducts.length === 0 && (
              <p className="py-4 text-center text-sm text-muted-foreground">No product activity yet.</p>
            )}

            {topProducts.map((product, index) => {
              const detail = topProductDetailsQuery.data?.[index]
              return (
                <div key={product.productId} className="flex items-center justify-between gap-2 text-sm">
                  <div className="min-w-0">
                    <p className="truncate font-medium">{detail?.name ?? `Product #${product.productId}`}</p>
                    <p className="text-xs text-muted-foreground">{product.unitsSold} units sold</p>
                  </div>
                  <span className="shrink-0 font-mono text-xs text-muted-foreground">
                    {currencyFormatter.format(product.revenue)}
                  </span>
                </div>
              )
            })}
          </CardContent>
        </Card>
      </div>

      {isAdmin && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">System health</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-4">
            {healthQuery.isLoading &&
              Array.from({ length: 9 }).map((_, i) => <Skeleton key={i} className="h-6 w-28" />)}
            {healthQuery.isError && (
              <p className="text-sm text-destructive">Could not reach the health aggregator.</p>
            )}
            {healthQuery.data && (
              <>
                <div className="flex items-center gap-2 text-sm">
                  <span
                    className={`h-2.5 w-2.5 rounded-full ${
                      healthQuery.data.gatewayStatus === 'UP' ? 'bg-emerald-500' : 'bg-red-500'
                    }`}
                  />
                  Gateway
                </div>
                {healthQuery.data.services.map((service) => (
                  <div key={service.name} className="flex items-center gap-2 text-sm" title={service.detail ?? undefined}>
                    <span className={`h-2.5 w-2.5 rounded-full ${service.status === 'UP' ? 'bg-emerald-500' : 'bg-red-500'}`} />
                    {service.name}
                  </div>
                ))}
              </>
            )}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Recent orders</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Order #</TableHead>
                  <TableHead className="text-right">Total</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {recentOrdersQuery.isLoading &&
                  Array.from({ length: 5 }).map((_, i) => (
                    <TableRow key={i}>
                      {Array.from({ length: 3 }).map((__, j) => (
                        <TableCell key={j}>
                          <Skeleton className="h-4 w-full" />
                        </TableCell>
                      ))}
                    </TableRow>
                  ))}

                {recentOrdersQuery.isError && (
                  <TableRow>
                    <TableCell colSpan={3} className="py-8 text-center text-sm text-destructive">
                      Failed to load recent orders.
                    </TableCell>
                  </TableRow>
                )}

                {recentOrdersQuery.data?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3} className="py-8 text-center text-sm text-muted-foreground">
                      No orders yet.
                    </TableCell>
                  </TableRow>
                )}

                {recentOrdersQuery.data?.content.map((order) => (
                  <TableRow key={order.id}>
                    <TableCell className="font-medium">
                      <Link to={`/orders/${order.id}`} className="text-primary hover:underline">
                        {order.orderNumber}
                      </Link>
                    </TableCell>
                    <TableCell className="text-right">{currencyFormatter.format(order.totalAmount)}</TableCell>
                    <TableCell>
                      <StatusBadge status={order.status} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
