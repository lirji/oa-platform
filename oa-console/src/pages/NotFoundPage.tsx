import { Button, Result } from 'antd'
import { useNavigate } from 'react-router-dom'

export default function NotFoundPage() {
  const nav = useNavigate()
  return (
    <Result
      status="404"
      title="页面不存在"
      subTitle="没有对应的控制台路由。请从左侧菜单进入，或回工作台。"
      extra={<Button type="primary" onClick={() => nav('/workbench')}>回工作台</Button>}
    />
  )
}
