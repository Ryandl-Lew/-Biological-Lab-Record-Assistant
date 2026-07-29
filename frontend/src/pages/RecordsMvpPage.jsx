import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import DOMPurify from 'dompurify'
import { CalendarDays, Clock3, Eye, FileText, FlaskConical, Pencil, Plus, Search, Send, UserRound } from 'lucide-react'
import { Badge, Button, EmptyState, PageHeader, StatusBadge } from '@/components/ui'
import { fetchProjects, fetchRecords } from '@/api'
import RecordTree from '@/components/record/RecordTree'
import SubmissionDialog from '@/components/record/SubmissionDialog'

export default function RecordsMvpPage() {
  const navigate = useNavigate()
  const [projects, setProjects] = useState([])
  const [records, setRecords] = useState([])
  const [selectedId, setSelectedId] = useState('')
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [submitOpen, setSubmitOpen] = useState(false)

  useEffect(() => {
    setLoading(true)
    Promise.all([fetchProjects({ size: 100 }), fetchRecords({ size: 100 })])
      .then(([projectResult, recordResult]) => {
        setProjects(projectResult.items)
        setRecords(recordResult.items)
        setSelectedId((current) => current || recordResult.items[0]?.id || '')
        setError('')
      })
      .catch((requestError) => setError(requestError.message))
      .finally(() => setLoading(false))
  }, [])

  const filtered = useMemo(() => {
    const query = keyword.trim().toLocaleLowerCase()
    return records.filter((record) => (!status || record.status === status) && (!query || [record.title, record.code, record.projectName, record.purpose, record.experimentType].some((value) => value?.toLocaleLowerCase().includes(query))))
  }, [keyword, records, status])
  const visibleProjectIds = new Set(filtered.map((record) => record.projectId))
  const visibleProjects = projects.filter((project) => visibleProjectIds.has(project.id))
  const selected = filtered.find((record) => record.id === selectedId) || filtered[0]
  const submitted = async () => {
    setSubmitOpen(false)
    try {
      const result = await fetchRecords({ size: 100 })
      setRecords(result.items)
      setError('')
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  return <section>
    <PageHeader eyebrow="实验记录" title="记录目录" actions={<Button icon={Plus} onClick={() => navigate('/records/new')}>新建记录</Button>}/>
    {error && <p role="alert" className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    <div className="grid min-h-[650px] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-card lg:grid-cols-[340px,minmax(0,1fr)]">
      <aside className="border-b border-slate-200 bg-slate-50/60 p-4 lg:border-b-0 lg:border-r">
        <div className="relative"><Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"/><input aria-label="搜索记录" value={keyword} onChange={(event) => setKeyword(event.target.value)} className="input h-10 bg-white pl-9" placeholder="搜索标题、编号、目的"/></div>
        <select aria-label="记录状态" value={status} onChange={(event) => setStatus(event.target.value)} className="input mt-3 h-10 bg-white"><option value="">全部状态</option><option value="IN_PROGRESS">进行中</option><option value="IN_REVIEW">审核中</option><option value="CHANGES_REQUESTED">需修改</option><option value="COMPLETED">已完成</option></select>
        <div className="mt-4 flex items-center justify-between px-2 text-xs font-medium uppercase tracking-wider text-slate-400"><span>项目与记录</span><span>{filtered.length}</span></div>
        <div className="mt-2 max-h-[540px] overflow-y-auto pr-1">{loading ? <p className="py-12 text-center text-sm text-slate-400">加载中…</p> : visibleProjects.length ? <RecordTree projects={visibleProjects} records={filtered} selectedId={selected?.id} onSelect={(record) => setSelectedId(record.id)}/> : <EmptyState icon={FileText} title="暂无匹配记录"/>}</div>
      </aside>
      <main className="min-w-0 p-6 lg:p-8">
        {selected ? <div className="mx-auto max-w-4xl">
          <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-100 pb-6"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><StatusBadge kind="record" status={selected.status}/><Badge>{selected.experimentType}</Badge></div><h2 className="mt-3 text-2xl font-bold text-slate-900">{selected.title}</h2><p className="mt-2 text-sm text-slate-500">{selected.projectName} · {selected.code}</p></div><div className="flex flex-wrap gap-2">{selected.capabilities?.canSubmit && <Button icon={Send} onClick={() => setSubmitOpen(true)}>提交审核</Button>}<Button variant="secondary" icon={Eye} onClick={() => navigate(`/records/${selected.id}`)}>查看详情</Button>{selected.capabilities?.canEdit && <Button variant="secondary" icon={Pencil} onClick={() => navigate(`/records/${selected.id}/edit`)}>编辑记录</Button>}</div></div>
          <dl className="grid gap-3 border-b border-slate-100 py-6 sm:grid-cols-2 xl:grid-cols-4"><div className="rounded-lg bg-slate-50 p-3"><dt className="flex items-center gap-1.5 text-xs text-slate-400"><UserRound size={13}/>创建者</dt><dd className="mt-1 text-sm font-medium">{selected.creatorName}</dd></div><div className="rounded-lg bg-slate-50 p-3"><dt className="flex items-center gap-1.5 text-xs text-slate-400"><CalendarDays size={13}/>实验日期</dt><dd className="mt-1 text-sm font-medium">{selected.experimentDate}</dd></div><div className="rounded-lg bg-slate-50 p-3"><dt className="flex items-center gap-1.5 text-xs text-slate-400"><Clock3 size={13}/>创建时间</dt><dd className="mt-1 text-sm font-medium">{new Date(selected.createdAt).toLocaleDateString()}</dd></div><div className="rounded-lg bg-slate-50 p-3"><dt className="flex items-center gap-1.5 text-xs text-slate-400"><Clock3 size={13}/>最后更新</dt><dd className="mt-1 text-sm font-medium">{new Date(selected.updatedAt).toLocaleString()}</dd></div></dl>
          <section className="py-6"><h3 className="flex items-center gap-2 text-sm font-semibold"><FlaskConical size={16} className="text-brand-600"/>实验目的</h3><p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-slate-600">{selected.purpose || '暂无实验目的'}</p></section>
          <section className="border-t border-slate-100 pt-6"><h3 className="flex items-center gap-2 text-sm font-semibold"><FileText size={16} className="text-brand-600"/>记录正文</h3>{selected.contentHtml ? <div className="prose mt-4 max-w-none text-sm" dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(selected.contentHtml) }}/> : <p className="mt-3 text-sm text-slate-400">暂无正文内容</p>}</section>
        </div> : !loading && <div className="flex h-full items-center justify-center"><EmptyState icon={FileText} title="选择一条记录查看简介"/></div>}
      </main>
    </div>
    {selected && <SubmissionDialog record={selected} open={submitOpen} onClose={() => setSubmitOpen(false)} onSubmitted={submitted}/>}
  </section>
}
