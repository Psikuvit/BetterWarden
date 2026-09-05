import { Link } from 'react-router-dom'
import { useBranding } from '../api/branding'

// Public landing (spec: "/" - server name, logo, links).
export default function Landing() {
  const branding = useBranding()

  return (
    <div className="landing">
      {branding?.logoUrl && (
        <img
          src={branding.logoUrl}
          alt=""
          style={{ width: 64, height: 64, borderRadius: 12, objectFit: 'cover', marginBottom: 16 }}
        />
      )}
      <h1>{branding?.serverName || 'BetterWarden'}</h1>
      <p className="muted">Moderation &amp; staff-ops for this network.</p>
      <nav className="landing-links">
        <Link to="/dash">Staff panel</Link>
      </nav>
      <p className="muted" style={{ marginTop: 40, fontSize: '0.85rem' }}>
        Public pages (/bans, /player/[name], /appeal/[token], /staff) aren&apos;t built yet.
      </p>
    </div>
  )
}
