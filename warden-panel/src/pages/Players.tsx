import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import PlayerHead from '../components/PlayerHead'
import { playersApi, type SearchResult } from '../api/players'
import './Panel.css'

export default function Players() {
  const [q, setQ] = useState('')
  const [results, setResults] = useState<SearchResult[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const handle = setTimeout(() => {
      if (!q.trim()) {
        setResults([])
        return
      }
      setLoading(true)
      playersApi
        .search(q)
        .then((res) => {
          setResults(res)
          setError(null)
        })
        .catch(() => setError('Could not reach Core'))
        .finally(() => setLoading(false))
    }, 250)
    return () => clearTimeout(handle)
  }, [q])

  return (
    <div>
      <div className="page-header">
        <h1>Players</h1>
      </div>
      <input
        className="search-box"
        type="text"
        placeholder="Search by name or UUID..."
        value={q}
        onChange={(e) => setQ(e.target.value)}
      />

      {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
      {!loading && q.trim() && results.length === 0 && <p className="muted">No players found.</p>}

      <div className="player-grid">
        {results.map((p) => (
          <Link key={p.uuid} to={`/players/${p.uuid}`} className="card player-card">
            <PlayerHead uuid={p.uuid} size={40} />
            <div>
              <div className="player-card-name">{p.name}</div>
              <div className="player-card-meta">Last seen {new Date(p.lastSeen).toLocaleString()}</div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
