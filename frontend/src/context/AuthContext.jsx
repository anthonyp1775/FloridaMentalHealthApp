import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { authApi, tokenStore } from '../api'

const AuthContext = createContext(null)

const USER_KEY = 'fmh.user'

/** Who is signed in, for the whole app. */
export function AuthProvider({ children }) {
  /*
   * Read from localStorage on first render rather than in an effect, so
   * a refresh does not flash the login screen before restoring the
   * session.
   */
  const [user, setUser] = useState(() => {
    try {
      const raw = localStorage.getItem(USER_KEY)
      return tokenStore.get() && raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  })

  const persist = useCallback((auth) => {
    const next = {
      email: auth.email,
      fullName: auth.fullName,
      roles: auth.roles || []
    }
    tokenStore.set(auth.token)
    localStorage.setItem(USER_KEY, JSON.stringify(next))
    setUser(next)
    return next
  }, [])

  const login = useCallback(async (email, password) => {
    return persist(await authApi.login({ email, password }))
  }, [persist])

  const register = useCallback(async (body) => {
    return persist(await authApi.register(body))
  }, [persist])

  const logout = useCallback(() => {
    tokenStore.clear()
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }, [])

  const value = useMemo(() => ({
    user,
    isSignedIn: !!user,
    isAdmin: !!user?.roles?.includes('ROLE_ADMIN'),
    login,
    register,
    logout
  }), [user, login, register, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
