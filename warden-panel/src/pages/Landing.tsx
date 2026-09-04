import { Link } from 'react-router-dom'

// Public landing (spec: "/" - server name, logo, links). Server name/logo should come from
// branding config once /settings exists to set it - hardcoded placeholder for now.
export default function Landing() {
  return (
    <div className="landing">
      <h1>BetterWarden</h1>
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
