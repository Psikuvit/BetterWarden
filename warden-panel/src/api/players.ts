import { getJson, postJson } from './client'

export interface SearchResult {
  uuid: string
  name: string
  lastSeen: string
}

export interface PunishmentEntry {
  id: number
  type: string
  reason: string
  staffName: string
  issuedAt: string
  expiresAt: string | null
  active: boolean
}

export interface AltEntry {
  uuid: string
  name: string
}

export interface NoteEntry {
  id: number
  staffUuid: string
  body: string
  createdAt: string
}

export interface PlayerProfile {
  uuid: string
  name: string
  nameHistory: string[]
  firstSeen: string
  lastSeen: string
  punishments: PunishmentEntry[]
  alts: AltEntry[]
  notes: NoteEntry[]
  /** null when the viewer isn't ADMIN+ - see PlayerPanelController - not just an empty list. */
  ipHistory: string[] | null
}

export interface PunishRequest {
  type: string
  reason: string
  durationSeconds: number | null
  silent: boolean
}

export const playersApi = {
  search: (q: string) => getJson<SearchResult[]>(`/api/panel/players?q=${encodeURIComponent(q)}`),
  profile: (uuid: string) => getJson<PlayerProfile>(`/api/panel/players/${uuid}`),
  addNote: (uuid: string, body: string) => postJson<NoteEntry>(`/api/panel/players/${uuid}/notes`, { body }),
  punish: (uuid: string, req: PunishRequest) => postJson<PunishmentEntry>(`/api/panel/players/${uuid}/punish`, req),
}
