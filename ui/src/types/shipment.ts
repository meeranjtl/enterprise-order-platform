export type ShipmentStatus = 'PENDING' | 'SHIPPED' | 'DELIVERED'

export interface Shipment {
  id: number
  orderId: number
  customerId: number
  trackingNumber?: string
  status: ShipmentStatus
  packingList?: string
  shippedAt?: string
  deliveredAt?: string
  createdAt?: string
  updatedAt?: string
}
