import { useEffect, useState } from 'react'
import { api, ApiError, type HealthStatus } from '../api/client'

// First real page: proves the full loop (React -> Spring Boot REST -> back), same spirit as
// Stage 0's Paper risk spike. Only wired to /health so far - the actual dashboard counters
// (open reports/tickets/appeals, activity chart, node strip - spec §4) need endpoints that
// don't exist yet.
export default function Dashboard() {
  const [health, setHealth] = useState<HealthStatus | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .health()
      .then(setHealth)
      .catch((e: unknown) => setError(e instanceof ApiError ? e.message : 'Could not reach Core'))
  }, [])

  return (
    <div>
      <h1>Dashboard</h1>
      <p className="muted">
        Live counters, punishment activity chart, recent actions feed, and node status strip are
        not built yet - this is a skeleton proving Core is actually reachable.
      </p>

      <div className="card" style={{ maxWidth: 360 }}>
        <h2>Core status</h2>
        {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
        {!error && !health && <p className="muted">Loading...</p>}
        {health && (
          <dl className="stat-list">
            <div>
              <dt>Status</dt>
              <dd style={{ color: health.status === 'ok' ? 'var(--success)' : 'var(--danger)' }}>
                {health.status}
              </dd>
            </div>
            <div>
              <dt>Players on record</dt>
              <dd>{health.players}</dd>
            </div>
            <div>
              <dt>Server time</dt>
              <dd>{new Date(health.time).toLocaleString()}</dd>
            </div>
          </dl>
        )}
      </div>
    </div>
  )
}
