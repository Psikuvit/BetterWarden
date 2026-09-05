import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ticketsApi, type TicketThread as Thread } from '../api/tickets'
import './Panel.css'

export default function TicketThread() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [thread, setThread] = useState<Thread | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reply, setReply] = useState('')
  const [sending, setSending] = useState(false)

  function reload() {
    if (!id) return
    ticketsApi
      .thread(id)
      .then((t) => {
        setThread(t)
        setError(null)
      })
      .catch(() => setError('Could not load this ticket'))
  }

  useEffect(reload, [id])

  async function send(internal: boolean) {
    if (!id || !reply.trim()) return
    setSending(true)
    try {
      await ticketsApi.reply(id, reply.trim(), internal)
      setReply('')
      reload()
    } finally {
      setSending(false)
    }
  }

  async function onClose() {
    if (!id) return
    await ticketsApi.close(id)
    navigate('/tickets')
  }

  if (error) return <p style={{ color: 'var(--danger)' }}>{error}</p>
  if (!thread) return <p className="muted">Loading...</p>

  const { ticket, messages } = thread

  return (
    <div>
      <div className="breadcrumb muted">
        <Link to="/tickets">Tickets</Link> / #{ticket.id}
      </div>
      <div className="thread-header">
        <h1>{ticket.subject}</h1>
        {ticket.status !== 'CLOSED' && (
          <button className="btn" onClick={onClose}>
            Close ticket
          </button>
        )}
      </div>

      <div className="thread-grid">
        <div>
          <div className="message-list">
            {messages.length === 0 && <p className="muted">No messages yet.</p>}
            {messages.map((m) => (
              <div key={m.id} className={`message ${m.internal ? 'internal' : m.author !== ticket.openerUuid ? 'staff' : ''}`}>
                <div className="msg-avatar" />
                <div>
                  <div className="msg-bubble">{m.body}</div>
                  <div className="msg-meta">
                    {m.internal ? 'Internal note - not visible to player' : m.authorName} &middot;{' '}
                    {new Date(m.sentAt).toLocaleString()}
                  </div>
                </div>
              </div>
            ))}
          </div>
          {ticket.status !== 'CLOSED' && (
            <div className="reply-box">
              <textarea
                placeholder={`Reply to ${ticket.openerName}...`}
                value={reply}
                onChange={(e) => setReply(e.target.value)}
              />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <button className="btn btn-sm" disabled={sending || !reply.trim()} onClick={() => send(false)}>
                  Send
                </button>
                <button className="btn btn-sm" disabled={sending || !reply.trim()} onClick={() => send(true)}>
                  Internal note
                </button>
              </div>
            </div>
          )}
        </div>

        <div className="card">
          <h2 style={{ marginTop: 0, fontSize: '0.95rem' }}>Details</h2>
          <dl className="meta">
            <dt>Player</dt>
            <dd>
              <Link to={`/players/${ticket.openerUuid}`}>{ticket.openerName}</Link>
            </dd>
            <dt>Priority</dt>
            <dd>{ticket.priority}</dd>
            <dt>Status</dt>
            <dd>{ticket.status}</dd>
            <dt>Assigned</dt>
            <dd>{ticket.assignee ?? 'Unassigned'}</dd>
            <dt>Opened</dt>
            <dd>{new Date(ticket.createdAt).toLocaleString()}</dd>
          </dl>
        </div>
      </div>
    </div>
  )
}
