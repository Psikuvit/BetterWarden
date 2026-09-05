import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { appealsApi, type AppealEntry } from '../api/appeals'
import './Panel.css'

export default function Appeals() {
  const [appeals, setAppeals] = useState<AppealEntry[]>([])
  const [error, setError] = useState<string | null>(null)

  function reload() {
    appealsApi
      .list('PENDING')
      .then((res) => {
        setAppeals(res)
        setError(null)
      })
      .catch(() => setError('Could not reach Core'))
  }

  useEffect(reload, [])

  async function onApprove(id: number) {
    const note = window.prompt('Note for this approval (optional):') ?? ''
    await appealsApi.approve(id, note)
    reload()
  }

  async function onDeny(id: number) {
    const note = window.prompt('Reason for denying this appeal:')
    if (note === null) return
    await appealsApi.deny(id, note)
    reload()
  }

  async function onMoreInfo(id: number) {
    const note = window.prompt('What more info do you need?')
    if (note === null) return
    await appealsApi.moreInfo(id, note)
    reload()
  }

  return (
    <div>
      <div className="page-header">
        <h1>Appeals</h1>
      </div>

      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {!error && appeals.length === 0 && <p className="muted">No pending appeals.</p>}

      {appeals.length > 0 && (
        <div className="card">
          {appeals.map((a) => (
            <div key={a.id} className="appeal-row">
              <div>
                <div className="appeal-player">
                  <Link to={`/players/${a.playerUuid}`}>{a.playerName}</Link>
                </div>
                <div className="appeal-punishment">
                  {a.punishmentType} - {a.punishmentReason} &middot; {new Date(a.createdAt).toLocaleDateString()}
                </div>
              </div>
              <div className="appeal-message">&ldquo;{a.message}&rdquo;</div>
              <div className="appeal-actions">
                <button className="btn btn-sm btn-approve" onClick={() => onApprove(a.id)}>
                  Approve
                </button>
                <button className="btn btn-sm btn-danger" onClick={() => onDeny(a.id)}>
                  Deny
                </button>
                <button className="btn btn-sm" onClick={() => onMoreInfo(a.id)}>
                  More info
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
