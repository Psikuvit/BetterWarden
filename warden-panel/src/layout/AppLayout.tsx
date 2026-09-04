import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import './AppLayout.css'

// Matches docs/spec/04-PANEL.txt §1's private route list. Routes not built yet still show in
// the nav (going to ComingSoon) so the shape of the whole panel is visible from day one, not
// just whatever happens to be finished - same "don't fake completeness, don't hide scope either"
// approach as the rest of this project.
const NAV_ITEMS = [
  { to: '/dash', label: 'Dashboard' },
  { to: '/punishments', label: 'Punishments' },
  { to: '/players', label: 'Players' },
  { to: '/reports', label: 'Reports' },
  { to: '/tickets', label: 'Tickets' },
  { to: '/appeals', label: 'Appeals' },
  { to: '/staff-stats', label: 'Staff Stats' },
  { to: '/nodes', label: 'Nodes' },
  { to: '/settings', label: 'Settings' },
  { to: '/audit', label: 'Audit Log' },
]

export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function onLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="app-brand">BetterWarden</div>
        <nav>
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="app-user">
          <div>
            <div className="app-user-name">{user?.username}</div>
            <div className="app-user-role muted">{user?.role}</div>
          </div>
          <button className="logout-button" onClick={onLogout}>
            Log out
          </button>
        </div>
      </aside>
      <div className="app-main">
        <main className="app-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
