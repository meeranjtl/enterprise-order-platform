export interface OutboxEvent {
  id: number
  aggregateId: string
  eventType: string
  kafkaTopic: string
  kafkaKey?: string | null
  payload: string
  published: boolean
  publishedAt?: string | null
  createdAt: string
}

// Client-side only — which producer service an event came from, since each
// service exposes its own outbox and the UI fans out and merges (see eventsApi.ts).
export interface OutboxEventWithSource extends OutboxEvent {
  source: string
}
