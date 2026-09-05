import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import PlayerHead from '../components/PlayerHead'
import { punishmentsApi, type PunishmentRow, type BrowseFilters } from '../api/punishments'
import './Panel.css'

const TYPES = ['BAN', 'TEMPBAN', 'IPBAN', 'MUTE', 'TEMPMUTE', 'WARN', 'KICK']
const PAGE_SIZE = 25

const BADGE_CLASS: Record<string, string> = {
  BAN: 'badge-ban', TEMPBAN: 'badge-ban', IPBAN: 'badge-ban',
  MUTE: 'badge-mute', TEMPMUTE: 'badge-mute',
  WARN: 'badge-warn', KICK: 'badge-kick',
}

export default function Punishments() {
  const [rows, setRows] = useState<PunishmentRow[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [q, setQ] = useState('')
  const [type, setType] = useState('')
  const [active, setActive] = useState('')
  const [days, setDays] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    const filters: BrowseFilters = {
      page,
      size: PAGE_SIZE,
      q: q || undefined,
      type: type || undefined,
      active: active === '' ? undefined : active === 'true',
      days: days ? Number(days) : undefined,
    }
    punishmentsApi
      .browse(filters)
      .then((res) => {
        setRows(res.rows)
        setTotal(res.total)
        setError(null)
      })
      .catch(() => setError('Could not reach Core'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, q, type, active, days])

  const from = total === 0 ? 0 : page * PAGE_SIZE + 1
  const to = Math.min(total, (page + 1) * PAGE_SIZE)

  return (
    <div>
      <div className="page-header">
        <h1>
          Punishments <span className="count-pill">{total.toLocaleString()} total</span>
        </h1>
      </div>

      <div className="filter-bar">
        <input
          className="search"
          type="text"
          placeholder="Search player or reason..."
          value={q}
          onChange={(e) => {
            setPage(0)
            setQ(e.target.value)
          }}
        />
        <select
          value={type}
          onChange={(e) => {
            setPage(0)
            setType(e.target.value)
          }}
        >
          <option value="">All types</option>
          {TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
        <select
          value={active}
          onChange={(e) => {
            setPage(0)
            setActive(e.target.value)
          }}
        >
          <option value="">Active + expired</option>
          <option value="true">Active only</option>
          <option value="false">Expired only</option>
        </select>
        <select
          value={days}
          onChange={(e) => {
            setPage(0)
            setDays(e.target.value)
          }}
        >
          <option value="">All time</option>
          <option value="1">Last 24 hours</option>
          <option value="7">Last 7 days</option>
          <option value="30">Last 30 days</option>
        </select>
      </div>

      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}

      <div className="card" style={{ padding: 0 }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>Player</th>
              <th>Type</th>
              <th>Reason</th>
              <th>Staff</th>
              <th>Server</th>
              <th>Issued</th>
              <th>Expires</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {!loading && rows.length === 0 && (
              <tr>
                <td colSpan={8} className="empty-state">
                  No punishments match these filters.
                </td>
              </tr>
            )}
            {rows.map((r) => (
              <PunishmentRowView key={r.id} row={r} />
            ))}
          </tbody>
        </table>
      </div>

      <div className="pagination">
        <span>
          {loading ? 'Loading...' : total === 0 ? 'No results' : `Showing ${from}-${to} of ${total.toLocaleString()}`}
        </span>
        <div style={{ display: 'flex', gap: 8 }}>
          <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </button>
          <button disabled={to >= total} onClick={() => setPage((p) => p + 1)}>
            Next
          </button>
        </div>
      </div>
    </div>
  )
}

function PunishmentRowView({ row }: { row: PunishmentRow }) {
  const expired = !row.active
  const navigate = useNavigate()
  return (
    <tr onClick={() => navigate(`/punishments/${row.id}`)}>
      <td>
        <div className="player-cell">
          <PlayerHead uuid={row.playerUuid} size={24} />
          {row.playerName}
        </div>
      </td>
      <td>
        <span className={`badge ${BADGE_CLASS[row.type] ?? 'badge-kick'}`}>{row.type}</span>
      </td>
      <td className="muted">{row.reason}</td>
      <td className="mono">{row.staffUuid ? row.staffUuid.slice(0, 8) : 'Console'}</td>
      <td className="muted">{row.server ?? '-'}</td>
      <td className="muted">{new Date(row.issuedAt).toLocaleString()}</td>
      <td className="muted">{row.expiresAt ? new Date(row.expiresAt).toLocaleString() : 'Never'}</td>
      <td className={expired ? 'status-expired' : 'status-active'}>{expired ? 'Expired/Revoked' : 'Active'}</td>
    </tr>
  )
}
