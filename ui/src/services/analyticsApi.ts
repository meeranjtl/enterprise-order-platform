import { apiClient } from '@/lib/api'
import { unwrap } from '@/lib/unwrap'
import type { AnalyticsSummary, DailyMetric } from '@/types/analytics'
import type { BaseResponse } from '@/types/api'

export async function getSummary(): Promise<AnalyticsSummary> {
  const { data } = await apiClient.get<BaseResponse<AnalyticsSummary>>('/api/v1/analytics/summary')
  return unwrap(data)
}

export async function getDailyMetrics(params?: { from?: string; to?: string }): Promise<DailyMetric[]> {
  const { data } = await apiClient.get<BaseResponse<DailyMetric[]>>('/api/v1/analytics/daily-metrics', { params })
  return unwrap(data)
}
