import { apiClient } from '@/lib/api'
import { unwrap } from '@/lib/unwrap'
import type { BaseResponse } from '@/types/api'
import type { OutboxEvent, OutboxEventWithSource } from '@/types/event'

// Each producer service exposes its own transactional outbox history (Phase 13
// addition — see docs/gotchas.md) mounted under that service's own gateway-routed
// path prefix; there is no single platform-wide event bus endpoint. The UI fans
// out to all five producers in parallel and merges by timestamp.
const EVENT_SOURCES: { source: string; path: string }[] = [
  { source: 'Orders', path: '/api/v1/orders/events/recent' },
  { source: 'Payments', path: '/api/v1/payments/events/recent' },
  { source: 'Inventory', path: '/api/v1/inventory/events/recent' },
  { source: 'Shipments', path: '/api/v1/shipments/events/recent' },
  { source: 'Notifications', path: '/api/v1/notifications/events/recent' },
]

export async function listRecentEvents(limit = 20): Promise<OutboxEventWithSource[]> {
  const results = await Promise.allSettled(
    EVENT_SOURCES.map(async ({ source, path }) => {
      const { data } = await apiClient.get<BaseResponse<OutboxEvent[]>>(path, { params: { limit } })
      return unwrap(data).map((event) => ({ ...event, source }))
    }),
  )

  return results
    .flatMap((result) => (result.status === 'fulfilled' ? result.value : []))
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, limit)
}
