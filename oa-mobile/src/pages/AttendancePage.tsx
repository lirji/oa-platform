import { Button, Card, List, Space, Toast } from 'antd-mobile'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, errorText } from '../api/client'
import type { ApiResult, PunchResult, PunchRow } from '../api/types'
import { punchParams } from '../features/rules'
import { usePermissions } from '../permissions'
import { ErrorState, LoadingState, NoPermission } from '../components/PageState'

function locate(): Promise<GeolocationCoordinates | undefined> {
  if (!navigator.geolocation) return Promise.resolve(undefined)
  return new Promise((resolve) => navigator.geolocation.getCurrentPosition((p) => resolve(p.coords), () => resolve(undefined), { enableHighAccuracy: true, timeout: 5_000, maximumAge: 30_000 }))
}

export default function AttendancePage() {
  const { perms, loading } = usePermissions(); const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['attendance', 'today'], enabled: perms.has('oa:attendance:view'),
    queryFn: async () => (await api.get<ApiResult<PunchRow[]>>('/api/v1/attendance/me')).data.data,
  })
  const punch = useMutation({
    mutationFn: async (type: 'IN' | 'OUT') => {
      const coords = await locate()
      return (await api.post<ApiResult<PunchResult>>('/api/v1/attendance/punch', null, { params: punchParams(type, coords) })).data.data
    },
    onSuccess: async (result) => { Toast.show(result.duplicate ? '本次打卡已记录' : (result.message || '打卡成功')); await queryClient.invalidateQueries({ queryKey: ['attendance'] }) },
    onError: (error) => Toast.show({ content: errorText(error), icon: 'fail' }),
  })
  if (loading) return <LoadingState />
  if (!perms.has('oa:attendance:view') && !perms.has('oa:attendance:punch')) return <NoPermission />
  return <div className="page-content" data-testid="attendance-page">
    <Card title="今日打卡" className="hero-card"><p className="today-date">{new Intl.DateTimeFormat('zh-CN', { dateStyle: 'full' }).format(new Date())}</p>
      {perms.has('oa:attendance:punch') ? <Space block justify="center" className="punch-actions">
        <Button color="primary" shape="rounded" loading={punch.isPending} onClick={() => punch.mutate('IN')}>上班打卡</Button>
        <Button color="success" shape="rounded" loading={punch.isPending} onClick={() => punch.mutate('OUT')}>下班打卡</Button>
      </Space> : <NoPermission description="当前账号没有打卡权限" />}
      <p className="location-note">会尝试记录当前位置；定位不可用时仍可打卡。</p>
    </Card>
    {perms.has('oa:attendance:view') && <Card title="今日记录">
      {query.isLoading ? <LoadingState /> : query.error ? <ErrorState message={errorText(query.error)} retry={() => void query.refetch()} /> :
        <List>{query.data?.length ? query.data.map((row, index) => <List.Item key={index} extra={row.source ?? 'MOBILE'}>
          {row.punchType ?? row.type ?? '打卡'} <small>{row.punchTime ?? row.time ?? ''}</small></List.Item>) : <List.Item>还没有打卡记录</List.Item>}</List>}
    </Card>}
  </div>
}
