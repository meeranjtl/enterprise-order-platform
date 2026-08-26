import { useQuery } from '@tanstack/react-query'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { getSystemHealth } from '@/services/healthApi'

const timeFormatter = new Intl.DateTimeFormat('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit' })

function StatusDot({ status }: { status: string }) {
  return (
    <span className="flex items-center gap-2">
      <span className={`h-2.5 w-2.5 rounded-full ${status === 'UP' ? 'bg-emerald-500' : 'bg-red-500'}`} />
      {status}
    </span>
  )
}

export default function SystemHealthPage() {
  const healthQuery = useQuery({
    queryKey: ['system', 'health'],
    queryFn: getSystemHealth,
    refetchInterval: 10000,
  })

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">System Health</h1>
        <p className="text-sm text-muted-foreground">
          Gateway + downstream service status, polled every 10 seconds.
          {healthQuery.data && ` Last checked ${timeFormatter.format(new Date(healthQuery.data.checkedAt))}.`}
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Gateway</CardTitle>
        </CardHeader>
        <CardContent>
          {healthQuery.isLoading ? (
            <Skeleton className="h-6 w-32" />
          ) : healthQuery.isError ? (
            <p className="text-sm text-destructive">Could not reach the gateway.</p>
          ) : (
            <StatusDot status={healthQuery.data!.gatewayStatus} />
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Downstream services</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Service</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Detail</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {healthQuery.isLoading &&
                  Array.from({ length: 8 }).map((_, i) => (
                    <TableRow key={i}>
                      {Array.from({ length: 3 }).map((__, j) => (
                        <TableCell key={j}>
                          <Skeleton className="h-4 w-full" />
                        </TableCell>
                      ))}
                    </TableRow>
                  ))}

                {healthQuery.isError && (
                  <TableRow>
                    <TableCell colSpan={3} className="py-8 text-center text-sm text-destructive">
                      Failed to load service health.
                    </TableCell>
                  </TableRow>
                )}

                {healthQuery.data?.services.map((service) => (
                  <TableRow key={service.name}>
                    <TableCell className="font-medium">{service.name}</TableCell>
                    <TableCell>
                      <StatusDot status={service.status} />
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">{service.detail ?? '—'}</TableCell>
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
