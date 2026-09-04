import { Route, Routes } from 'react-router-dom'
import AppLayout from './layout/AppLayout'
import ComingSoon from './pages/ComingSoon'
import Dashboard from './pages/Dashboard'
import Landing from './pages/Landing'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />

      {/* No auth gate yet - see AppLayout's banner and PLAN.md Stage 5. */}
      <Route element={<AppLayout />}>
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
  )
}
