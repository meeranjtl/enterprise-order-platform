import { apiClient } from '@/lib/api'
import { unwrap } from '@/lib/unwrap'
import type { BaseResponse } from '@/types/api'
import type { Shipment } from '@/types/shipment'

export async function getShipmentByOrderId(orderId: number): Promise<Shipment> {
  const { data } = await apiClient.get<BaseResponse<Shipment>>('/api/v1/shipments', { params: { orderId } })
  return unwrap(data)
}

export async function deliverShipment(id: number): Promise<Shipment> {
  const { data } = await apiClient.post<BaseResponse<Shipment>>(`/api/v1/shipments/${id}/deliver`)
  return unwrap(data)
}
