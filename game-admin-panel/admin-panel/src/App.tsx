import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import PrivateRoute from './auth/PrivateRoute'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import AccountsPage from './pages/AccountsPage'
import AccountDetailPage from './pages/AccountDetailPage'
import CreateAccountPage from './pages/CreateAccountPage'
import EditAccountPage from './pages/EditAccountPage'
import ChangePasswordPage from './pages/ChangePasswordPage'
import ChangeStatusPage from './pages/ChangeStatusPage'
import AppLayout from './components/AppLayout'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<PrivateRoute><AppLayout /></PrivateRoute>}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/accounts" element={<AccountsPage />} />
          <Route path="/accounts/new" element={<CreateAccountPage />} />
          <Route path="/accounts/:id" element={<AccountDetailPage />} />
          <Route path="/accounts/:id/edit" element={<EditAccountPage />} />
          <Route path="/accounts/:id/password" element={<ChangePasswordPage />} />
          <Route path="/accounts/:id/status" element={<ChangeStatusPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </AuthProvider>
  )
}
