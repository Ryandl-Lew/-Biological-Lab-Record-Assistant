import { lazy, Suspense, useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Archive, ArrowLeft, CalendarDays, CheckCircle2, CircleUserRound, FileCheck2, FolderPlus, History, Mail, Paperclip, Plus, UserPlus, Users } from 'lucide-react'
import {
  archiveProject,
  fetchProject,
  fetchProjectAttachments,
  fetchProjectAuditEvents,
  fetchProjectMembers,
  fetchRecords,
  inviteProjectMember,
  removeProjectMember,
  updateProjectMemberRole,
} from '@/api'
import { Badge, Button, EmptyState, StatusBadge, Surface, Tabs } from '@/components/ui'
import { PROJECT_ROLE_LABELS, PROJECT_ROLE_TONES } from '@/domain'

const ProgressReportPanel = lazy(() => import('@/components/agent/ProgressReportPanel'))
import TimelineGraph from '@/components/timeline/TimelineGraph.jsx'

const EVENT_LABELS = {
  PROJECT_CREATED: '创建了项目',
  PROJECT_ARCHIVED: '归档了项目',
  INVITATION_CREATED: '发出了项目邀请',
  INVITATION_ACCEPTED: '接受了项目邀请',
  INVITATION_REJECTED: '拒绝了项目邀请',
  INVITATION_EXPIRED: '项目邀请已过期',
  MEMBER_ROLE_CHANGED: '调整了成员角色',
  MEMBER_REMOVED: '移除了项目成员',
  RECORD_CREATED: '创建了实验记录',
  RECORD_DELETED: '删除了实验记录',
  ATTACHMENT_UPLOADED: '上传了记录附件',
  ATTACHMENT_DELETED: '删除了记录附件',
  RECORD_SUBMITTED: '提交了实验记录审核',
  REVIEW_CHANGES_REQUESTED: '退回了实验记录',
  REVIEW_APPROVED: '通过了实验记录审核',
  REVIEWER_REASSIGNED: '重新指派了审核人',
  RECORD_EXPORT_PREVIEW: '预览了记录报告',
  RECORD_EXPORT_MARKDOWN: '导出了 Markdown 报告',
  RECORD_EXPORT_PDF: '导出了 PDF 报告',
  AGENT_RUN_SUCCEEDED: '生成了已验证的 Agent 报告',
  RECORD_REVISION_RESTORED: '恢复了工作副本',
}

const EVENT_FILTERS = [...Object.keys(EVENT_LABELS)]

const EVENT_ICONS = {
  PROJECT_CREATED: FolderPlus,
  PROJECT_ARCHIVED: Archive,
  INVITATION_CREATED: Mail,
  INVITATION_ACCEPTED: UserPlus,
  INVITATION_REJECTED: Mail,
  INVITATION_EXPIRED: Mail,
  MEMBER_ROLE_CHANGED: CircleUserRound,
  MEMBER_REMOVED: CircleUserRound,
  RECORD_CREATED: FileCheck2,
  RECORD_DELETED: FileCheck2,
  ATTACHMENT_UPLOADED: Paperclip,
  ATTACHMENT_DELETED: Paperclip,
  RECORD_SUBMITTED: FileCheck2,
  REVIEW_CHANGES_REQUESTED: FileCheck2,
  REVIEW_APPROVED: CheckCircle2,
  REVIEWER_REASSIGNED: CircleUserRound,
  RECORD_EXPORT_PREVIEW: FileCheck2,
  RECORD_EXPORT_MARKDOWN: FileCheck2,
  RECORD_EXPORT_PDF: FileCheck2,
  AGENT_RUN_SUCCEEDED: FileCheck2,
  RECORD_REVISION_RESTORED: History,
}

function formatBytes(value) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

export default function ProjectDetailMvpPage() {
  const { projectId } = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const initialTab = searchParams.get('tab') || 'overview'
  const [project, setProject] = useState(null)
  const [members, setMembers] = useState([])
  const [records, setRecords] = useState([])
  const [tab, setTab] = useState(initialTab)
  const [timelineEvents, setTimelineEvents] = useState([])
  const [timelineLoading, setTimelineLoading] = useState(false)
  const [attachments, setAttachments] = useState({ items: [], meta: null })
  const [attachmentPage, setAttachmentPage] = useState(0)
  const [error, setError] = useState('')
  const [loadingTab, setLoadingTab] = useState(false)
  const [showInvite, setShowInvite] = useState(false)
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)

  const loadCore = useCallback(async () => {
    setError('')
    try {
      const [projectData, memberData, recordData] = await Promise.all([
        fetchProject(projectId),
        fetchProjectMembers(projectId),
        fetchRecords({ projectId, size: 100 }),
      ])
      setProject(projectData)
      setMembers(memberData)
      setRecords(recordData.items)
    } catch (requestError) {
      setError(requestError.message)
    }
  }, [projectId])

  useEffect(() => { loadCore() }, [loadCore])

  useEffect(() => {
    if (tab !== 'timeline') return
    setTimelineLoading(true)
    setError('')
    const fetchAll = async () => {
      const all = []
      let page = 0
      const size = 100
      while (true) {
        const result = await fetchProjectAuditEvents(projectId, { page, size })
        all.push(...result.items)
        if (result.items.length < size) break
        page++
      }
      return all
    }
    fetchAll().then(setTimelineEvents).catch((requestError) => setError(requestError.message)).finally(() => setTimelineLoading(false))
  }, [projectId, tab])

  useEffect(() => {
    if (tab !== 'attachments') return
    setLoadingTab(true)
    fetchProjectAttachments(projectId, { page: attachmentPage, size: 20 }).then(setAttachments).catch((requestError) => setError(requestError.message)).finally(() => setLoadingTab(false))
  }, [attachmentPage, projectId, tab])

  const changeTab = (value) => {
    setTab(value)
    const next = new URLSearchParams(searchParams)
    if (value === 'overview') next.delete('tab')
    else next.set('tab', value)
    setSearchParams(next, { replace: true })
  }

  const act = async (operation) => {
    setBusy(true)
    setError('')
    try {
      await operation()
      await loadCore()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setBusy(false)
    }
  }

  if (!project && !error) return <p className="py-16 text-center text-sm text-slate-400">加载项目中…</p>
  if (!project) return <EmptyState icon={Users} title="无法访问项目" description={error} />

  const owner = project.currentUserRole === 'OWNER'
  const active = project.status === 'ACTIVE'
  const tabs = [
    { key: 'overview', label: '概览' },
    { key: 'members', label: `成员 ${members.length}` },
    { key: 'records', label: `实验记录 ${project.recordCount}` },
    { key: 'timeline', label: '时间线' },
    { key: 'attachments', label: '附件汇总' },
    { key: 'progress', label: '智能进展' },
  ]

  const invite = async (event) => {
    event.preventDefault()
    await act(() => inviteProjectMember(projectId, email))
    setEmail('')
    setShowInvite(false)
  }
  const archive = () => {
    if (confirm('归档不可恢复。确认归档该项目？')) act(() => archiveProject(projectId))
  }
  const setRunId = (run) => { const next = new URLSearchParams(searchParams); next.set('tab', 'progress'); if (run) next.set('run', run); else next.delete('run'); setSearchParams(next, { replace: true }) }
  const navigateEvidence = (evidence) => {
    if (evidence.type === 'RECORD') navigate(`/records/${evidence.recordId || evidence.id}`)
    else if (evidence.type === 'REVISION') navigate(`/records/${evidence.recordId}?tab=history&revision=${evidence.revisionId || evidence.id}`)
    else if (evidence.type === 'REVISION_DIFF') navigate(`/records/${evidence.recordId}?tab=history&from=${evidence.fromRevisionId}&to=${evidence.toRevisionId}`)
    else if (evidence.type === 'REVIEW') navigate(`/records/${evidence.recordId}#record-review`)
    else if (evidence.type === 'AUDIT_EVENT') changeTab('timeline')
  }

  return (
    <section className="space-y-6">
      <button onClick={() => navigate('/projects')} className="inline-flex items-center gap-1.5 text-sm text-slate-500"><ArrowLeft size={15} />返回项目列表</button>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><div className="flex items-center gap-3"><h1 className="text-2xl font-bold">{project.name}</h1><StatusBadge kind="project" status={project.status} /><Badge tone={PROJECT_ROLE_TONES[project.currentUserRole]}>{PROJECT_ROLE_LABELS[project.currentUserRole]}</Badge></div><p className="mt-2 text-sm text-slate-500">{project.description || '暂无项目简介'}</p></div>
        {owner && active && <Button variant="danger" icon={Archive} loading={busy} onClick={archive}>归档项目</Button>}
      </div>
      {project.status === 'ARCHIVED' && <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">该项目已不可逆归档，所有写操作均已关闭。</div>}
      {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      <div className="overflow-x-auto"><Tabs items={tabs} activeKey={tab} onChange={changeTab} /></div>

      {tab === 'overview' && <div className="space-y-5"><div className="grid gap-5 md:grid-cols-3"><Surface title="成员数量"><p className="text-3xl font-bold">{project.memberCount}</p></Surface><Surface title="实验记录"><p className="text-3xl font-bold">{project.recordCount}</p></Surface><Surface title="创建时间"><p className="flex items-center gap-2 text-sm text-slate-600"><CalendarDays size={16} className="text-slate-400"/>{new Date(project.createdAt).toLocaleString()}</p></Surface></div><Surface title="项目详细描述" className="min-h-48"><p className="whitespace-pre-wrap text-sm leading-7 text-slate-600">{project.detailedDescription || '暂无项目详细描述'}</p></Surface></div>}

      {tab === 'records' && (() => {
        const createRecord = project.capabilities.canCreateRecord
          ? <Button size="sm" icon={Plus} onClick={() => navigate(`/records/new?projectId=${projectId}`)}>新建实验记录</Button>
          : null
        return records.length
          ? <Surface title="项目实验记录" extra={createRecord}><div className="divide-y">{records.map((record) => <button key={record.id} onClick={() => navigate(`/records/${record.id}`)} className="flex w-full items-center justify-between py-3 text-left"><span><span className="block font-medium">{record.title}</span><span className="text-xs text-slate-400">{record.code} · {record.creatorName}</span></span><span className="text-sm text-brand-600">查看</span></button>)}</div></Surface>
          : <EmptyState title="该项目暂无实验记录" action={createRecord} />
      })()}

      {tab === 'members' && <Surface title="项目成员" extra={owner && active ? <Button size="sm" icon={UserPlus} onClick={() => setShowInvite(true)}>邀请成员</Button> : null}><div className="table-wrap"><table className="data-table"><thead><tr><th>成员</th><th>角色</th><th>加入时间</th>{owner && active && <th>管理</th>}</tr></thead><tbody>{members.map((member) => <tr key={member.userId}><td><div className="font-medium">{member.displayName}</div><div className="text-xs text-slate-400">{member.email}</div></td><td><Badge tone={PROJECT_ROLE_TONES[member.role]}>{PROJECT_ROLE_LABELS[member.role]}</Badge></td><td>{new Date(member.joinedAt).toLocaleString()}</td>{owner && active && <td>{member.role === 'OWNER' ? <span className="text-xs text-slate-400">负责人不可变更</span> : <div className="flex gap-2"><select aria-label={`修改 ${member.displayName} 角色`} value={member.role} onChange={(event) => act(() => updateProjectMemberRole(projectId, member.userId, event.target.value))} className="input h-8 w-32"><option value="MEMBER">编辑成员</option><option value="REVIEWER">审核者</option></select><Button size="sm" variant="danger" onClick={() => confirm(`确认移除 ${member.displayName}？`) && act(() => removeProjectMember(projectId, member.userId))}>移除</Button></div>}</td>}</tr>)}</tbody></table></div></Surface>}

      {tab === 'timeline' && <Surface title="项目时间线">
        {timelineLoading ? <p className="py-8 text-center text-sm text-slate-400">加载时间线中…</p> :
          <TimelineGraph
            events={timelineEvents}
            labels={EVENT_LABELS}
            icons={EVENT_ICONS}
            members={members}
            onNavigateRecord={(recordId) => navigate(`/records/${recordId}`)}
            emptyMessage="暂无协作事件"
          />}
      </Surface>}

      {tab === 'attachments' && <Surface title="记录附件汇总"><p className="mb-4 text-sm text-slate-500">这里只汇总记录附件，不提供项目级上传或删除。</p>{loadingTab ? <p className="py-8 text-center text-sm text-slate-400">加载附件中…</p> : attachments.items.length ? <div className="divide-y">{attachments.items.map((attachment) => <button id={`attachment-${attachment.id}`} key={attachment.id} onClick={() => navigate(`/records/${attachment.recordId}#attachment-${attachment.id}`)} className="flex w-full items-center gap-3 py-3 text-left"><span className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-100 text-slate-500"><Paperclip size={16} /></span><span className="min-w-0 flex-1"><span className="block truncate text-sm font-medium">{attachment.filename}</span><span className="block truncate text-xs text-slate-400">{attachment.recordCode} · {attachment.recordTitle} · {attachment.uploaderName}</span></span><span className="text-xs text-slate-400">{formatBytes(attachment.sizeBytes)}</span></button>)}</div> : <EmptyState icon={Paperclip} title="暂无记录附件" />}{attachments.meta?.totalPages > 1 && <div className="mt-4 flex justify-end gap-2"><Button size="sm" variant="secondary" disabled={attachmentPage === 0} onClick={() => setAttachmentPage((page) => page - 1)}>上一页</Button><Button size="sm" variant="secondary" disabled={attachmentPage + 1 >= attachments.meta.totalPages} onClick={() => setAttachmentPage((page) => page + 1)}>下一页</Button></div>}</Surface>}

      {tab === 'progress' && <Suspense fallback={<p role="status" className="py-12 text-center text-slate-400">加载智能进展组件中…</p>}><ProgressReportPanel project={project} runId={searchParams.get('run')} onRunId={setRunId} onEvidence={navigateEvidence} /></Suspense>}

      {showInvite && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"><form onSubmit={invite} className="w-full max-w-md rounded-2xl bg-white p-6 shadow-pop"><h2 className="text-lg font-semibold">邀请项目成员</h2><p className="mt-1 text-sm text-slate-500">仅可邀请已注册邮箱；接受后默认成为编辑成员。</p><label htmlFor="invite-email" className="field-label mt-5">注册邮箱</label><input id="invite-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required className="input" /><div className="mt-6 flex justify-end gap-2"><Button variant="secondary" onClick={() => setShowInvite(false)}>取消</Button><Button type="submit" loading={busy}>发送邀请</Button></div></form></div>}
    </section>
  )
}
