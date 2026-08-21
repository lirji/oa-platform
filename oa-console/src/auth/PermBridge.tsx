import { useEffect, useRef } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from 'react-oidc-context'
import { apiClient, onPermVersionChanged } from '@oa/shared/api/client'
import { openNotifySocket, socketPermVersion } from '@oa/shared/api/notifySocket'
import { config } from '@oa/shared/config'
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
  const auth = useAuth()
  const qc = useQueryClient()
  /** 已经据此失效过的权限版本。防止"重取的响应再次触发失效"形成死循环。 */
  const lastHandled = useRef(0)
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
    // JWT 未登录时不能抢在 LoginPage 前发请求并触发 401→silent renew→重复重定向。
    enabled: !config.authEnabled || auth.isAuthenticated,
  })

  const elevations = useQuery({
    queryKey: [ELEVATION_KEY],
    queryFn: fetchElevations,
    // 只有真的提过权才有意义；没有 elevatedCodes 时不必查
    enabled: Boolean((!config.authEnabled || auth.isAuthenticated)
      && perms.data && (perms.data.elevatedCodes?.length ?? 0) > 0),
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
      // ★★★ 同一个版本只反应一次。
      //
      // 只跟 store 比对会形成**自持的请求风暴**：失效 → 重取 → 重取的响应
      // 自己也带这个头 → 而 store 要等 React 把 effect 刷完才更新，慢一拍 →
      // 于是每个响应都再触发一次失效。实测一次焦点事件能在 2 秒内打出
      // **840 个 `/me/permissions`**（约 420 QPS，单个标签页）——
      // 而这个项目当初正是算了"万人 / 15 秒 = 667 QPS"才决定不轮询的。
      //
      // 它不报错、界面也不白屏，只是所有请求都在排队后面 ——
      // 表现是"页面越用越卡"，最不像 bug 的一种 bug。
      if (version === lastHandled.current) return
      if (usePermStore.getState().version === version) return
      lastHandled.current = version
      qc.invalidateQueries({ queryKey: [PERM_KEY] })
      qc.invalidateQueries({ queryKey: [ELEVATION_KEY] })
    })
  }, [qc])

  const socketUserId = perms.data?.userId
  useEffect(() => {
    if (!config.authEnabled || !socketUserId) return

    let stopped = false
    let socket: WebSocket | null = null
    let retryTimer: ReturnType<typeof setTimeout> | null = null
    let retryMs = 1_000

    const connect = async () => {
      try {
        socket = await openNotifySocket()
        if (stopped) {
          socket.close(1000, 'component disposed')
          return
        }
        retryMs = 1_000
        socket.onmessage = (event) => {
          const version = socketPermVersion(String(event.data))
          if (version == null || version === lastHandled.current) return
          lastHandled.current = version
          qc.invalidateQueries({ queryKey: [PERM_KEY] })
          qc.invalidateQueries({ queryKey: [ELEVATION_KEY] })
        }
        socket.onclose = () => {
          if (stopped) return
          retryTimer = setTimeout(() => void connect(), retryMs)
          retryMs = Math.min(retryMs * 2, 30_000)
        }
      } catch {
        if (stopped) return
        retryTimer = setTimeout(() => void connect(), retryMs)
        retryMs = Math.min(retryMs * 2, 30_000)
      }
    }

    void connect()
    return () => {
      stopped = true
      if (retryTimer) clearTimeout(retryTimer)
      socket?.close(1000, 'identity changed')
    }
  }, [qc, socketUserId])

  return null
}
