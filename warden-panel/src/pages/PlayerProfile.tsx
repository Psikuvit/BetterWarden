import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { playersApi, type PlayerProfile as Profile } from '../api/players'
import './Panel.css'

const PUNISH_TYPES = ['WARN', 'KICK', 'MUTE', 'TEMPMUTE', 'BAN', 'TEMPBAN', 'IPBAN']

export default function PlayerProfile() {
  const { uuid } = useParams<{ uuid: string }>()
  const [profile, setProfile] = useState<Profile | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showPunish, setShowPunish] = useState(false)
  const [noteBody, setNoteBody] = useState('')
  const [savingNote, setSavingNote] = useState(false)

  function reload() {
    if (!uuid) return
    playersApi
      .profile(uuid)
      .then((p) => {
        setProfile(p)
        setError(null)
      })
      .catch(() => setError('Could not load this player'))
  }

  useEffect(reload, [uuid])

  async function onAddNote() {
    if (!uuid || !noteBody.trim()) return
    setSavingNote(true)
    try {
      await playersApi.addNote(uuid, noteBody.trim())
      setNoteBody('')
      reload()
    } finally {
      setSavingNote(false)
    }
  }

  if (error) return <p style={{ color: 'var(--danger)' }}>{error}</p>
  if (!profile) return <p className="muted">Loading...</p>

  return (
    <div>
      <div className="breadcrumb muted">
        <Link to="/players">Players</Link> / {profile.name}
      </div>
      <div className="profile-header">
        <span className="skin-render" />
        <div>
          <div className="profile-name">{profile.name}</div>
          <div className="profile-meta">
            {profile.nameHistory.length > 1 && <>Also known as: {profile.nameHistory.slice(1).join(', ')} &middot; </>}
            First joined {new Date(profile.firstSeen).toLocaleDateString()}
          </div>
        </div>
        <button className="btn btn-danger" style={{ marginLeft: 'auto' }} onClick={() => setShowPunish((v) => !v)}>
          Punish this player
        </button>
      </div>

      {showPunish && uuid && (
        <PunishForm
          uuid={uuid}
          onDone={() => {
            setShowPunish(false)
            reload()
          }}
        />
      )}

      <div className="profile-grid">
        <div className="stack">
          <div className="card">
            <h2>Punishment timeline</h2>
            {profile.punishments.length === 0 && <p className="muted">No punishments on record.</p>}
            <ul className="timeline">
              {profile.punishments.map((p) => (
                <li key={p.id}>
                  <div className="timeline-dot" style={{ background: p.active ? 'var(--danger)' : 'var(--text-muted)' }} />
                  <div>
                    {p.type} - {p.reason}
                    <div className="timeline-time">
                      {new Date(p.issuedAt).toLocaleString()} &middot; by {p.staffName}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          </div>
          <div className="card">
            <h2>Chat log</h2>
            <div className="coming-soon-tab">Chat log viewer with filter-hit highlighting - planned for v1.1, not built yet.</div>
          </div>
        </div>

        <div className="stack">
          <div className="card">
            <h2>Alt accounts</h2>
            {profile.alts.length === 0 && <p className="muted">None detected.</p>}
            {profile.alts.map((a) => (
              <div key={a.uuid} className="alt-row">
                <Link to={`/players/${a.uuid}`}>{a.name}</Link>
                <span className="confidence confidence-high">Shared IP</span>
              </div>
            ))}
          </div>
          <div className="card">
            <h2>Staff notes</h2>
            {profile.notes.length === 0 && <p className="muted">No notes yet.</p>}
            {profile.notes.map((n) => (
              <div key={n.id} className="note-item">
                {n.body}
                <div className="note-meta">
                  {n.staffUuid} &middot; {new Date(n.createdAt).toLocaleDateString()}
                </div>
              </div>
            ))}
            <div className="note-form">
              <textarea placeholder="Add a note..." value={noteBody} onChange={(e) => setNoteBody(e.target.value)} />
              <button className="btn btn-sm" disabled={savingNote || !noteBody.trim()} onClick={onAddNote}>
                Add
              </button>
            </div>
          </div>
          <div className="card">
            <h2>
              IP history <span className="muted" style={{ fontWeight: 400 }}>(ADMIN only)</span>
            </h2>
            {profile.ipHistory === null && <p className="muted">Not visible to your role.</p>}
            {profile.ipHistory?.length === 0 && <p className="muted">No history recorded.</p>}
            {profile.ipHistory?.map((h) => (
              <div key={h} className="note-item mono" style={{ fontFamily: 'ui-monospace, monospace' }}>
                {h}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

function PunishForm({ uuid, onDone }: { uuid: string; onDone: () => void }) {
  const [type, setType] = useState('WARN')
  const [reason, setReason] = useState('')
  const [durationMinutes, setDurationMinutes] = useState('')
  const [silent, setSilent] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit() {
    if (!reason.trim()) return
    setSaving(true)
    setError(null)
    try {
      await playersApi.punish(uuid, {
        type,
        reason: reason.trim(),
        durationSeconds: durationMinutes ? Number(durationMinutes) * 60 : null,
        silent,
      })
      onDone()
    } catch {
      setError('Could not issue this punishment')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="card" style={{ marginBottom: 20 }}>
      <h2>New punishment</h2>
      <div className="filter-bar" style={{ marginBottom: 10 }}>
        <select value={type} onChange={(e) => setType(e.target.value)}>
          {PUNISH_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
        <input
          className="search"
          type="text"
          placeholder="Reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
        <input
          type="number"
          min={0}
          placeholder="Duration (minutes, blank = permanent)"
          style={{ width: 220 }}
          value={durationMinutes}
          onChange={(e) => setDurationMinutes(e.target.value)}
        />
        <label className="muted" style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.85rem' }}>
          <input type="checkbox" checked={silent} onChange={(e) => setSilent(e.target.checked)} />
          Silent
        </label>
      </div>
      {error && <p style={{ color: 'var(--danger)', fontSize: '0.85rem' }}>{error}</p>}
      <button className="btn btn-danger" disabled={saving || !reason.trim()} onClick={submit}>
        {saving ? 'Issuing...' : 'Issue punishment'}
      </button>
    </div>
  )
}
