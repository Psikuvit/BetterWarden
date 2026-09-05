import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { punishmentsApi, type PunishmentDetail as Detail } from '../api/punishments'
import './Panel.css'

const BADGE_CLASS: Record<string, string> = {
  BAN: 'badge-ban', TEMPBAN: 'badge-ban', IPBAN: 'badge-ban',
  MUTE: 'badge-mute', TEMPMUTE: 'badge-mute',
  WARN: 'badge-warn', KICK: 'badge-kick',
}

export default function PunishmentDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [detail, setDetail] = useState<Detail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revoking, setRevoking] = useState(false)

  useEffect(() => {
    if (!id) return
    punishmentsApi
      .detail(id)
      .then(setDetail)
      .catch(() => setError('Could not load this punishment'))
  }, [id])

  async function onRevoke() {
    if (!id || !detail) return
    const reason = window.prompt('Reason for revoking this punishment:')
    if (reason === null) return
    setRevoking(true)
    try {
      await punishmentsApi.revoke(id, reason)
      const fresh = await punishmentsApi.detail(id)
      setDetail(fresh)
    } catch {
      setError('Could not revoke - it may already be revoked')
    } finally {
      setRevoking(false)
    }
  }

  if (error) return <p style={{ color: 'var(--danger)' }}>{error}</p>
  if (!detail) return <p className="muted">Loading...</p>

  return (
    <div>
      <div className="breadcrumb muted">
        <Link to="/punishments">Punishments</Link> / #{detail.id}
      </div>
      <div className="detail-header">
        <div className="detail-title">
          <h1>{detail.playerName}</h1>
          <span className={`badge ${BADGE_CLASS[detail.type] ?? 'badge-kick'}`}>{detail.type}</span>
        </div>
        <div style={{ display: 'flex', gap: 10 }}>
          <button className="btn" onClick={() => navigate(`/players/${detail.playerUuid}`)}>
            View player
          </button>
          {detail.active && (
            <button className="btn btn-danger" disabled={revoking} onClick={onRevoke}>
              {revoking ? 'Revoking...' : 'Revoke punishment'}
            </button>
          )}
        </div>
      </div>

      <div className="detail-grid">
        <div className="card">
          <h2>Details</h2>
          <dl className="meta">
            <dt>Punishment ID</dt>
            <dd className="mono">#{detail.id}</dd>
            <dt>Reason</dt>
            <dd>{detail.reason}</dd>
            <dt>Issued by</dt>
            <dd>{detail.staffName}</dd>
            <dt>Server</dt>
            <dd>{detail.server ?? 'Unknown'}</dd>
            <dt>Issued</dt>
            <dd>{new Date(detail.issuedAt).toLocaleString()}</dd>
            <dt>Duration</dt>
            <dd>{detail.permanent ? 'Permanent' : detail.expiresAt ? new Date(detail.expiresAt).toLocaleString() : '-'}</dd>
            <dt>IP hash</dt>
            <dd className="mono">{detail.ipHash ?? '-'}</dd>
            <dt>Silent</dt>
            <dd>{detail.silent ? 'Yes' : 'No'}</dd>
          </dl>
        </div>

        <div className="card">
          <h2>Audit trail</h2>
          <ul className="timeline">
            <li>
              <div className="timeline-dot" />
              <div>
                Punishment issued by <b>{detail.staffName}</b>
                <div className="timeline-time">{new Date(detail.issuedAt).toLocaleString()}</div>
              </div>
            </li>
            {detail.revoke && (
              <li>
                <div className="timeline-dot" style={{ background: 'var(--success)' }} />
                <div>
                  Revoked - {detail.revoke.reason}
                  <div className="timeline-time">
                    {new Date(detail.revoke.revokedAt).toLocaleString()} by {detail.revoke.staffUuid ?? 'Console'}
                  </div>
                </div>
              </li>
            )}
          </ul>
        </div>
      </div>
    </div>
  )
}
