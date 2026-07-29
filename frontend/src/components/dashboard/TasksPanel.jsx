/**
 * TasksPanel —— 右侧"待处理事项"列表。
 * 复用 fetchDashboardTasks 与 fetchDashboardSummary，纯前端聚合，无新后端。
 * 数据来源：CHANGES_REQUESTED（需修改）、PENDING_REVIEW（待审核）、PROJECT_INVITATION（项目邀请）。
 */
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowUpRight, Check, Hourglass, ListChecks, Undo2, UserPlus, X } from 'lucide-react'
import { acceptInvitation, fetchDashboardSummary, fetchDashboardTasks, rejectInvitation } from '@/api'
import { Button, EmptyState } from '@/components/ui'
import formatRelativeTime from '@/lib/formatRelativeTime'

const TASK_STYLES = {
  CHANGES_REQUESTED: { icon: Undo2, box: 'bg-red-50 text-red-600', label: '需修改' },
  PENDING_REVIEW: { icon: Hourglass, box: 'bg-amber-50 text-amber-600', label: '待审核' },
  PROJECT_INVITATION: { icon: UserPlus, box: 'bg-brand-50 text-brand-600', label: '项目邀请' },
}

function taskPath(task) {
  if (task.type === 'CHANGES_REQUESTED') return `/records/${task.targetId}/edit`
  if (task.type === 'PENDING_REVIEW') return `/records/${task.targetId}`
  return null
}

export default function TasksPanel() {
  const navigate = useNavigate()
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
    } catch (err) {
      setError(err.message)
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
    } catch (err) {
      setError(err.message)
    } finally {
      setBusyId(null)
    }
  }

  const totalPending = (summary?.changesRequestedCount ?? 0)
    + (summary?.pendingReviewCount ?? 0)
    + (summary?.pendingInvitationCount ?? 0)

  return (
    <section aria-label="待处理事项" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-card">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400">待处理事项</h2>
        {!loading && (
          <span className="text-xs text-slate-500">
            {totalPending > 0 ? `${totalPending} 项待处理` : '已清空'}
          </span>
        )}
      </div>

      {error && <p role="alert" className="mb-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      {loading ? (
        <p className="py-8 text-center text-sm text-slate-400">加载待办中…</p>
      ) : tasks.length === 0 ? (
        <EmptyState icon={ListChecks} title="待办已清空" description="当前没有需要处理的审核、修改或项目邀请。" />
      ) : (
        <ul className="space-y-2">
          {tasks.map((task) => {
            const config = TASK_STYLES[task.type] || TASK_STYLES.PROJECT_INVITATION
            const Icon = config.icon
            const path = taskPath(task)
            const target = task.stale || !path ? null : path
            return (
              <li key={task.id} className="rounded-lg border border-slate-100 p-3 transition hover:border-slate-200">
                <div className="flex items-start gap-3">
                  <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${config.box}`}>
                    <Icon size={16} strokeWidth={1.8} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate text-sm font-medium text-slate-900">{task.title}</span>
                      <span className="shrink-0 rounded-full bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-500">
                        {config.label}{task.revisionNo ? ` · R${task.revisionNo}` : ''}
                      </span>
                    </div>
                    <p className="mt-1 truncate text-xs text-slate-400">
                      {task.projectName} · {formatRelativeTime(task.time)}
                    </p>
                  </div>
                </div>

                {task.type === 'PROJECT_INVITATION' ? (
                  task.stale ? (
                    <p className="mt-2 pl-12 text-xs text-slate-400">已失效</p>
                  ) : (
                    <div className="mt-2 flex gap-2 pl-12">
                      <Button size="sm" icon={Check} loading={busyId === task.id} onClick={() => respondInvitation(task, true)}>接受</Button>
                      <Button size="sm" variant="secondary" icon={X} disabled={busyId === task.id} onClick={() => respondInvitation(task, false)}>拒绝</Button>
                    </div>
                  )
                ) : (
                  <div className="mt-2 pl-12">
                    {task.stale ? (
                      <span className="text-xs text-slate-400">已失效</span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => target && navigate(target)}
                        className="inline-flex items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700"
                      >
                        {task.action} <ArrowUpRight size={13} />
                      </button>
                    )}
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}