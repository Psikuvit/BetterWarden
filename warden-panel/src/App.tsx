import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import RequireAuth from './auth/RequireAuth'
import AppLayout from './layout/AppLayout'
import ComingSoon from './pages/ComingSoon'
import Dashboard from './pages/Dashboard'
import Landing from './pages/Landing'
import Login from './pages/Login'
import Setup from './pages/Setup'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={<Login />} />
        <Route path="/setup" element={<Setup />} />

        <Route
          element={
            <RequireAuth>
              <AppLayout />
            </RequireAuth>
          }
        >
          <Route path="/dash" element={<Dashboard />} />
          <Route path="/punishments" element={<ComingSoon title="Punishments" />} />
          <Route path="/players" element={<ComingSoon title="Players" />} />
          <Route path="/reports" element={<ComingSoon title="Reports" />} />
          <Route path="/tickets" element={<ComingSoon title="Tickets" />} />
          <Route path="/appeals" element={<ComingSoon title="Appeals" />} />
          <Route path="/staff-stats" element={<ComingSoon title="Staff Stats" />} />
          <Route path="/nodes" element={<ComingSoon title="Nodes" />} />
          <Route path="/settings" element={<ComingSoon title="Settings" />} />
          <Route path="/audit" element={<ComingSoon title="Audit Log" />} />
        </Route>

        <Route path="*" element={<ComingSoon title="Not found" />} />
      </Routes>
    </AuthProvider>
  )
}
