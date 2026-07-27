import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowUpRight,
  Bell,
  Check,
  FolderKanban,
  Hourglass,
  ListChecks,
  NotebookPen,
  Undo2,
  UserPlus,
  X,
} from 'lucide-react'
import { acceptInvitation, fetchDashboardSummary, fetchDashboardTasks, rejectInvitation } from '@/api'
import { Button, EmptyState } from '@/components/ui'
import { useAuthStore } from '@/store/authStore'

const TASK_STYLES = {
  CHANGES_REQUESTED: { icon: Undo2, box: 'bg-red-50 text-red-600', label: '需修改' },
  PENDING_REVIEW: { icon: Hourglass, box: 'bg-amber-50 text-amber-600', label: '待审核' },
  PROJECT_INVITATION: { icon: UserPlus, box: 'bg-brand-50 text-brand-600', label: '项目邀请' },
}

const SUMMARY_CARDS = [
  { key: 'projectCount', label: '参与项目', icon: FolderKanban },
  { key: 'editableRecordCount', label: '可继续编辑', icon: NotebookPen },
  { key: 'pendingReviewCount', label: '待我审核', icon: ListChecks },
  { key: 'unreadNotificationCount', label: '未读通知', icon: Bell },
]

function taskPath(task) {
  if (task.type === 'CHANGES_REQUESTED') return `/records/${task.targetId}/edit`
  if (task.type === 'PENDING_REVIEW') return `/records/${task.targetId}`
  return null
}

export default function DashboardMvpPage() {
  const navigate = useNavigate()
  const currentUser = useAuthStore((state) => state.currentUser)
  const [tasks, setTasks] = useState([])
  const [summary, setSummary] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      const [taskData, summaryData] = await Promise.all([
        fetchDashboardTasks(),
        fetchDashboardSummary(),
      ])
      setTasks(taskData)
      setSummary(summaryData)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  const respondInvitation = async (task, accepted) => {
    setBusyId(task.id)
    setError('')
    try {
      await (accepted ? acceptInvitation(task.targetId) : rejectInvitation(task.targetId))
      window.dispatchEvent(new Event('bionote:notifications-changed'))
      window.dispatchEvent(new Event('bionote:projects-changed'))
      await load()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className="space-y-7">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-brand-600">工作台</p>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">你好，{currentUser?.displayName}</h1>
          <p className="mt-1.5 text-sm text-slate-500">这里仅展示当前真实可操作的修改、审核和邀请任务。</p>
        </div>
        <Button onClick={() => navigate('/records/new')}>新建实验记录</Button>
      </div>

      {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {SUMMARY_CARDS.map((card) => (
          <div key={card.key} className="rounded-xl border border-slate-200 bg-white p-4">
            <div className="flex items-center justify-between">
              <span className="text-sm text-slate-500">{card.label}</span>
              <card.icon size={17} className="text-slate-400" />
            </div>
            <p className="mt-3 text-2xl font-bold tabular-nums text-slate-900">{summary?.[card.key] ?? '—'}</p>
          </div>
        ))}
      </div>

      <div className="mx-auto max-w-5xl">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-900">需要你处理</h2>
          {!loading && <span className="text-xs tabular-nums text-slate-400">{tasks.length} 项</span>}
        </div>
        {loading ? (
          <div className="rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-400">加载待办中…</div>
        ) : tasks.length === 0 ? (
          <EmptyState icon={ListChecks} title="待办已清空" description="当前没有需要处理的审核、修改或项目邀请。" />
        ) : (
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-card">
            <div className="divide-y divide-slate-100">
              {tasks.map((task) => {
                const config = TASK_STYLES[task.type] || TASK_STYLES.PROJECT_INVITATION
                const Icon = config.icon
                const path = taskPath(task)
                return (
                  <div key={task.id} className="flex items-center gap-4 px-5 py-4">
                    <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${config.box}`}><Icon size={17} /></span>
                    <button type="button" disabled={!path || task.stale} onClick={() => path && navigate(path)} className="min-w-0 flex-1 text-left disabled:cursor-default">
                      <span className="flex flex-wrap items-center gap-2">
                        <span className="truncate text-sm font-semibold text-slate-900">{task.title}</span>
                        <span className="text-xs text-slate-400">{config.label}{task.revisionNo ? ` · R${task.revisionNo}` : ''}</span>
                      </span>
                      <span className="mt-1 block truncate text-xs text-slate-500">{task.projectName} · {new Date(task.time).toLocaleString()}</span>
                    </button>
                    {task.type === 'PROJECT_INVITATION' ? (
                      task.stale ? <span className="text-xs text-slate-400">已失效</span> : (
                        <div className="flex gap-2">
                          <Button size="sm" icon={Check} loading={busyId === task.id} onClick={() => respondInvitation(task, true)}>接受</Button>
                          <Button size="sm" variant="secondary" icon={X} disabled={busyId === task.id} onClick={() => respondInvitation(task, false)}>拒绝</Button>
                        </div>
                      )
                    ) : (
                      <button type="button" onClick={() => navigate(path)} className="flex shrink-0 items-center gap-1 text-xs font-medium text-brand-600">{task.action}<ArrowUpRight size={13} /></button>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        )}
      </div>
    </section>
  )
}
