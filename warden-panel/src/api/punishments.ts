import { getJson, postJson } from './client'

export interface PunishmentRow {
  id: number
  playerUuid: string
  playerName: string
  type: string
  reason: string
  staffUuid: string | null
  server: string | null
  issuedAt: string
  expiresAt: string | null
  active: boolean
}

export interface BrowseResponse {
  rows: PunishmentRow[]
  total: number
  page: number
  size: number
}

export interface RevokeEvent {
  staffUuid: string
  reason: string
  revokedAt: string
}

export interface PunishmentDetail {
  id: number
  playerUuid: string
  playerName: string
  type: string
  reason: string
  staffUuid: string | null
  staffName: string
  server: string | null
  issuedAt: string
  expiresAt: string | null
  active: boolean
  silent: boolean
  permanent: boolean
  ipHash: string | null
  revoke: RevokeEvent | null
}

export interface BrowseFilters {
  type?: string
  staff?: string
  server?: string
  active?: boolean
  days?: number
  q?: string
  page?: number
  size?: number
}

function toQuery(filters: BrowseFilters): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

export const punishmentsApi = {
  browse: (filters: BrowseFilters) => getJson<BrowseResponse>(`/api/panel/punishments${toQuery(filters)}`),
  detail: (id: number | string) => getJson<PunishmentDetail>(`/api/panel/punishments/${id}`),
  revoke: (id: number | string, reason: string) => postJson<void>(`/api/panel/punishments/${id}/revoke`, { reason }),
}
