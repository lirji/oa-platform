import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, onPermVersionChanged } from '@oa/shared/api/client'
import { isAnonymous } from '@oa/shared/perm/evaluate'
import type { Elevation, MyPermissions } from '@oa/shared/perm/types'
import { usePermStore } from '../store/permStore'

export const PERM_KEY = 'me-permissions'
export const ELEVATION_KEY = 'my-elevations'

async function fetchPermissions(): Promise<MyPermissions> {
  const { data } = await apiClient.get('/api/v1/me/permissions')
  return data.data
}

async function fetchElevations(): Promise<Elevation[]> {
  const { data } = await apiClient.get('/api/v1/iam/elevations/mine')
  return data.data ?? []
}

/**
 * 权限装载桥。挂在 Provider 里，`return null`。
 *
 * <p>"改角色后不刷新即生效"靠三条腿（ADR-C4），这里实现其中两条：
 * <ol>
 *   <li><b>响应头</b>：每个响应都带 `X-OA-Perm-Version`，与本地不同就 invalidate ——
 *       <b>零额外请求</b>，且覆盖长连断掉的场景；</li>
 *   <li><b>焦点/重连重取</b>：从后台标签页回来、断网恢复时自动拉一次。</li>
 * </ol>
 * 第三条（WebSocket 推送）依赖 JWT 模式下的握手方案，Phase 3 不做（ADR-C5）。
 */
export default function PermBridge() {
  const qc = useQueryClient()
  const applyPermissions = usePermStore((s) => s.applyPermissions)
  const setAnonymous = usePermStore((s) => s.setAnonymous)
  const setElevations = usePermStore((s) => s.setElevations)

  const perms = useQuery({
    queryKey: [PERM_KEY],
    queryFn: fetchPermissions,
    // 权限是全局前置，不该有 stale 窗口 —— 但也不轮询（万人 15s 轮询 = 667 QPS）。
    staleTime: 0,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    retry: 1,
  })

  const elevations = useQuery({
    queryKey: [ELEVATION_KEY],
    queryFn: fetchElevations,
    // 只有真的提过权才有意义；没有 elevatedCodes 时不必查
    enabled: Boolean(perms.data && (perms.data.elevatedCodes?.length ?? 0) > 0),
    staleTime: 30_000,
    retry: 0,
  })

  useEffect(() => {
    if (!perms.data) return
    // ★★★ 未认证守卫。
    // `/me/permissions` 是 @PublicApi，**未认证返回 200 + 空清单而不是 401** ——
    // token 过期那一瞬间拉到空清单，若直接写进 store，用户看到的是
    // "我被撤销了所有权限"：整站菜单消失、却不跳登录，且没有任何报错。
    if (isAnonymous(perms.data)) {
      setAnonymous()
      return
    }
    applyPermissions(perms.data)
  }, [perms.data, applyPermissions, setAnonymous])

  useEffect(() => {
    if (elevations.data) setElevations(elevations.data)
  }, [elevations.data, setElevations])

  useEffect(() => {
    // 响应头这条腿：用户在操作时本来就在发请求，比对版本号零额外开销。
    onPermVersionChanged((version) => {
      if (usePermStore.getState().version !== version) {
        qc.invalidateQueries({ queryKey: [PERM_KEY] })
        qc.invalidateQueries({ queryKey: [ELEVATION_KEY] })
      }
    })
  }, [qc])

  return null
}
