import { createContext, useContext, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from './api/client'
import type { ApiResult, MyPermissions } from './api/types'
import { config } from './config'
import { useAuth } from 'react-oidc-context'

interface PermissionState {
  userId: string | null
  username: string | null
  dataScope: string
  perms: ReadonlySet<string>
  loading: boolean
  error: Error | null
  reload: () => void
}

const PermissionContext = createContext<PermissionState | null>(null)

export function PermissionProvider({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const ready = !config.authEnabled || auth.isAuthenticated
  const query = useQuery({
    queryKey: ['me', 'permissions'],
    enabled: ready,
    staleTime: 60_000,
    queryFn: async () => (await api.get<ApiResult<MyPermissions>>('/api/v1/me/permissions')).data.data,
  })
  const value = query.data
  return <PermissionContext.Provider value={{
    userId: value?.userId ?? null,
    username: value?.username ?? null,
    dataScope: value?.dataScope ?? 'SELF',
    perms: new Set(value?.permCodes ?? []),
    loading: query.isLoading,
    error: query.error,
    reload: () => { void query.refetch() },
  }}>{children}</PermissionContext.Provider>
}

export function usePermissions() {
  const value = useContext(PermissionContext)
  if (!value) throw new Error('usePermissions must be used inside PermissionProvider')
  return value
}
