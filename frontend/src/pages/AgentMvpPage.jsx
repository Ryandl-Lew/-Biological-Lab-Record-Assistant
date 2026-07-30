/**
 * 项目 Agent 工作台：从侧栏进入，顶部选择适用的实验项目。
 * 支持对话历史持久化存储与恢复。
 */
import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Bot, ExternalLink, History, MessageSquarePlus, Trash2 } from 'lucide-react'
import { fetchProject, fetchProjects } from '@/api'
import { deleteSession, getSession, listProjectSessions, saveProjectSession, appendSessionMessages } from '@/api/agentChat'
import ProjectChatPanel from '@/components/agent/ProjectChatPanel'
import { Button, EmptyState, PageHeader, Surface } from '@/components/ui'

const STORAGE_KEY = 'bionote.agent.projectId'

export default function AgentMvpPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [projects, setProjects] = useState([])
  const [project, setProject] = useState(null)
  const [loadingList, setLoadingList] = useState(true)
  const [loadingProject, setLoadingProject] = useState(false)
  const [error, setError] = useState('')
  const [showHistory, setShowHistory] = useState(false)
  const [sessions, setSessions] = useState([])
  const [activeSessionId, setActiveSessionId] = useState(null)
  const [sessionMessages, setSessionMessages] = useState(null)
  const [chatKey, setChatKey] = useState(0)

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
    return () => { cancelled = true }
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
    return () => { cancelled = true }
  }, [selectedId])

  useEffect(() => {
    if (!selectedId) return
    listProjectSessions(selectedId)
      .then((result) => setSessions(result))
      .catch(() => {})
  }, [selectedId])

  const onSelectProject = (event) => {
    const nextId = event.target.value
    setActiveSessionId(null)
    setSessionMessages(null)
    if (!nextId) {
      setSearchParams({}, { replace: true })
      return
    }
    setSearchParams({ projectId: nextId }, { replace: true })
  }

  const handleNewChat = () => {
    setActiveSessionId(null)
    setSessionMessages(null)
    setChatKey((k) => k + 1)
    setShowHistory(false)
  }

  const handleLoadSession = async (sessionId) => {
    try {
      const detail = await getSession(sessionId)
      setSessionMessages(detail.messages)
      setActiveSessionId(sessionId)
      setShowHistory(false)
    } catch (e) {
      setError(e.message || '加载对话失败')
    }
  }

  const handleAutoSave = async (messages) => {
    if (messages.length === 0) return
    try {
      if (activeSessionId) {
        await appendSessionMessages(activeSessionId, messages.slice(-2))
      } else {
        const title = messages[0]?.content?.slice(0, 50) || '新对话'
        const detail = await saveProjectSession(selectedId, {
          title,
          messages: messages.filter((m) => m.role === 'user' || m.role === 'assistant'),
        })
        setActiveSessionId(detail.id)
        const updated = await listProjectSessions(selectedId)
        setSessions(updated)
      }
    } catch (e) {
      // silent auto-save failure
    }
  }

  const handleDelete = async (sessionId) => {
    try {
      await deleteSession(sessionId)
      if (activeSessionId === sessionId) {
        setActiveSessionId(null)
        setSessionMessages(null)
      }
      const updated = await listProjectSessions(selectedId)
      setSessions(updated)
    } catch (e) {
      setError(e.message || '删除失败')
    }
  }

  if (!selectedId || !project) {
    return (
      <section className="space-y-5">
        <PageHeader eyebrow="发现与账户" title="Agent助手" />
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
          </label>
          {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
        </Surface>
        {loadingList ? <p className="text-sm text-slate-400">加载中…</p> : (
          <EmptyState icon={Bot} title="请先选择实验项目" description="Agent 对话与拟合都绑定到具体项目。可在上方下拉框中选择，或先到项目管理中加入项目。" />
        )}
      </section>
    )
  }

  return (
    <section className="space-y-5">
      <PageHeader eyebrow="发现与账户" title="Agent助手" />

      <Surface>
        <div className="flex items-center justify-between gap-4">
          <label className="flex flex-1 flex-col gap-2 sm:flex-row sm:items-center sm:gap-4">
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
            {selectedId && (
              <Link to={`/projects/${selectedId}`} className="inline-flex items-center gap-1 text-sm text-brand-600 hover:text-brand-700">
                打开项目<ExternalLink size={14} />
              </Link>
            )}
          </label>
          <div className="flex items-center gap-2">
            <Button variant="secondary" size="sm" icon={MessageSquarePlus} onClick={handleNewChat}>新对话</Button>
            <Button variant="secondary" size="sm" icon={History} onClick={() => { listProjectSessions(selectedId).then(setSessions).catch(() => {}); setShowHistory(!showHistory) }}>对话历史</Button>
          </div>
        </div>
        {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      </Surface>

      {showHistory && (
        <Surface title="对话历史" extra={
          <button onClick={() => setShowHistory(false)} className="text-sm text-slate-400 hover:text-slate-600">关闭</button>
        }>
          {sessions.length === 0 ? (
            <p className="py-6 text-center text-sm text-slate-400">暂无保存的对话</p>
          ) : (
            <div className="divide-y">
              {sessions.map((s) => (
                <div key={s.id} className="flex items-center justify-between py-3">
                  <button
                    onClick={() => handleLoadSession(s.id)}
                    className="flex-1 text-left"
                  >
                    <p className="truncate text-sm font-medium text-slate-900">{s.title}</p>
                    <p className="mt-0.5 text-xs text-slate-400">
                      {s.messageCount} 条消息 · {new Date(s.updatedAt).toLocaleString()}
                    </p>
                  </button>
                  <Button variant="danger" size="sm" icon={Trash2} onClick={() => handleDelete(s.id)} />
                </div>
              ))}
            </div>
          )}
        </Surface>
      )}

      <ProjectChatPanel
        key={chatKey}
        project={project}
        variant="page"
        initialMessages={sessionMessages}
        onAutoSave={handleAutoSave}
      />
    </section>
  )
}
