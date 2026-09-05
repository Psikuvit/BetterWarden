import { getJson, postJson } from './client'

export interface ReportEntry {
  id: number
  reporterUuid: string
  reporterName: string
  targetUuid: string
  targetName: string
  reason: string
  category: string | null
  status: 'OPEN' | 'CLAIMED' | 'DISMISSED' | 'CLOSED'
  claimedBy: string | null
  chatSnapshot: string | null
  location: string | null
  server: string | null
  createdAt: string
}

export const reportsApi = {
  list: (status?: string) => getJson<ReportEntry[]>(`/api/panel/reports${status ? `?status=${status}` : ''}`),
  claim: (id: number) => postJson<ReportEntry>(`/api/panel/reports/${id}/claim`, {}),
  dismiss: (id: number) => postJson<ReportEntry>(`/api/panel/reports/${id}/dismiss`, {}),
}
