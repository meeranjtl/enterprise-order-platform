import type { BaseResponse } from '@/types/api'

export function unwrap<T>(response: BaseResponse<T>): T {
  if (response.data === undefined) {
    throw new Error(response.error?.message ?? response.message ?? 'Unexpected empty response')
  }
  return response.data
}
