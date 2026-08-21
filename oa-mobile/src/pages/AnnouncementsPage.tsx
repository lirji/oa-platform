import { Button, Card, Empty, Tag, Toast } from 'antd-mobile'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, errorText } from '../api/client'
import type { Announcement, ApiResult } from '../api/types'
import { announcementNeedsRead } from '../features/rules'
import { usePermissions } from '../permissions'
import { ErrorState, LoadingState, NoPermission } from '../components/PageState'

export default function AnnouncementsPage() {
  const { perms, loading } = usePermissions(); const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['announcements'], enabled: perms.has('oa:announce:read'),
    queryFn: async () => (await api.get<ApiResult<Announcement[]>>('/api/v1/announcements', { params: { limit: 50 } })).data.data,
  })
  const read = useMutation({
    mutationFn: async (id: number) => api.post(`/api/v1/announcements/${id}/read`),
    onSuccess: async () => { Toast.show('已标记为已读'); await queryClient.invalidateQueries({ queryKey: ['announcements'] }) },
    onError: (error) => Toast.show({ content: errorText(error), icon: 'fail' }),
  })
  if (loading) return <LoadingState />
  if (!perms.has('oa:announce:read')) return <NoPermission />
  if (query.isLoading) return <LoadingState />
  if (query.error) return <ErrorState message={errorText(query.error)} retry={() => void query.refetch()} />
  if (!query.data?.length) return <Empty className="page-empty" description="暂时没有公告" />
  return <div className="page-content announcement-list" data-testid="announcements-page">
    {query.data.map((item) => <Card key={item.id} title={<span>{announcementNeedsRead(item) && <i className="unread-dot" />} {item.title}</span>}
      extra={<Tag color={item.readByMe ? 'default' : 'primary'}>{item.readByMe ? '已读' : '未读'}</Tag>}>
      <p className="announcement-content">{item.content}</p>
      <div className="card-meta"><span>{item.publisherName ?? '系统'} · {new Date(item.publishedAt).toLocaleDateString('zh-CN')}</span>
        {announcementNeedsRead(item) && <Button size="mini" color="primary" loading={read.isPending} onClick={() => read.mutate(item.id)}>标为已读</Button>}</div>
    </Card>)}
  </div>
}
