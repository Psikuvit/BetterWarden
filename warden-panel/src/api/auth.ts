import { ApiError, postJson } from './client'

export interface PanelUser {
  username: string
  role: 'OWNER' | 'ADMIN' | 'MODERATOR' | 'VIEWER'
  mustChangePassword: boolean
}

async function me(): Promise<PanelUser | null> {
  const res = await fetch('/api/panel/auth/me', {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  if (res.status === 401) return null
  if (!res.ok) throw new ApiError(res.status, 'Could not check login status')
  return res.json() as Promise<PanelUser>
}

async function login(username: string, password: string): Promise<PanelUser> {
  return postJson<PanelUser>('/api/panel/auth/login', { username, password })
}

async function logout(): Promise<void> {
  await postJson<void>('/api/panel/auth/logout', {})
}

export const authApi = { me, login, logout }
