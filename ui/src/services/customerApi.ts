import { apiClient } from '@/lib/api'
import { unwrap } from '@/lib/unwrap'
import type { BaseResponse } from '@/types/api'
import type { Customer } from '@/types/customer'
import type { PageResponse } from '@/types/page'

export interface CustomerSearchParams {
  email?: string
  firstName?: string
  lastName?: string
  status?: string
  city?: string
  country?: string
  page?: number
  size?: number
}

export type CustomerInput = Omit<Customer, 'id' | 'createdAt' | 'updatedAt' | 'status'>

// GET /api/v1/customers/search/advanced and GET /api/v1/customers are both
// ADMIN-only server-side (CustomerController) — this page is admin-gated
// end to end, not just hidden from nav.
export async function searchCustomers(params: CustomerSearchParams): Promise<PageResponse<Customer>> {
  const { data } = await apiClient.get<BaseResponse<PageResponse<Customer>>>('/api/v1/customers/search/advanced', {
    params,
  })
  return unwrap(data)
}

export async function createCustomer(payload: CustomerInput): Promise<Customer> {
  const { data } = await apiClient.post<BaseResponse<Customer>>('/api/v1/customers', payload)
  return unwrap(data)
}

export async function updateCustomer(id: number, payload: CustomerInput): Promise<Customer> {
  const { data } = await apiClient.put<BaseResponse<Customer>>(`/api/v1/customers/${id}`, payload)
  return unwrap(data)
}

export async function deleteCustomer(id: number): Promise<void> {
  await apiClient.delete<BaseResponse<void>>(`/api/v1/customers/${id}`)
}
