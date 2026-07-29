/**
 * 项目 Agent 工作台：从侧栏进入，顶部选择适用的实验项目。
 */
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Bot, ExternalLink } from 'lucide-react'
import { fetchProject, fetchProjects } from '@/api'
import ProjectChatPanel from '@/components/agent/ProjectChatPanel'
import { EmptyState, PageHeader, Surface } from '@/components/ui'

const STORAGE_KEY = 'bionote.agent.projectId'

export default function AgentMvpPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [projects, setProjects] = useState([])
  const [project, setProject] = useState(null)
  const [loadingList, setLoadingList] = useState(true)
  const [loadingProject, setLoadingProject] = useState(false)
  const [error, setError] = useState('')

  const selectedId = searchParams.get('projectId') || ''

  useEffect(() => {
    let cancelled = false
    setLoadingList(true)
    fetchProjects({ status: 'ACTIVE', size: 100 })
      .then((result) => {
        if (cancelled) return
        const items = result.items || []
        setProjects(items)
        setError('')
        if (!searchParams.get('projectId')) {
          const saved = localStorage.getItem(STORAGE_KEY)
          const fallback = items.find((item) => item.id === saved)?.id || items[0]?.id
          if (fallback) {
            setSearchParams({ projectId: fallback }, { replace: true })
          }
        }
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError?.message || '加载项目列表失败')
      })
      .finally(() => {
        if (!cancelled) setLoadingList(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only bootstrap list once
  }, [])

  useEffect(() => {
    if (!selectedId) {
      setProject(null)
      return
    }
    let cancelled = false
    setLoadingProject(true)
    fetchProject(selectedId)
      .then((detail) => {
        if (cancelled) return
        setProject(detail)
        localStorage.setItem(STORAGE_KEY, selectedId)
        setError('')
      })
      .catch((requestError) => {
        if (!cancelled) {
          setProject(null)
          setError(requestError?.message || '加载项目失败')
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingProject(false)
      })
    return () => {
      cancelled = true
    }
  }, [selectedId])

  const onSelectProject = (event) => {
    const nextId = event.target.value
    if (!nextId) {
      setSearchParams({}, { replace: true })
      return
    }
    setSearchParams({ projectId: nextId }, { replace: true })
  }

  return (
    <section className="space-y-5">
      <PageHeader
        eyebrow="发现与账户"
        title="Agent助手"
        description="针对选定实验项目进行问答、曲线拟合与过程分析。模型由服务端统一配置，切换项目会清空当前对话上下文。"
      />

      <Surface>
        <label className="flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-4">
          <span className="shrink-0 text-sm font-medium text-slate-700">当前适用项目</span>
          <select
            aria-label="选择 Agent 适用的实验项目"
            className="input max-w-xl flex-1"
            value={selectedId}
            disabled={loadingList || projects.length === 0}
            onChange={onSelectProject}
          >
            {projects.length === 0 ? <option value="">暂无可用项目</option> : null}
            {projects.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
                {item.status && item.status !== 'ACTIVE' ? `（${item.status}）` : ''}
              </option>
            ))}
          </select>
          {selectedId ? (
            <Link
              to={`/projects/${selectedId}`}
              className="inline-flex items-center gap-1 text-sm text-brand-600 hover:text-brand-700"
            >
              打开项目
              <ExternalLink size={14} />
            </Link>
          ) : null}
        </label>
        {error ? (
          <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">
            {error}
          </p>
        ) : null}
      </Surface>

      {/* Demo scenario cards */}
      {!loadingList && !loadingProject && project && (
        <div className="grid gap-4 sm:grid-cols-3">
          <Surface className="border-l-4 border-l-brand-500">
            <p className="text-sm font-semibold text-slate-800">实验记录智能总结</p>
            <p className="mt-1 text-xs text-slate-500">
              在记录详情页点击「记录问答」→「总结这个实验」，AI 将按「实验目的 / 材料 / 步骤 / 结果 / 异常 / 建议」结构化输出。
            </p>
            {project.currentUserRole === 'OWNER' && (
              <a href={`/projects/${project.id}`} className="mt-2 inline-block text-xs font-medium text-brand-600 hover:text-brand-700">
                前往项目查看记录 →
              </a>
            )}
          </Surface>
          <Surface className="border-l-4 border-l-emerald-500">
            <p className="text-sm font-semibold text-slate-800">项目进度助手</p>
            <p className="mt-1 text-xs text-slate-500">
              在项目详情页点击「智能进展」→「生成进展报告」，AI 将汇总完成实验、风险与下一步建议。
            </p>
            {project.currentUserRole === 'OWNER' && (
              <a href={`/projects/${project.id}`} className="mt-2 inline-block text-xs font-medium text-brand-600 hover:text-brand-700">
                前往项目详情 →
              </a>
            )}
          </Surface>
          <Surface className="border-l-4 border-l-amber-500">
            <p className="text-sm font-semibold text-slate-800">实验数据分析助手</p>
            <p className="mt-1 text-xs text-slate-500">
              在下方面板中上传 CSV/Excel，用自然语言描述拟合需求（如「拟合 y=a+b*x，xField=浓度，yField=吸光度」）。
            </p>
          </Surface>
        </div>
      )}

      {loadingList || loadingProject ? (
        <p className="text-sm text-slate-400">加载中…</p>
      ) : !selectedId || !project ? (
        <EmptyState
          icon={Bot}
          title="请先选择实验项目"
          description="Agent 对话与拟合都绑定到具体项目。可在上方下拉框中选择，或先到项目管理中加入项目。"
        />
      ) : (
        <ProjectChatPanel project={project} variant="page" />
      )}
    </section>
  )
}
