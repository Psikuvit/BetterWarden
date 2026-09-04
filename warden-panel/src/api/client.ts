// Same-origin in production (served by Core's own Tomcat); vite.config.ts proxies these
// paths to a local Core during `npm run dev`. No auth header yet - see PLAN.md Stage 5 auth.
export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { Accept: 'application/json', ...init?.headers },
    ...init,
  })
  if (!res.ok) {
    throw new ApiError(res.status, `${path} -> HTTP ${res.status}`)
  }
  return res.json() as Promise<T>
}

export interface HealthStatus {
  status: string
  time: string
  players: number
}

export const api = {
  health: () => request<HealthStatus>('/health'),
}
