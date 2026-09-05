import { useEffect, useState } from 'react'
import ActivityChart from '../components/ActivityChart'
import { dashboardApi, type DashboardData } from '../api/dashboard'
import './Dashboard.css'

export default function Dashboard() {
  const [data, setData] = useState<DashboardData | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    dashboardApi
      .get()
      .then(setData)
      .catch(() => setError('Could not reach Core'))
  }, [])

  if (error) {
    return <p style={{ color: 'var(--danger)' }}>{error}</p>
  }
  if (!data) {
    return <p className="muted">Loading...</p>
  }

  return (
    <div>
      <h1>Dashboard</h1>

      <div className="stat-grid">
        <StatCard label="Online players" value={data.onlinePlayers} />
        <StatCard label="Open reports" value={data.openReports} />
        <StatCard label="Open tickets" value={data.openTickets} />
        <StatCard label="Pending appeals" value={data.pendingAppeals} />
        <StatCard label="Active bans" value={data.activeBans} />
        <StatCard label="Active mutes" value={data.activeMutes} />
        <StatCard label="Connected nodes" value={data.connectedNodes} />
      </div>

      <div className="dash-columns">
        <div className="card">
          <h2>Punishment activity - last 30 days</h2>
          <ActivityChart data={data.punishmentActivity} />
        </div>

        <div className="card">
          <h2>Recent actions</h2>
          {data.recentActions.length === 0 && <p className="muted">Nothing yet.</p>}
          <ul className="action-feed">
            {data.recentActions.map((a) => (
              <li key={a.id}>
                {/* a.actor is a raw UUID for staff (CONSOLE otherwise) - no name resolution wired
                    up here yet, same open item as PunishmentService.resolveStaffName elsewhere. */}
                <span className="action-actor">{a.actor}</span>
                <span className="muted"> {a.action.toLowerCase().replaceAll('_', ' ')}</span>
                {a.target && <span className="muted"> - {a.target}</span>}
                <time className="action-time muted" dateTime={a.at}>
                  {new Date(a.at).toLocaleString()}
                </time>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  )
}

function StatCard({ label, value, note }: { label: string; value: number; note?: string }) {
  return (
    <div className="card stat-card">
      <div className="stat-card-value">{value}</div>
      <div className="muted">{label}</div>
      {note && (
        <div className="muted" style={{ fontSize: '0.7rem' }}>
          ({note})
        </div>
      )}
    </div>
  )
}
