import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useBranding } from '../api/branding'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import './Login.css'

export default function Login() {
  const { login, setupNeeded } = useAuth()
  const branding = useBranding()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const from = (location.state as { from?: Location })?.from?.pathname ?? '/dash'

  if (setupNeeded) {
    return <Navigate to="/setup" replace />
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(username, password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? 'Invalid username or password' : 'Could not reach Core')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="login-page">
      <form className="card login-card" onSubmit={onSubmit}>
        {branding?.logoUrl && <img className="login-logo" src={branding.logoUrl} alt="" />}
        <h1>{branding?.serverName || 'BetterWarden'}</h1>
        <p className="muted">Staff panel login</p>

        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus required />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>

        {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Signing in...' : 'Sign in'}
        </button>

        <p className="muted" style={{ fontSize: '0.8rem', marginTop: 16 }}>
          Discord OAuth and Minecraft-account linking aren&apos;t built yet - email/password only.
        </p>
      </form>
    </div>
  )
}
