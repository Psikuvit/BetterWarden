import { csrfHeader, ensureCsrfCookie } from './csrf'

// Same-origin in production (served by Core's own Tomcat); vite.config.ts proxies these
// paths to a local Core during `npm run dev`.
export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    credentials: 'same-origin',
    headers: { Accept: 'application/json', ...init?.headers },
    ...init,
  })
  if (!res.ok) {
    throw new ApiError(res.status, `${path} -> HTTP ${res.status}`)
  }
  if (res.status === 204) {
    return undefined as T
  }
  return res.json() as Promise<T>
}

/** POST/PUT/DELETE to a session-cookie-protected endpoint (anything under /api/panel/** except login/csrf) - CSRF-exempt endpoints like /api/v1/** don't need this. */
export async function postJson<T>(path: string, body: unknown): Promise<T> {
  await ensureCsrfCookie()
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...csrfHeader() },
    body: JSON.stringify(body),
  })
}
