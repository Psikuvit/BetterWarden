import { getJson, postJson } from './client'

export interface TicketEntry {
  id: number
  subject: string
  openerUuid: string
  openerName: string
  status: 'OPEN' | 'IN_PROGRESS' | 'CLOSED'
  priority: 'LOW' | 'NORMAL' | 'HIGH'
  assignee: string | null
  category: string | null
  source: 'GAME' | 'DISCORD' | 'WEB'
  createdAt: string
}

export interface TicketMessage {
  id: number
  author: string
  authorName: string
  source: 'GAME' | 'DISCORD' | 'WEB'
  body: string
  internal: boolean
  sentAt: string
}

export interface TicketThread {
  ticket: TicketEntry
  messages: TicketMessage[]
}

export const ticketsApi = {
  list: (status?: string) => getJson<TicketEntry[]>(`/api/panel/tickets${status ? `?status=${status}` : ''}`),
  thread: (id: number | string) => getJson<TicketThread>(`/api/panel/tickets/${id}`),
  reply: (id: number | string, body: string, internal: boolean) =>
    postJson<TicketMessage>(`/api/panel/tickets/${id}/reply`, { body, internal }),
  close: (id: number | string) => postJson<TicketEntry>(`/api/panel/tickets/${id}/close`, {}),
}
