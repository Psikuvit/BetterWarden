import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ticketsApi, type TicketEntry } from '../api/tickets'
import './Panel.css'

const SOURCE_CLASS: Record<string, string> = { GAME: 'source-game', DISCORD: 'source-discord', WEB: 'source-web' }
const SOURCE_LETTER: Record<string, string> = { GAME: 'G', DISCORD: 'D', WEB: 'W' }
const PRIORITY_CLASS: Record<string, string> = { HIGH: 'priority-high', NORMAL: 'priority-normal', LOW: 'priority-low' }

export default function Tickets() {
  const [tickets, setTickets] = useState<TicketEntry[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    ticketsApi
      .list()
      .then((res) => {
        setTickets(res.filter((t) => t.status !== 'CLOSED'))
        setError(null)
      })
      .catch(() => setError('Could not reach Core'))
  }, [])

  return (
    <div>
      <div className="page-header">
        <h1>Tickets</h1>
      </div>

      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {!error && tickets.length === 0 && <p className="muted">No open tickets.</p>}

      {tickets.length > 0 && (
        <div className="card">
          {tickets.map((t) => (
            <Link key={t.id} to={`/tickets/${t.id}`} className="ticket-row">
              <div className={`source-badge ${SOURCE_CLASS[t.source]}`} title={t.source}>
                {SOURCE_LETTER[t.source]}
              </div>
              <div>
                <div className="ticket-subject">{t.subject}</div>
                <div className="ticket-preview">{t.openerName}</div>
              </div>
              <div className="ticket-meta">
                <span className={`priority-pill ${PRIORITY_CLASS[t.priority] ?? 'priority-normal'}`}>{t.priority}</span>
                <div>{new Date(t.createdAt).toLocaleDateString()}</div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
