export interface ErrorDetails {
  code: string
  message: string
  details?: string
}

export interface BaseResponse<T> {
  success: boolean
  message?: string
  data?: T
  error?: ErrorDetails
  timestamp?: string
}
