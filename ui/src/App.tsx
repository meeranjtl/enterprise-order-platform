import { Navigate, Route, Routes } from 'react-router-dom'

import { RequireAuth } from '@/components/auth/RequireAuth'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'
import { AppShell } from '@/components/layout/AppShell'
import CustomersPage from '@/pages/CustomersPage'
import DashboardPage from '@/pages/DashboardPage'
import KafkaEventsPage from '@/pages/KafkaEventsPage'
import LoginPage from '@/pages/LoginPage'
import OrderDetailPage from '@/pages/OrderDetailPage'
import OrdersPage from '@/pages/OrdersPage'
import PaymentsPage from '@/pages/PaymentsPage'
import ProductsPage from '@/pages/ProductsPage'
import RegisterPage from '@/pages/RegisterPage'
import SystemHealthPage from '@/pages/SystemHealthPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/products" element={<ProductsPage />} />
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/orders/:id" element={<OrderDetailPage />} />
          <Route path="/payments" element={<PaymentsPage />} />

          <Route element={<RequireRoleRoute role="ADMIN" />}>
            <Route path="/customers" element={<CustomersPage />} />
            <Route path="/kafka-events" element={<KafkaEventsPage />} />
            <Route path="/health" element={<SystemHealthPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
