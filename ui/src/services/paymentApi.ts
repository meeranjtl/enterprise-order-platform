import { apiClient } from '@/lib/api'
import { unwrap } from '@/lib/unwrap'
import type { BaseResponse } from '@/types/api'
import type { Payment } from '@/types/payment'

// No GET /api/v1/payments list endpoint exists (PaymentController only has
// /{id}, /{id}/retry, /{id}/refund, and create) — see docs/gotchas.md
// §Phase 13. The Payments page is a lookup tool, not a listing, to match.
export async function getPayment(id: number): Promise<Payment> {
  const { data } = await apiClient.get<BaseResponse<Payment>>(`/api/v1/payments/${id}`)
  return unwrap(data)
}

export async function getPaymentByOrderId(orderId: number): Promise<Payment> {
  const { data } = await apiClient.get<BaseResponse<Payment>>('/api/v1/payments', { params: { orderId } })
  return unwrap(data)
}

export async function retryPayment(id: number): Promise<Payment> {
  const { data } = await apiClient.post<BaseResponse<Payment>>(`/api/v1/payments/${id}/retry`)
  return unwrap(data)
}

export async function refundPayment(id: number): Promise<Payment> {
  const { data } = await apiClient.post<BaseResponse<Payment>>(`/api/v1/payments/${id}/refund`)
  return unwrap(data)
}
