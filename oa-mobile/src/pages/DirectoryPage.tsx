import { Avatar, Card, Empty, List, SearchBar, Tag } from 'antd-mobile'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api, errorText } from '../api/client'
import type { ApiResult, DirectoryEntry } from '../api/types'
import { directoryScopeLabel } from '../features/rules'
import { usePermissions } from '../permissions'
import { ErrorState, LoadingState, NoPermission } from '../components/PageState'

export default function DirectoryPage() {
  const { perms, dataScope, loading } = usePermissions(); const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: ['directory', keyword], enabled: perms.has('oa:employee:view'),
    queryFn: async () => (await api.get<ApiResult<DirectoryEntry[]>>('/api/v1/org/directory', { params: { keyword: keyword || undefined, limit: 50 } })).data.data,
  })
  if (loading) return <LoadingState />
  if (!perms.has('oa:employee:view')) return <NoPermission />
  return <div className="page-content" data-testid="directory-page">
    <Card><SearchBar placeholder="搜索姓名、工号、手机号" value={keyword} onChange={setKeyword} clearable />
      <div className="scope-row"><Tag color="primary" fill="outline">{directoryScopeLabel(dataScope)}</Tag></div></Card>
    {query.isLoading ? <LoadingState /> : query.error ? <ErrorState message={errorText(query.error)} retry={() => void query.refetch()} /> : !query.data?.length ?
      <Empty className="page-empty" description="没有找到同事" /> : <List>
      {query.data.map((person) => <List.Item key={person.employeeId} prefix={<Avatar src="" fallback={person.name.slice(0, 1)} />} description={[person.orgName, person.positionName].filter(Boolean).join(' · ')}>
        <strong>{person.name}</strong><div className="contact-line">{person.mobile ?? person.email ?? '暂无联系方式'}</div>
      </List.Item>)}
    </List>}
  </div>
}
