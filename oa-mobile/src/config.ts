const trim = (value: string) => value.replace(/\/+$/, '')
export const config = {
  apiBaseUrl: trim(import.meta.env.VITE_API_BASE_URL ?? ''),
  authEnabled: (import.meta.env.VITE_AUTH_ENABLED ?? 'false') === 'true',
  devUser: import.meta.env.VITE_DEV_USER ?? 'seed-user-1',
  casdoorAuthority: trim(import.meta.env.VITE_CASDOOR_AUTHORITY ?? 'http://localhost:8000'),
  casdoorClientId: import.meta.env.VITE_CASDOOR_CLIENT_ID ?? '',
  oidcScope: import.meta.env.VITE_OIDC_SCOPE ?? 'openid profile email offline_access',
} as const

export function currentDevUser(): string {
  if (config.authEnabled) return ''
  try { return localStorage.getItem('oa.devUser') ?? config.devUser } catch { return config.devUser }
}
