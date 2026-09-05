import { useEffect, useState } from 'react'
import { getJson } from './client'

export interface Branding {
  serverName: string
  logoUrl: string
  accentColor: string
}

const brandingApi = {
  get: () => getJson<Branding>('/api/panel/branding'),
}

/** Fetches once, applies the accent colour to the whole document (spec §5: "single accent colour driven by a config value"), and hands back the branding for the logo/server-name UI. Public endpoint - safe to call before login (Login/Landing pages need it too). */
export function useBranding(): Branding | null {
  const [branding, setBranding] = useState<Branding | null>(null)

  useEffect(() => {
    brandingApi
      .get()
      .then((b) => {
        setBranding(b)
        if (b.accentColor) {
          document.documentElement.style.setProperty('--accent', b.accentColor)
        }
      })
      .catch(() => {
        // Branding is cosmetic - a Core that's unreachable here will fail loudly elsewhere
        // (Dashboard, etc.); just fall back to the built-in name/colour silently.
      })
  }, [])

  return branding
}
