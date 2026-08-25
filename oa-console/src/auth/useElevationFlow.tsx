import { useCallback, useState } from 'react'
import { App, Alert, Form, InputNumber, Input, Modal, Select, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@oa/shared/api/client'
import { errText, type NormalizedError } from '@oa/shared/api/errors'
import { usePerm, usePermVersion } from './usePerm'

interface Role { id: number; code: string; name: string }

/**
 * JIT 提权闭环：收到 3002 → 弹窗 → 提交四眼审批申请。
 *
 * <p>★ 为什么必须独立成一套而不是当普通 403：后端的 message 是
 * "该操作需要临时提权，请先申请：oa:audit:view"。若全站 403 都渲染成"无权限"，
 * 用户**永远找不到申请入口**，而且会以为是权限配错了去找管理员加权限 ——
 * 加了还是不行，因为 elevation 是另一条路径。
 *
 * <p>★ 提权对话框自身可能 403：`/iam/roles/mine` 与 `POST /iam/elevations` 都要
 * `oa:iam:elevate`。没有该权限的人打开弹窗会立刻再吃一个 403 ——
 * "申请入口存在但一点就报错"比不给入口更糟。所以先判，再决定弹什么。
 */
export function useElevationFlow() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const perm = usePerm()
  const permVersion = usePermVersion()
  const [open, setOpen] = useState(false)
  const [pending, setPending] = useState<NormalizedError | null>(null)
  const [form] = Form.useForm()

  const canElevate = perm.has('oa:iam:elevate')

  const roles = useQuery({
    queryKey: ['my-roles', permVersion],
    queryFn: async () => (await apiClient.get('/api/v1/iam/roles/mine')).data.data as Role[],
    enabled: open && canElevate,
    staleTime: 60_000,
  })

  const submit = useMutation({
    mutationFn: async (v: { roleId: number; reason: string; hours: number }) =>
      apiClient.post('/api/v1/iam/elevations', v),
    onSuccess: async () => {
      message.success('提权申请已提交，需由另一名权限管理员批准后才会生效')
      setOpen(false)
      // 申请阶段不会产生权限，不能立即重放原请求；批准后缓存失效通知会刷新权限。
      await qc.invalidateQueries({ queryKey: ['my-elevation-requests'] })
    },
    onError: (e) => message.error(errText(e)),
  })

  /** 业务侧在 catch 到 needElevation 时调它。可传入"重放函数"。 */
  const request = useCallback((err: NormalizedError, _replay?: () => void) => {
    setPending(err)
    if (!canElevate) {
      // 不弹选择器，直接说清该找谁 —— 弹一个必然 403 的表单毫无意义
      message.warning(
        `该操作需要临时提权（${err.requiredPerms?.join('、') ?? '未知权限'}），` +
        '但你没有自助提权的权限，请联系管理员',
      )
      return
    }
    setOpen(true)
  }, [canElevate, message])

  const dialog = (
    <Modal
      title="申请临时提权"
      open={open}
      onCancel={() => setOpen(false)}
      confirmLoading={submit.isPending}
      okText="申请"
      destroyOnClose
      onOk={async () => {
        const v = await form.validateFields().catch(() => null)
        if (v) submit.mutate(v)
      }}
    >
      <Alert
        type="info" showIcon style={{ marginBottom: 16 }}
        message={`该操作需要：${pending?.requiredPerms?.join('、') ?? '临时提权'}`}
        description="提权只能激活你【本来就持有】的角色，并且有时限。提交后须由另一名权限管理员批准，整个过程会被审计记录。"
      />
      <Form form={form} layout="vertical" initialValues={{ hours: 1 }}>
        <Form.Item name="roleId" label="提权到" rules={[{ required: true, message: '请选择角色' }]}>
          <Select
            loading={roles.isLoading}
            placeholder={roles.data?.length ? '选择一个你已持有的角色' : '你当前没有可提权的角色'}
            options={(roles.data ?? []).map((r) => ({ value: r.id, label: `${r.name}（${r.code}）` }))}
          />
        </Form.Item>
        <Form.Item name="hours" label="时长（小时）" rules={[{ required: true }]}>
          <InputNumber min={1} max={8} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="reason" label="事由" rules={[{ required: true, message: '提权必须说明事由' }]}>
          <Input.TextArea rows={3} maxLength={200} showCount placeholder="例如：排查线上权限问题" />
        </Form.Item>
      </Form>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        批准后权限会自动刷新；到期后自动失效，无需手动撤销。
      </Typography.Text>
    </Modal>
  )

  return { request, dialog, canElevate }
}
