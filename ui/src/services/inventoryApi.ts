import { apiClient } from '@/lib/api'
import { unwrap } from '@/lib/unwrap'
import type { BaseResponse } from '@/types/api'

export interface StockAdjustment {
  productId: number
  quantity: number
  reason: string
}

export interface Inventory {
  id: number
  productId: number
  totalQuantity: number
  reservedQuantity: number
  availableQuantity: number
}

// inventory-service keeps its own reservable stock pool, separate from
// product-service's catalog `stockQuantity` — a brand-new product has no
// row here until this is called, so orders for it fail reservation
// silently. Called once, right after product creation, to seed it.
export async function adjustInventory(payload: StockAdjustment): Promise<Inventory> {
  const { data } = await apiClient.post<BaseResponse<Inventory>>('/api/v1/inventory/adjust', payload, {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  })
  return unwrap(data)
}
