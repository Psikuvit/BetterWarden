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
    // Backend error responses are {"error": "..."} (see e.g. SetupController, PanelAuthController) -
    // surface that when present instead of just the HTTP status.
    let message = `${path} -> HTTP ${res.status}`
    try {
      const body = (await res.clone().json()) as { error?: string }
      if (body.error) message = body.error
    } catch {
      // not JSON, or empty body - keep the generic message
    }
    throw new ApiError(res.status, message)
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

/** GET from a panel endpoint - no CSRF needed, reads are exempt by Spring Security's own default. */
export function getJson<T>(path: string): Promise<T> {
  return request<T>(path)
}
