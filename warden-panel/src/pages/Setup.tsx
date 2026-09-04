import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { setupApi } from '../api/setup'
import { useAuth } from '../auth/AuthContext'
import './Login.css'

// docs/spec/04-PANEL.txt §2 - MVP steps 1-2 only (setup code, owner account). Steps 3-6
// (Minecraft-account linking, Discord bot token, LiteBans import preview, Cloudflare Tunnel)
// aren't built - the finish screen says so rather than faking them.
type Step = 'code' | 'account' | 'done'

export default function Setup() {
  const { setupNeeded, completeSetup } = useAuth()
  const navigate = useNavigate()

  const [step, setStep] = useState<Step>('code')
  const [code, setCode] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (!setupNeeded) {
    return <Navigate to="/login" replace />
  }

  async function onVerifyCode(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await setupApi.verify(code)
      setStep('account')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reach Core')
    } finally {
      setSubmitting(false)
    }
  }

  async function onCreateAccount(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (password !== confirm) {
      setError('Passwords do not match')
      return
    }
    setSubmitting(true)
    try {
      await completeSetup(code, username, password)
      setStep('done')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reach Core')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="login-page">
      <div className="card login-card">
        <h1>BetterWarden setup</h1>
        <p className="muted">Step {step === 'code' ? '1' : step === 'account' ? '2' : '2'} of 2</p>

        {step === 'code' && (
          <form onSubmit={onVerifyCode} style={{ display: 'contents' }}>
            <p className="muted" style={{ fontSize: '0.85rem' }}>
              Enter the setup code printed in this server&apos;s console log on startup.
            </p>
            <label>
              Setup code
              <input
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="XXXX-XXXX"
                autoFocus
                required
              />
            </label>
            {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
            <button type="submit" disabled={submitting}>
              {submitting ? 'Checking...' : 'Continue'}
            </button>
          </form>
        )}

        {step === 'account' && (
          <form onSubmit={onCreateAccount} style={{ display: 'contents' }}>
            <p className="muted" style={{ fontSize: '0.85rem' }}>
              Create your owner account. This has full access to everything.
            </p>
            <label>
              Username
              <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus required />
            </label>
            <label>
              Password
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                minLength={8}
                required
              />
            </label>
            <label>
              Confirm password
              <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
            </label>
            {error && <p style={{ color: 'var(--danger)' }}>{error}</p>}
            <button type="submit" disabled={submitting}>
              {submitting ? 'Creating account...' : 'Finish setup'}
            </button>
          </form>
        )}

        {step === 'done' && (
          <div>
            <p>Owner account created. You&apos;re logged in.</p>
            <p className="muted" style={{ fontSize: '0.85rem' }}>
              Minecraft-account linking, Discord bot setup, LiteBans import, and Cloudflare
              Tunnel aren&apos;t available yet - you can go straight to the dashboard.
            </p>
            <button type="button" onClick={() => navigate('/dash', { replace: true })}>
              Go to dashboard
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
