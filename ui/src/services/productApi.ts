import { apiClient } from '@/lib/api'
import { unwrap } from '@/lib/unwrap'
import type { BaseResponse } from '@/types/api'
import type { PageResponse } from '@/types/page'
import type { Category, Product } from '@/types/product'

export interface ProductSearchParams {
  sku?: string
  name?: string
  categoryId?: number
  minPrice?: number
  maxPrice?: number
  status?: string
  inStockOnly?: boolean
  page?: number
  size?: number
}

export async function searchProducts(params: ProductSearchParams): Promise<PageResponse<Product>> {
  const { data } = await apiClient.get<BaseResponse<PageResponse<Product>>>('/api/v1/products/search', { params })
  return unwrap(data)
}

export async function getProduct(id: number): Promise<Product> {
  const { data } = await apiClient.get<BaseResponse<Product>>(`/api/v1/products/${id}`)
  return unwrap(data)
}

// Categories, routed through the new /api/v1/categories/** gateway route
// (see services/gateway application.yml — added for this page's filter).
export async function listCategories(): Promise<PageResponse<Category>> {
  const { data } = await apiClient.get<BaseResponse<PageResponse<Category>>>('/api/v1/categories', {
    params: { size: 100 },
  })
  return unwrap(data)
}
