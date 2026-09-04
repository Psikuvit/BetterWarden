import { postJson } from './client'
import type { PanelUser } from './auth'

async function status(): Promise<boolean> {
  const res = await fetch('/api/panel/setup/status', {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' },
  })
  if (!res.ok) throw new Error('Could not check setup status')
  const body = (await res.json()) as { needed: boolean }
  return body.needed
}

async function verify(setupCode: string): Promise<void> {
  await postJson<void>('/api/panel/setup/verify', { setupCode })
}

async function complete(setupCode: string, username: string, password: string): Promise<PanelUser> {
  return postJson<PanelUser>('/api/panel/setup/complete', { setupCode, username, password })
}

export const setupApi = { status, verify, complete }
