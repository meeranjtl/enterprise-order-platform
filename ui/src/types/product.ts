export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'OUT_OF_STOCK' | 'DISCONTINUED'

export interface Product {
  id: number
  sku: string
  name: string
  description?: string
  price: number
  stockQuantity: number
  status: ProductStatus
  categoryId: number
  categoryName?: string
  createdAt?: string
  updatedAt?: string
}

export interface Category {
  id: number
  name: string
  description?: string
  active?: boolean
}
