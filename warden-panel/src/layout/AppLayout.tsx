import { NavLink, Outlet } from 'react-router-dom'
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
      </aside>
      <div className="app-main">
        <div className="no-auth-banner">
          No login yet - this panel has no authentication. Anyone who can reach this port can
          see and use everything below. See PLAN.md Stage 5 auth.
        </div>
        <main className="app-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
