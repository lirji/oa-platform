import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'

export default function ForbiddenPage() {
  const nav = useNavigate()
  return (
    <Result
      status="403"
      title="无访问权限"
      subTitle="你没有访问该页面的权限。若认为这是配置问题，请联系管理员。"
      extra={<Button type="primary" onClick={() => nav('/workbench')}>回工作台</Button>}
    />
  )
}
