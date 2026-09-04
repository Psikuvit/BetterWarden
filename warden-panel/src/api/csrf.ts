// Spring Security's CookieCsrfTokenRepository puts the token in a readable (non-HttpOnly)
// XSRF-TOKEN cookie; it expects the same value echoed back as the X-XSRF-TOKEN header on every
// state-changing request. GETting /api/panel/auth/csrf (permitAll) is what makes Spring Security
// issue that cookie in the first place - call ensureCsrfCookie() before the first POST.
const COOKIE_NAME = 'XSRF-TOKEN'
const HEADER_NAME = 'X-XSRF-TOKEN'

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[1]) : null
}

export async function ensureCsrfCookie(): Promise<void> {
  if (readCookie(COOKIE_NAME)) return
  await fetch('/api/panel/auth/csrf', { credentials: 'same-origin' })
}

export function csrfHeader(): Record<string, string> {
  const token = readCookie(COOKIE_NAME)
  return token ? { [HEADER_NAME]: token } : {}
}
