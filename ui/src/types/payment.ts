export type PaymentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'REFUNDED'
export type PaymentMethod = 'CREDIT_CARD' | 'DEBIT_CARD' | 'BANK_TRANSFER'

export interface Payment {
  id: number
  orderId: number
  customerId: number
  amount: number
  status: PaymentStatus
  method: PaymentMethod
  transactionId?: string
  failureReason?: string
  retryCount?: number
  nextRetryAt?: string
  createdAt?: string
  updatedAt?: string
}
