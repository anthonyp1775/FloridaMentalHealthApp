import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * Gate for routes that need a signed-in user.
 *
 * This is convenience, not security - the API enforces authorization on
 * every request regardless of what the browser renders. Hiding a route
 * only keeps someone from seeing a screen that would fail anyway.
 */
export default function ProtectedRoute({ requireAdmin = false, children }) {
  const { isSignedIn, isAdmin } = useAuth()
  const location = useLocation()

  if (!isSignedIn) {
    // Remember where they were headed so login can return them to it.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (requireAdmin && !isAdmin) {
    return <Navigate to="/" replace />
  }

  return children
}
