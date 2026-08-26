export type OrderStatus =
  | 'PENDING'
  | 'VALIDATED'
  | 'PAYMENT_PENDING'
  | 'PAYMENT_APPROVED'
  | 'PAYMENT_REJECTED'
  | 'CANCELLED'
  | 'FAILED'
  | 'SHIPPED'
  | 'COMPLETED'

export interface OrderItem {
  id: number
  productId: number
  productSku: string
  productName: string
  quantity: number
  unitPrice: number
  discount?: number
  lineTotal: number
}

export interface Order {
  id: number
  orderNumber: string
  customerId: number
  items: OrderItem[]
  subtotal: number
  tax: number
  shippingCost: number
  totalAmount: number
  status: OrderStatus
  createdAt?: string
  updatedAt?: string
}

export interface CreateOrderItemInput {
  productId: number
  quantity: number
}

export interface CreateOrderInput {
  customerId: number
  items: CreateOrderItemInput[]
}
