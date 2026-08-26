export interface ProductPerformance {
  productId: number
  unitsSold: number
  revenue: number
  timesInOrder: number
}

export interface AnalyticsSummary {
  totalOrders: number
  totalRevenue: number
  completedOrders: number
  failedOrders: number
  avgOrderValue: number
  distinctCustomers: number
  topProducts: ProductPerformance[]
}

export interface DailyMetric {
  metricDate: string
  totalOrders: number
  totalRevenue: number
  avgOrderValue: number
  completedOrders: number
  failedOrders: number
  distinctCustomers: number
}
