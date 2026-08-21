import { Button, Card, Dialog, Empty, List, Space, Toast } from 'antd-mobile'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, errorText } from '../api/client'
import type { ApiResult, Todo } from '../api/types'
import { canHandleTodo } from '../features/rules'
import { usePermissions } from '../permissions'
import { ErrorState, LoadingState, NoPermission } from '../components/PageState'

const dateText = (value?: string) => value ? new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : ''

export default function TodosPage() {
  const { perms, loading } = usePermissions(); const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['todos'], enabled: perms.has('oa:flow:todo:view'),
    queryFn: async () => (await api.get<ApiResult<Todo[]>>('/api/v1/flow/todos')).data.data,
  })
  const complete = useMutation({
    mutationFn: async ({ taskId, outcome }: { taskId: string; outcome: 'APPROVE' | 'REJECT' }) => {
      await api.post(`/api/v1/flow/todos/${encodeURIComponent(taskId)}/complete`, { outcome, comment: outcome === 'APPROVE' ? '移动端同意' : '移动端驳回' })
    },
    onSuccess: async () => { Toast.show('办理成功'); await queryClient.invalidateQueries({ queryKey: ['todos'] }); await queryClient.invalidateQueries({ queryKey: ['todo-count'] }) },
    onError: (error) => Toast.show({ content: errorText(error), icon: 'fail' }),
  })
  const decide = async (todo: Todo, outcome: 'APPROVE' | 'REJECT') => {
    const ok = await Dialog.confirm({ content: `确认${outcome === 'APPROVE' ? '同意' : '驳回'}“${todo.title}”吗？` })
    if (ok) complete.mutate({ taskId: todo.taskId, outcome })
  }
  if (loading) return <LoadingState />
  if (!perms.has('oa:flow:todo:view')) return <NoPermission />
  if (query.isLoading) return <LoadingState />
  if (query.error) return <ErrorState message={errorText(query.error)} retry={() => void query.refetch()} />
  if (!query.data?.length) return <Empty className="page-empty" description="当前没有待办" />
  return <div className="page-list" data-testid="todos-page"><List>
    {query.data.map((todo) => <List.Item key={todo.id} className="todo-item" data-task-id={todo.taskId} description={<>
      <div>{todo.applicantName ? `申请人：${todo.applicantName}` : todo.summary}</div><small>{dateText(todo.createdAt)}</small>
    </>}><Card className="flat-card"><strong>{todo.title}</strong>
      {canHandleTodo(todo, perms) && <Space block className="todo-actions">
        <Button size="small" onClick={() => void decide(todo, 'REJECT')} disabled={complete.isPending}>驳回</Button>
        <Button size="small" color="primary" onClick={() => void decide(todo, 'APPROVE')} loading={complete.isPending}>同意</Button>
      </Space>}
    </Card></List.Item>)}
  </List></div>
}
