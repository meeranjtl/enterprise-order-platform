import { apiClient } from '@/lib/api'
import type { SystemHealthResponse } from '@/types/health'

// Gateway's own aggregator endpoint (not wrapped in BaseResponse — it's a
// gateway-native response, not a servlet-service one; see SystemHealthController).
export async function getSystemHealth(): Promise<SystemHealthResponse> {
  const { data } = await apiClient.get<SystemHealthResponse>('/api/v1/system/health')
  return data
}
