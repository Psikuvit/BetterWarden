import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import RequireAuth from './auth/RequireAuth'
import AppLayout from './layout/AppLayout'
import Appeals from './pages/Appeals'
import ComingSoon from './pages/ComingSoon'
import Dashboard from './pages/Dashboard'
import Landing from './pages/Landing'
import Login from './pages/Login'
import PlayerProfile from './pages/PlayerProfile'
import Players from './pages/Players'
import PunishmentDetail from './pages/PunishmentDetail'
import Punishments from './pages/Punishments'
import Reports from './pages/Reports'
import Setup from './pages/Setup'
import TicketThread from './pages/TicketThread'
import Tickets from './pages/Tickets'

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
          <Route path="/punishments" element={<Punishments />} />
          <Route path="/punishments/:id" element={<PunishmentDetail />} />
          <Route path="/players" element={<Players />} />
          <Route path="/players/:uuid" element={<PlayerProfile />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/tickets" element={<Tickets />} />
          <Route path="/tickets/:id" element={<TicketThread />} />
          <Route path="/appeals" element={<Appeals />} />
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
