import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  ChevronLeft,
  ChevronRight,
  FolderKanban,
  LayoutTemplate,
  NotebookPen,
  Paperclip,
  Search,
  SlidersHorizontal,
  User,
} from 'lucide-react'
import { fetchProjectMembers, fetchProjects, search as searchApi } from '@/api'
import { Badge, Button, EmptyState, Tabs } from '@/components/ui'
import { RECORD_STATUS_LABELS, SEARCH_ENTITY_LABELS, SEARCH_ENTITY_TONES } from '@/domain'

const ENTITY_CONFIG = {
  PROJECT: { icon: FolderKanban, tone: 'bg-violet-50 text-violet-600' },
  RECORD: { icon: NotebookPen, tone: 'bg-amber-50 text-amber-600' },
  TEMPLATE: { icon: LayoutTemplate, tone: 'bg-emerald-50 text-emerald-600' },
  MEMBER: { icon: User, tone: 'bg-brand-50 text-brand-600' },
  ATTACHMENT: { icon: Paperclip, tone: 'bg-slate-100 text-slate-500' },
}

const ENTITY_TYPES = ['ALL', 'PROJECT', 'RECORD', 'TEMPLATE', 'MEMBER', 'ATTACHMENT']

function targetPath(target) {
  if (!target) return null
  if (target.type === 'PROJECT') return `/projects/${target.id}`
  if (target.type === 'RECORD') return `/records/${target.id}`
  if (target.type === 'TEMPLATE') return `/templates?templateId=${target.id}`
  if (target.type === 'PROJECT_MEMBERS') return `/projects/${target.id}?tab=members`
  if (target.type === 'RECORD_ATTACHMENT') return `/records/${target.id}#attachment-${target.attachmentId}`
  return null
}

export default function SearchMvpPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [keywordDraft, setKeywordDraft] = useState(searchParams.get('keyword') || '')
  const [showFilters, setShowFilters] = useState(Boolean(searchParams.get('projectId') || searchParams.get('creatorId') || searchParams.get('recordStatus')))
  const [result, setResult] = useState({ items: [], meta: { page: 0, size: 20, total: 0, counts: {} } })
  const [projects, setProjects] = useState([])
  const [members, setMembers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const requestId = useRef(0)
  const keyword = searchParams.get('keyword') || ''
  const entityType = searchParams.get('entityType') || 'ALL'
  const projectId = searchParams.get('projectId') || ''
  const creatorId = searchParams.get('creatorId') || ''
  const recordStatus = searchParams.get('recordStatus') || ''
  const page = Number(searchParams.get('page') || 0)

  const filters = useMemo(() => ({
    keyword,
    entityType,
    projectId,
    creatorId,
    recordStatus,
    page,
    size: 20,
  }), [creatorId, entityType, keyword, page, projectId, recordStatus])

  useEffect(() => {
    fetchProjects({ page: 0, size: 100 }).then((response) => setProjects(response.items)).catch(() => setProjects([]))
  }, [])

  useEffect(() => {
    if (!filters.projectId) {
      setMembers([])
      return
    }
    fetchProjectMembers(filters.projectId).then(setMembers).catch(() => setMembers([]))
  }, [filters.projectId])

  useEffect(() => {
    const currentRequest = ++requestId.current
    setLoading(true)
    setError('')
    searchApi(filters).then((response) => {
      if (requestId.current === currentRequest) setResult(response)
    }).catch((requestError) => {
      if (requestId.current === currentRequest) setError(requestError.message)
    }).finally(() => {
      if (requestId.current === currentRequest) setLoading(false)
    })
  }, [filters])

  useEffect(() => {
    if (keywordDraft === filters.keyword) return undefined
    const timeoutId = window.setTimeout(() => {
      const next = new URLSearchParams(searchParams)
      if (keywordDraft.trim()) next.set('keyword', keywordDraft.trim())
      else next.delete('keyword')
      next.delete('page')
      setSearchParams(next, { replace: true })
    }, 400)
    return () => window.clearTimeout(timeoutId)
  }, [filters.keyword, keywordDraft, searchParams, setSearchParams])

  const updateFilter = (key, value) => {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key === 'projectId') next.delete('creatorId')
    next.delete('page')
    setSearchParams(next, { replace: true })
  }

  const submitKeyword = () => {
    const next = new URLSearchParams(searchParams)
    if (keywordDraft.trim()) next.set('keyword', keywordDraft.trim())
    else next.delete('keyword')
    next.delete('page')
    setSearchParams(next, { replace: true })
  }

  const goPage = (page) => {
    const next = new URLSearchParams(searchParams)
    if (page > 0) next.set('page', String(page))
    else next.delete('page')
    setSearchParams(next)
  }

  const tabs = ENTITY_TYPES.map((type) => ({
    key: type,
    label: type === 'ALL' ? '全部' : SEARCH_ENTITY_LABELS[type],
  }))
  const totalPages = Math.ceil(result.meta.total / result.meta.size)

  return (
    <section>
      <div className="mx-auto max-w-4xl">
        <div className="relative">
          <Search size={18} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            value={keywordDraft}
            onChange={(event) => setKeywordDraft(event.target.value)}
            onKeyDown={(event) => { if (event.key === 'Enter') submitKeyword() }}
            placeholder="搜索项目、实验记录、模板、成员、附件…"
            aria-label="全局搜索"
            className="w-full rounded-xl border border-slate-300 bg-white py-3.5 pl-11 pr-28 text-[15px] text-slate-900 shadow-card outline-none transition placeholder:text-slate-400 focus:border-brand-500 focus:shadow-glow"
          />
          <button type="button" onClick={() => setShowFilters((value) => !value)} className={`absolute right-2.5 top-1/2 flex h-9 -translate-y-1/2 items-center gap-1.5 rounded-lg px-3 text-sm font-medium ${showFilters ? 'bg-brand-50 text-brand-700' : 'text-slate-500 hover:bg-slate-100'}`}>
            <SlidersHorizontal size={15} />高级筛选
          </button>
        </div>

        {showFilters && (
          <div className="mt-3 grid grid-cols-1 gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-card sm:grid-cols-3">
            <div><label htmlFor="search-project" className="field-label">项目</label><select id="search-project" value={filters.projectId} onChange={(event) => updateFilter('projectId', event.target.value)} className="input h-10"><option value="">全部项目</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</select></div>
            <div><label htmlFor="search-creator" className="field-label">记录创建者</label><select id="search-creator" disabled={!filters.projectId} value={filters.creatorId} onChange={(event) => updateFilter('creatorId', event.target.value)} className="input h-10 disabled:bg-slate-50"><option value="">全部成员</option>{members.map((member) => <option key={member.userId} value={member.userId}>{member.displayName}</option>)}</select></div>
            <div><label htmlFor="search-status" className="field-label">记录状态</label><select id="search-status" value={filters.recordStatus} onChange={(event) => updateFilter('recordStatus', event.target.value)} className="input h-10"><option value="">全部状态</option>{Object.entries(RECORD_STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
          </div>
        )}

        <div className="mt-5 overflow-x-auto"><Tabs items={tabs} activeKey={filters.entityType} onChange={(value) => updateFilter('entityType', value === 'ALL' ? '' : value)} /></div>
        {error && <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}

        <div className="mt-4 space-y-2.5">
          {loading ? (
            <div className="rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-400">搜索中…</div>
          ) : result.items.length > 0 ? result.items.map((hit) => {
            const config = ENTITY_CONFIG[hit.entityType]
            const Icon = config?.icon || Paperclip
            const path = targetPath(hit.target)
            return (
              <button key={`${hit.entityType}-${hit.id}`} type="button" onClick={() => path && navigate(path)} className="flex w-full items-center gap-4 rounded-xl border border-slate-200 bg-white px-4 py-3.5 text-left shadow-card transition-all hover:border-brand-200 hover:shadow-pop">
                <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${config?.tone || ENTITY_CONFIG.ATTACHMENT.tone}`}><Icon size={17} /></div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2.5"><span className="truncate text-sm font-semibold text-slate-900">{hit.title}</span><Badge tone={SEARCH_ENTITY_TONES[hit.entityType]}>{SEARCH_ENTITY_LABELS[hit.entityType]}</Badge></div>
                  <p className="mt-1 line-clamp-2 text-xs text-slate-500">{hit.snippet}</p>
                  {hit.projectName && <p className="mt-1 text-[11px] text-slate-400">所属项目：{hit.projectName}</p>}
                </div>
                <ChevronRight size={16} className="shrink-0 text-slate-300" />
              </button>
            )
          }) : <EmptyState icon={Search} title="没有找到相关内容" description="尝试更换关键词或调整筛选条件。" />}
        </div>

        {!loading && totalPages > 1 && (
          <div className="mt-5 flex items-center justify-center gap-3">
            <Button size="sm" variant="secondary" icon={ChevronLeft} disabled={filters.page <= 0} onClick={() => goPage(filters.page - 1)}>上一页</Button>
            <span className="text-xs text-slate-500">第 {filters.page + 1} / {totalPages} 页</span>
            <Button size="sm" variant="secondary" disabled={filters.page + 1 >= totalPages} onClick={() => goPage(filters.page + 1)}>下一页<ChevronRight size={14} /></Button>
          </div>
        )}
      </div>
    </section>
  )
}
