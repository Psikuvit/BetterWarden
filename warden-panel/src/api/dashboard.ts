async function fetchJson<T>(path: string): Promise<T> {
  const res = await fetch(path, { credentials: 'same-origin', headers: { Accept: 'application/json' } })
  if (!res.ok) throw new Error(`${path} -> HTTP ${res.status}`)
  return res.json() as Promise<T>
}

export interface RecentAction {
  id: number
  actor: string
  actorType: string
  action: string
  target: string | null
  at: string
}

export interface DailyCount {
  date: string
  count: number
}

export interface DashboardData {
  onlinePlayers: number
  openReports: number
  openTickets: number
  activeBans: number
  activeMutes: number
  pendingAppeals: number
  connectedNodes: number
  recentActions: RecentAction[]
  punishmentActivity: DailyCount[]
}

export const dashboardApi = {
  get: () => fetchJson<DashboardData>('/api/panel/dashboard'),
}
