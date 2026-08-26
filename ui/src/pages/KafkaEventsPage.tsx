import { useQuery } from '@tanstack/react-query'
import { RadioTower } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { listRecentEvents } from '@/services/eventsApi'
import type { OutboxEventWithSource } from '@/types/event'

const dateTimeFormatter = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
})

const TOPIC_COLORS: Record<string, string> = {
  'order-events': 'bg-indigo-500/15 text-indigo-600 dark:text-indigo-400',
  'payment-events': 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400',
  'inventory-events': 'bg-amber-500/15 text-amber-600 dark:text-amber-400',
  'shipping-events': 'bg-blue-500/15 text-blue-600 dark:text-blue-400',
  'notification-events': 'bg-zinc-500/15 text-zinc-600 dark:text-zinc-400',
}

function formatPayload(payload: string): string {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

function EventRow({ event }: { event: OutboxEventWithSource }) {
  const topicColor = TOPIC_COLORS[event.kafkaTopic] ?? 'bg-zinc-500/15 text-zinc-600 dark:text-zinc-400'

  return (
    <div className="rounded-lg border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${topicColor}`}>
          {event.kafkaTopic}
        </span>
        <Badge variant="outline">{event.source}</Badge>
        <span className="text-sm font-medium">{event.eventType}</span>
        <span className="ml-auto font-mono text-xs text-muted-foreground">
          {dateTimeFormatter.format(new Date(event.createdAt))}
        </span>
      </div>
      <p className="mt-1 font-mono text-xs text-muted-foreground">
        aggregateId={event.aggregateId} · {event.published ? 'published' : 'pending'}
      </p>
      <pre className="mt-2 max-h-40 overflow-auto rounded-md bg-muted p-2 font-mono text-xs">
        {formatPayload(event.payload)}
      </pre>
    </div>
  )
}

export default function KafkaEventsPage() {
  const eventsQuery = useQuery({
    queryKey: ['events', 'recent'],
    queryFn: () => listRecentEvents(30),
    refetchInterval: 4000,
  })

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Kafka Events</h1>
        <p className="text-sm text-muted-foreground">
          Recent events from each service's transactional outbox, polled every few seconds.
        </p>
      </div>

      <Card>
        <CardHeader className="flex-row items-center gap-2">
          <RadioTower className="h-4 w-4 text-muted-foreground" />
          <CardTitle className="text-base">Live feed</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {eventsQuery.isLoading &&
            Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-24 w-full" />)}

          {eventsQuery.data?.length === 0 && (
            <p className="py-8 text-center text-sm text-muted-foreground">No events yet.</p>
          )}

          {eventsQuery.data?.map((event) => (
            <EventRow key={`${event.source}-${event.id}`} event={event} />
          ))}
        </CardContent>
      </Card>
    </div>
  )
}
