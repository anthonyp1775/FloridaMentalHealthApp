import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import CrisisBanner from './CrisisBanner'
import { Button } from './ui'

/**
 * The frame around every signed-in screen.
 *
 * CrisisBanner lives here rather than in each page, so there is no route
 * on which it can be forgotten.
 */
export default function Layout() {
  const { user, isAdmin, logout } = useAuth()
  const { theme, toggle } = useTheme()
  const navigate = useNavigate()

  const signOut = () => {
    logout()
    navigate('/login')
  }

  const linkClass = ({ isActive }) => (isActive ? 'is-active' : undefined)

  return (
    <div className="shell">
      <CrisisBanner />

      <header className="masthead">
        <div className="masthead__inner">
          <Link to="/" className="masthead__mark">Find Care Florida</Link>

          <nav className="nav" aria-label="Main">
            <NavLink to="/" end className={linkClass}>Search</NavLink>
            <NavLink to="/saved" className={linkClass}>Saved</NavLink>
            <NavLink to="/referrals" className={linkClass}>My requests</NavLink>
            <NavLink to="/profile" className={linkClass}>Profile</NavLink>

            {isAdmin && (
              <>
                <NavLink to="/admin/queue" className={linkClass}>Queue</NavLink>
                <NavLink to="/admin/providers" className={linkClass}>Directory</NavLink>
                <NavLink to="/admin/reports" className={linkClass}>Reports</NavLink>
              </>
            )}
          </nav>

          <div className="masthead__who">
            <span>{user?.fullName}</span>
            <Button
              variant="quiet"
              size="small"
              onClick={toggle}
              aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
            >
              {theme === 'dark' ? 'Light' : 'Dark'}
            </Button>
            <Button variant="quiet" size="small" onClick={signOut}>Sign out</Button>
          </div>
        </div>
      </header>

      <Outlet />
    </div>
  )
}
