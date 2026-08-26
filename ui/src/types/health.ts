export interface ServiceHealthStatus {
  name: string
  status: 'UP' | 'DOWN'
  detail?: string | null
}

export interface SystemHealthResponse {
  gatewayStatus: string
  services: ServiceHealthStatus[]
  checkedAt: string
}
