import { getJson, postJson } from './client'

export interface AppealEntry {
  id: number
  playerUuid: string
  playerName: string
  punishmentId: number
  punishmentType: string
  punishmentReason: string
  message: string
  status: 'PENDING' | 'APPROVED' | 'DENIED' | 'MORE_INFO'
  reviewedBy: string | null
  reviewNote: string | null
  createdAt: string
}

export const appealsApi = {
  list: (status?: string) => getJson<AppealEntry[]>(`/api/panel/appeals${status ? `?status=${status}` : ''}`),
  approve: (id: number, note: string) => postJson<AppealEntry>(`/api/panel/appeals/${id}/approve`, { note }),
  deny: (id: number, note: string) => postJson<AppealEntry>(`/api/panel/appeals/${id}/deny`, { note }),
  moreInfo: (id: number, note: string) => postJson<AppealEntry>(`/api/panel/appeals/${id}/more-info`, { note }),
}
