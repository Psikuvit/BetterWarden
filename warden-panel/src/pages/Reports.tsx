import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { reportsApi, type ReportEntry } from '../api/reports'
import './Panel.css'

export default function Reports() {
  const [reports, setReports] = useState<ReportEntry[]>([])
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()

  function reload() {
    reportsApi
      .list('OPEN')
      .then((res) => {
        setReports(res)
        setError(null)
      })
      .catch(() => setError('Could not reach Core'))
  }

  useEffect(reload, [])

  async function onClaim(id: number) {
    await reportsApi.claim(id)
    reload()
  }

  async function onDismiss(id: number) {
    await reportsApi.dismiss(id)
    reload()
  }

  return (
    <div>
      <div className="page-header">
        <h1>Reports</h1>
        <span>
          <span className="live-dot" /> <span className="live-label">{reports.length} open</span>
        </span>
      </div>

      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {!error && reports.length === 0 && <p className="muted">No open reports.</p>}

      <div className="report-list">
        {reports.map((r) => (
          <div key={r.id} className="card report-card">
            <div className="report-top">
              <div>
                <span className="report-target">{r.targetName}</span> <span className="muted">reported for</span>{' '}
                <b>{r.category ?? r.reason}</b>
              </div>
              <div className="report-reporter">
                by {r.reporterName} &middot; {new Date(r.createdAt).toLocaleString()} {r.server && `· ${r.server}`}
              </div>
            </div>
            <div className="report-body">
              <div className="chat-snapshot">{r.chatSnapshot || 'No chat snapshot captured.'}</div>
              <div className="context-box">
                <dl>
                  <dt>Reason</dt>
                  <dd>{r.reason}</dd>
                  <dt>Location</dt>
                  <dd>{r.location ?? 'Unknown'}</dd>
                  <dt>Claimed by</dt>
                  <dd>{r.claimedBy ?? 'Unclaimed'}</dd>
                </dl>
              </div>
            </div>
            <div className="report-actions">
              <button className="btn btn-sm" onClick={() => onClaim(r.id)}>
                Claim
              </button>
              <button className="btn btn-sm btn-danger" onClick={() => navigate(`/players/${r.targetUuid}`)}>
                Punish
              </button>
              <button className="btn btn-sm btn-ghost" onClick={() => onDismiss(r.id)}>
                Dismiss
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
