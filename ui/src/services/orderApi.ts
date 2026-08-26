import { apiClient } from '@/lib/api'
import { unwrap } from '@/lib/unwrap'
import type { BaseResponse } from '@/types/api'
import type { CreateOrderInput, Order } from '@/types/order'
import type { PageResponse } from '@/types/page'

export interface OrderListParams {
  status?: string
  page?: number
  size?: number
}

// GET /api/v1/orders is ADMIN-only server-side (OrderController).
export async function listOrders(params: OrderListParams): Promise<PageResponse<Order>> {
  const { data } = await apiClient.get<BaseResponse<PageResponse<Order>>>('/api/v1/orders', { params })
  return unwrap(data)
}

// GET /api/v1/orders/customer/{id} is self-or-admin server-side.
export async function listOrdersByCustomer(customerId: number, params: OrderListParams): Promise<PageResponse<Order>> {
  const { data } = await apiClient.get<BaseResponse<PageResponse<Order>>>(
    `/api/v1/orders/customer/${customerId}`,
    { params },
  )
  return unwrap(data)
}

export async function getOrder(id: number): Promise<Order> {
  const { data } = await apiClient.get<BaseResponse<Order>>(`/api/v1/orders/${id}`)
  return unwrap(data)
}

// POST /api/v1/orders is CUSTOMER-only server-side — an admin cannot place an
// order on this platform's RBAC model, only manage them.
export async function createOrder(payload: CreateOrderInput): Promise<Order> {
  const { data } = await apiClient.post<BaseResponse<Order>>('/api/v1/orders', payload)
  return unwrap(data)
}

export async function cancelOrder(id: number): Promise<void> {
  await apiClient.delete<BaseResponse<void>>(`/api/v1/orders/${id}`)
}
