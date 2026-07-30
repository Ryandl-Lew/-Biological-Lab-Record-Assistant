import { useCallback, useEffect, useRef, useState } from 'react'
import {
  cancelAgentRun,
  createProjectAgentRun,
  createRecordAgentRun,
  fetchAgentArtifact,
  fetchAgentRun,
  fetchAgentSteps,
  fetchProjectArtifacts,
  fetchRecordArtifacts,
  rerunAgent,
} from '@/api'
import { AGENT_ARTIFACT_KIND, ACTIVE_AGENT_RUN_STATUSES, AGENT_RUN_STATUS } from '@/api/agentTypes'
import { Badge, Button, EmptyState, Surface } from '@/components/ui'
import { agentErrorMessage } from './messages'
import AgentArtifactView from './AgentArtifactView'
import {
  Activity,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Clock,
  FileText,
  Loader2,
} from 'lucide-react'

const newKey = () => globalThis.crypto?.randomUUID?.() || `summary-${Date.now()}-${Math.random()}`

const STEP_TYPE_LABELS = {
  RUN_STARTED: '任务启动',
  MODEL_REQUEST: '请求模型',
  MODEL_RESPONSE: '模型响应',
  TOOL_RESULT: '工具执行',
}

const STEP_LABELS = {
  get_project_overview: '读取项目概况',
  list_project_records: '收集实验记录',
  get_record_overview: '读取记录详情',
  list_record_revisions: '分析版本历史',
  get_revision_summary: '提取版本变更',
  compare_record_revisions: '对比版本差异',
  list_review_feedback: '收集审核反馈',
  list_project_attachments: '列出项目附件',
  list_record_attachments: '列出记录附件',
  read_attachment_content: '读取附件内容',
  list_project_activity: '分析项目活动',
  get_latest_project_report: '读取历史报告',
}

function getStepLabel(step) {
  if (step.toolName && STEP_LABELS[step.toolName]) return STEP_LABELS[step.toolName]
  if (step.stepType && STEP_TYPE_LABELS[step.stepType]) return STEP_TYPE_LABELS[step.stepType]
  return step.toolName || step.stepType || '正在处理...'
}

function StepItem({ step, isActive, isDone }) {
  const Icon = isActive ? Loader2 : isDone ? CheckCircle2 : Clock
  const label = getStepLabel(step)
  return (
    <div
      className={`flex items-center gap-2 rounded-lg border px-2.5 py-1.5 text-xs ${
        isActive
          ? 'border-brand-200 bg-brand-50 text-brand-700'
          : isDone
            ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
            : 'border-slate-100 bg-white text-slate-400'
      }`}
    >
      <Icon
        size={12}
        className={
          isActive ? 'animate-spin text-brand-500' : isDone ? 'text-emerald-500' : 'text-slate-300'
        }
      />
      <span className="flex-1">{label}</span>
      {step.latencyMs != null && step.latencyMs > 0 && (
        <span className="text-xs text-slate-400">{step.latencyMs}ms</span>
      )}
    </div>
  )
}

export default function AutoSummaryPanel({ subjectType, subjectId }) {
  const isRecord = subjectType === 'record'
  const artifactKind = isRecord
    ? AGENT_ARTIFACT_KIND.RECORD_SUMMARY
    : AGENT_ARTIFACT_KIND.PROJECT_PROGRESS
  const fetchArtifacts = isRecord
    ? () => fetchRecordArtifacts(subjectId)
    : () => fetchProjectArtifacts(subjectId)

  const [history, setHistory] = useState([])
  const [runId, setRunId] = useState(null)
  const [run, setRun] = useState(null)
  const [steps, setSteps] = useState([])
  const [artifact, setArtifact] = useState(null)
  const [artifactRun, setArtifactRun] = useState(null)
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)
  const [showHistory, setShowHistory] = useState(false)
  const [showAllSteps, setShowAllSteps] = useState(false)

  const timer = useRef(null)
  const attempt = useRef(0)

  const loadHistory = useCallback(async () => {
    try {
      const result = await fetchArtifacts()
      setHistory(result.items || [])
    } catch {
      /* silent */
    }
  }, [])

  useEffect(() => {
    loadHistory()
  }, [loadHistory])

  // Poll run status
  useEffect(() => {
    if (!runId) return
    let cancelled = false
    const poll = async () => {
      try {
        const value = await fetchAgentRun(runId)
        if (cancelled) return
        setRun(value)
        setError('')
        if (!ACTIVE_AGENT_RUN_STATUSES.has(value.status)) {
          setCreating(false)
        }
        if (value.status === AGENT_RUN_STATUS.SUCCEEDED) {
          try {
            const artifactData = await fetchAgentArtifact(value.artifactId)
            setArtifact(artifactData)
            setArtifactRun(value)
            setRunId(null)
            await loadHistory()
          } catch (e) {
            setError(agentErrorMessage(e))
          }
          return
        }
        if (ACTIVE_AGENT_RUN_STATUSES.has(value.status)) {
          try {
            const stepsData = await fetchAgentSteps(runId)
            setSteps(stepsData.items || [])
          } catch {
            /* steps are optional */
          }
          const delays = [1500, 2500, 4000]
          timer.current = setTimeout(poll, delays[Math.min(attempt.current++, delays.length - 1)])
        }
        if (
          value.status === AGENT_RUN_STATUS.FAILED ||
          value.status === AGENT_RUN_STATUS.LIMIT_EXCEEDED ||
          value.status === AGENT_RUN_STATUS.INVALID_OUTPUT
        ) {
          setError(agentErrorMessage(value))
          setRunId(null)
        }
      } catch (requestError) {
        if (!cancelled) {
          setError(agentErrorMessage(requestError))
          setRunId(null)
        }
      }
    }
    attempt.current = 0
    poll()
    return () => {
      cancelled = true
      clearTimeout(timer.current)
    }
  }, [runId])

  const start = async () => {
    setCreating(true)
    setError('')
    setArtifact(null)
    setArtifactRun(null)
    setSteps([])
    try {
      const created = isRecord
        ? await createRecordAgentRun(subjectId, { artifactKind }, newKey())
        : await createProjectAgentRun(subjectId, { artifactKind }, newKey())
      setRunId(created.id)
    } catch (requestError) {
      setError(agentErrorMessage(requestError))
      setCreating(false)
    }
  }

  const cancel = async () => {
    if (!runId) return
    try {
      await cancelAgentRun(runId)
      setRunId(null)
      setCreating(false)
    } catch (requestError) {
      setError(agentErrorMessage(requestError))
    }
  }

  const rerun = async () => {
    if (!run) return
    try {
      const created = await rerunAgent(run.id, newKey())
      setRunId(created.id)
      setCreating(true)
      setError('')
      setArtifact(null)
      setArtifactRun(null)
      setSteps([])
    } catch (requestError) {
      setError(agentErrorMessage(requestError))
    }
  }

  const viewArtifact = async (summary) => {
    try {
      const detail = await fetchAgentArtifact(summary.id)
      setArtifact(detail)
      const relatedRun = {
        id: summary.runId,
        provider: '',
        model: '',
        artifactKind: summary.artifactKind,
        createdAt: summary.createdAt,
      }
      setArtifactRun(relatedRun)
      setShowHistory(false)
    } catch (requestError) {
      setError(agentErrorMessage(requestError))
    }
  }

  const isRunning = runId && run && ACTIVE_AGENT_RUN_STATUSES.has(run.status)

  return (
    <Surface title={isRecord ? '自动总结' : '项目自动总结'}>
      <p className="text-sm text-slate-500">
        {isRecord
          ? 'AI 将自动深入分析本实验记录的版本历史、审核意见、附件和字段数据，提取关键发现并生成结构化总结报告。'
          : 'AI 将自动遍历项目下所有实验记录，结合历史版本与审核信息，提取关键进展和风险并生成结构化总结报告。'}
      </p>

      {error && (
        <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {/* Running state */}
      {isRunning && (
        <div className="mt-4 space-y-3">
          <div className="rounded-xl border border-brand-200 bg-brand-50 p-4" aria-live="polite">
            <div className="flex items-center gap-3">
              <Loader2 size={18} className="animate-spin text-brand-600" />
              <div>
                <p className="font-medium text-brand-800">
                  {run.status === 'QUEUED' ? '等待处理...' : '正在分析并生成报告...'}
                </p>
                <p className="mt-0.5 text-xs text-brand-600">
                  已执行 {run.stepCount} 步 | {run.toolCallCount} 次工具调用 |{' '}
                  {run.inputTokens + run.outputTokens} tokens
                </p>
              </div>
            </div>
          </div>

          {steps.length > 0 &&
            (() => {
              const VISIBLE = 6
              const collapsed = steps.length > VISIBLE && !showAllSteps
              const visibleSteps = collapsed ? steps.slice(-VISIBLE) : steps
              const hiddenCount = steps.length - VISIBLE
              return (
                <div className="space-y-1">
                  <div className="flex items-center justify-between">
                    <p className="text-xs font-medium text-slate-500">执行步骤 ({steps.length})</p>
                    {steps.length > VISIBLE && (
                      <button
                        onClick={() => setShowAllSteps(!showAllSteps)}
                        className="inline-flex items-center gap-1 text-xs text-brand-600 hover:text-brand-700"
                      >
                        {showAllSteps ? (
                          <>
                            <ChevronRight size={12} className="rotate-90" />
                            收起
                          </>
                        ) : (
                          <>
                            <ChevronDown size={12} />
                            展开全部
                          </>
                        )}
                      </button>
                    )}
                  </div>
                  {collapsed && (
                    <div className="rounded-lg border border-slate-100 bg-slate-50 px-2.5 py-1.5 text-xs text-slate-400">
                      已完成 {hiddenCount} 个步骤
                    </div>
                  )}
                  {visibleSteps.map((step, index) => {
                    const actualIndex = collapsed ? index + hiddenCount : index
                    return (
                      <StepItem
                        key={step.stepNo || index}
                        step={step}
                        isActive={actualIndex === steps.length - 1 && isRunning}
                        isDone={actualIndex < steps.length - 1 || !isRunning}
                      />
                    )
                  })}
                </div>
              )
            })()}

          {steps.length === 0 && (
            <div className="space-y-2">
              <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm">
                <Loader2 size={14} className="animate-spin text-brand-500" />
                <span>正在准备分析任务...</span>
              </div>
            </div>
          )}

          <div className="flex gap-2">
            <Button size="sm" variant="secondary" onClick={cancel}>
              取消
            </Button>
          </div>
        </div>
      )}

      {/* Completed state */}
      {artifact && (
        <div className="mt-4 space-y-3">
          <AgentArtifactView artifact={artifact} run={artifactRun} />
          <div className="flex gap-2">
            <Button size="sm" onClick={rerun}>
              重新生成
            </Button>
            {history.length > 0 && (
              <Button size="sm" variant="secondary" onClick={() => setShowHistory(!showHistory)}>
                历史总结 ({history.length})
              </Button>
            )}
          </div>
        </div>
      )}

      {/* History list */}
      {showHistory && history.length > 0 && (
        <div className="mt-4 rounded-lg border border-slate-200">
          <div className="divide-y">
            {history.map((item) => (
              <button
                key={item.id}
                onClick={() => viewArtifact(item)}
                className="flex w-full items-center justify-between px-3 py-2.5 text-left text-sm hover:bg-slate-50"
              >
                <span>
                  <b className="block truncate max-w-xs">{item.headline || '未命名报告'}</b>
                  <span className="text-xs text-slate-400">
                    {new Date(item.createdAt).toLocaleString()}
                  </span>
                </span>
                <Badge>{item.artifactKind === 'RECORD_SUMMARY' ? '记录总结' : '项目进展'}</Badge>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Idle state */}
      {!artifact && !isRunning && !creating && (
        <div className="mt-4">
          {history.length > 0 && !showHistory && (
            <p className="mb-3 text-xs text-slate-400">
              已有 {history.length} 份历史总结，
              <button
                onClick={() => setShowHistory(true)}
                className="text-brand-600 hover:underline"
              >
                查看
              </button>
            </p>
          )}
          <Button icon={FileText} onClick={start}>
            {history.length ? '重新生成' : '开始总结'}
          </Button>
        </div>
      )}

      {/* Creating (waiting for first poll) */}
      {creating && !runId && (
        <div className="mt-4 flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-500">
          <Loader2 size={14} className="animate-spin" />
          正在提交分析任务...
        </div>
      )}

      {/* No history, no artifact, not running */}
      {history.length === 0 && !artifact && !isRunning && !creating && (
        <EmptyState
          icon={Activity}
          title={isRecord ? '暂无记录总结' : '暂无项目总结'}
          description={
            isRecord
              ? '点击上方按钮，AI 将自动分析本记录并生成结构化报告。'
              : '点击上方按钮，AI 将自动分析项目下所有记录并生成进展报告。'
          }
        />
      )}
    </Surface>
  )
}
