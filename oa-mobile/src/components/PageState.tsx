import { Button, DotLoading, ErrorBlock } from 'antd-mobile'

export function LoadingState() {
  return <div className="page-state" aria-label="加载中"><DotLoading color="primary" /></div>
}

export function ErrorState({ message, retry }: { message: string; retry: () => void }) {
  return <div className="page-state"><ErrorBlock status="default" title="加载失败" description={message} />
    <Button size="small" color="primary" onClick={retry}>重新加载</Button></div>
}

export function NoPermission({ description = '当前账号没有访问此功能的权限' }: { description?: string }) {
  return <div className="page-state"><ErrorBlock status="empty" title="暂无权限" description={description} /></div>
}
