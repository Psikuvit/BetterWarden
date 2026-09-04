import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { authApi, type PanelUser } from '../api/auth'
import { setupApi } from '../api/setup'

interface AuthState {
  user: PanelUser | null
  loading: boolean
  setupNeeded: boolean
  login: (username: string, password: string) => Promise<void>
  completeSetup: (setupCode: string, username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<PanelUser | null>(null)
  const [setupNeeded, setSetupNeeded] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([setupApi.status(), authApi.me()])
      .then(([needed, currentUser]) => {
        setSetupNeeded(needed)
        setUser(currentUser)
      })
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const loggedInUser = await authApi.login(username, password)
    setUser(loggedInUser)
  }, [])

  const completeSetup = useCallback(async (setupCode: string, username: string, password: string) => {
    const owner = await setupApi.complete(setupCode, username, password)
    setSetupNeeded(false)
    setUser(owner)
  }, [])

  const logout = useCallback(async () => {
    await authApi.logout()
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, loading, setupNeeded, login, completeSetup, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
