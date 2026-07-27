import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, ArrowRight, Check, FilePlus2, FolderKanban, LayoutTemplate, NotebookPen } from 'lucide-react'
import { Button, EmptyState, PageHeader, StatusBadge } from '@/components/ui'
import { fetchProjects, fetchTemplates, reserveRecord } from '@/api'

export default function RecordCreateMvpPage() {
  const navigate = useNavigate()
  const [projects, setProjects] = useState([])
  const [templates, setTemplates] = useState([])
  const [projectId, setProjectId] = useState('')
  const [templateId, setTemplateId] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([fetchProjects({ status: 'ACTIVE', size: 100 }), fetchTemplates({ size: 100 })])
      .then(([projectResult, templateResult]) => {
        setProjects(projectResult.items.filter((project) => project.capabilities.canCreateRecord))
        setTemplates(templateResult.items)
      })
      .catch((requestError) => setError(requestError.message))
      .finally(() => setLoading(false))
  }, [])

  const project = projects.find((item) => item.id === projectId)
  const template = templates.find((item) => item.id === templateId)
  const systemTemplates = useMemo(() => templates.filter((item) => item.scope === 'SYSTEM'), [templates])
  const personalTemplates = useMemo(() => templates.filter((item) => item.scope === 'PERSONAL'), [templates])

  const next = async () => {
    if (!projectId) { setError('请选择所属项目'); return }
    setSubmitting(true)
    setError('')
    try {
      const reserved = await reserveRecord({ projectId, templateId: templateId || null })
      navigate(`/records/${reserved.id}/edit?new=1`)
    } catch (requestError) {
      setError(requestError.message)
      setSubmitting(false)
    }
  }

  const TemplateCard = ({ item, blank = false }) => {
    const selected = blank ? !templateId : templateId === item.id
    return <button type="button" onClick={() => setTemplateId(blank ? '' : item.id)} className={`relative rounded-xl border p-4 text-left transition ${selected ? 'border-brand-500 bg-brand-50/70 shadow-sm ring-1 ring-brand-500' : 'border-slate-200 bg-white hover:border-brand-300 hover:shadow-sm'}`}>
      {selected && <span className="absolute right-3 top-3 flex h-5 w-5 items-center justify-center rounded-full bg-brand-600 text-white"><Check size={12}/></span>}
      <span className={`flex h-10 w-10 items-center justify-center rounded-lg ${blank ? 'bg-slate-100 text-slate-600' : 'bg-indigo-50 text-indigo-600'}`}>{blank ? <FilePlus2 size={19}/> : <LayoutTemplate size={19}/>}</span>
      <span className="mt-3 block pr-7 text-sm font-semibold">{blank ? '空白记录' : item.name}</span>
      <span className="mt-1 block line-clamp-2 min-h-10 text-xs leading-5 text-slate-500">{blank ? '仅包含固定字段和自由正文，适合临时或非标准实验。' : item.description || `${item.fields?.length || 0} 个动态字段`}</span>
      {!blank && <span className="mt-3 block text-xs text-slate-400">{item.experimentType || '通用实验'} · {item.fields?.length || 0} 个字段</span>}
    </button>
  }

  return <section className="pb-28">
    <button onClick={() => navigate('/records')} className="mb-4 inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-900"><ArrowLeft size={15}/>返回记录目录</button>
    <PageHeader eyebrow="实验记录" title="创建实验记录" />
    {error && <p role="alert" className="mb-5 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    {loading ? <p className="py-16 text-center text-sm text-slate-400">加载项目与模板中…</p> : <div className="grid gap-6 xl:grid-cols-[minmax(300px,0.85fr),minmax(0,1.65fr)]">
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-card">
        <div className="mb-4 flex items-center gap-3"><span className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-50 text-brand-600"><FolderKanban size={18}/></span><div><h2 className="font-semibold">1. 选择所属项目</h2><p className="text-xs text-slate-400">记录创建后不可更换项目</p></div></div>
        {projects.length ? <div className="space-y-3">{projects.map((item) => {
          const selected = projectId === item.id
          return <button type="button" key={item.id} onClick={() => setProjectId(item.id)} className={`w-full rounded-xl border p-4 text-left transition ${selected ? 'border-brand-500 bg-brand-50/70 ring-1 ring-brand-500' : 'border-slate-200 hover:border-brand-300'}`}><div className="flex items-start gap-3"><span className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border ${selected ? 'border-brand-600 bg-brand-600 text-white' : 'border-slate-300'}`}>{selected && <Check size={12}/>}</span><span className="min-w-0 flex-1"><span className="flex items-center justify-between gap-2"><strong className="truncate text-sm">{item.name}</strong><StatusBadge kind="project" status={item.status}/></span><span className="mt-1 block line-clamp-2 text-xs leading-5 text-slate-500">{item.description || '暂无项目简介'}</span><span className="mt-2 block text-xs text-slate-400">{item.memberCount} 位成员 · {item.recordCount} 条记录</span></span></div></button>
        })}</div> : <EmptyState
          icon={FolderKanban}
          title="暂无可创建记录的项目"
          action={<Button size="sm" onClick={() => navigate('/projects')}>前往项目管理</Button>}
        />}
      </section>
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-card">
        <div className="mb-4 flex items-center gap-3"><span className="flex h-9 w-9 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600"><NotebookPen size={18}/></span><div><h2 className="font-semibold">2. 选择记录结构</h2><p className="text-xs text-slate-400">模板会作为结构快照固化到记录中</p></div></div>
        <div className="grid gap-3 sm:grid-cols-2 2xl:grid-cols-3"><TemplateCard blank/>{systemTemplates.map((item) => <TemplateCard key={item.id} item={item}/>)}</div>
        {personalTemplates.length > 0 && <><div className="my-5 flex items-center gap-3"><span className="h-px flex-1 bg-slate-200"/><span className="text-xs font-medium text-slate-400">我的模板</span><span className="h-px flex-1 bg-slate-200"/></div><div className="grid gap-3 sm:grid-cols-2 2xl:grid-cols-3">{personalTemplates.map((item) => <TemplateCard key={item.id} item={item}/>)}</div></>}
      </section>
    </div>}
    <div className="fixed bottom-0 left-0 right-0 z-20 border-t border-slate-200 bg-white/95 px-4 py-3 shadow-[0_-8px_25px_rgba(15,23,42,0.08)] backdrop-blur lg:left-60"><div className="mx-auto flex max-w-[1600px] flex-wrap items-center justify-between gap-3"><div className="text-sm"><span className="text-slate-400">已选择：</span><strong>{project?.name || '尚未选择项目'}</strong><span className="mx-2 text-slate-300">/</span><strong>{template?.name || '空白记录'}</strong></div><Button icon={ArrowRight} loading={submitting} disabled={loading || !projectId} onClick={next}>进入编辑器</Button></div></div>
  </section>
}
