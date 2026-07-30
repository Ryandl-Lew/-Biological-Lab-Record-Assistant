import { useEffect, useRef, useState } from 'react'
import { cancelAgentRun, fetchAgentRun, rerunAgent } from '@/api'
import { ACTIVE_AGENT_RUN_STATUSES, AGENT_RUN_STATUS } from '@/api/agentTypes'
import { Button } from '@/components/ui'
import { agentErrorMessage } from './messages'

const LABELS = {
  QUEUED: '等待处理',
  RUNNING: '正在收集证据并生成报告',
  SUCCEEDED: '已生成',
  FAILED: '生成失败，可查看原因并重试',
  CANCELLED: '已取消',
  LIMIT_EXCEEDED: '达到运行限制，未生成报告',
  INVALID_OUTPUT: '模型结果未通过结构或证据校验',
}
const newKey = () => globalThis.crypto?.randomUUID?.() || `agent-${Date.now()}-${Math.random()}`

export default function AgentRunStatus({ runId, onRunId, onRun, onSucceeded, onTrace }) {
  const [run, setRun] = useState(null),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false)
  const timer = useRef(null),
    attempt = useRef(0),
    succeeded = useRef(null)
  useEffect(() => {
    if (!runId) {
      setRun(null)
      return undefined
    }
    let cancelled = false
    const poll = async () => {
      try {
        const value = await fetchAgentRun(runId)
        if (cancelled) return
        setRun(value)
        setError('')
        onRun?.(value)
        if (value.status === AGENT_RUN_STATUS.SUCCEEDED && succeeded.current !== value.id) {
          succeeded.current = value.id
          onSucceeded?.(value)
        }
        if (ACTIVE_AGENT_RUN_STATUSES.has(value.status)) {
          const delays = [2000, 3000, 5000]
          timer.current = setTimeout(poll, delays[Math.min(attempt.current++, delays.length - 1)])
        }
      } catch (requestError) {
        if (!cancelled) setError(agentErrorMessage(requestError))
      }
    }
    attempt.current = 0
    poll()
    return () => {
      cancelled = true
      clearTimeout(timer.current)
    }
  }, [onRun, onSucceeded, runId])
  if (!runId) return null
  const cancel = async () => {
    setBusy(true)
    try {
      const value = await cancelAgentRun(runId)
      setRun(value)
      onRun?.(value)
    } catch (requestError) {
      setError(agentErrorMessage(requestError))
    } finally {
      setBusy(false)
    }
  }
  const rerun = async () => {
    setBusy(true)
    try {
      const value = await rerunAgent(runId, newKey())
      setRun(value)
      onRunId(value.id)
      succeeded.current = null
    } catch (requestError) {
      setError(agentErrorMessage(requestError))
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="rounded-xl border border-brand-200 bg-brand-50 p-4" aria-live="polite">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="font-medium">{LABELS[run?.status] || '正在读取运行状态'}</p>
          {run?.errorMessage && (
            <p className="mt-1 text-sm text-red-700">{agentErrorMessage(run)}</p>
          )}
          {error && <p className="mt-1 text-sm text-red-700">{error}</p>}
        </div>
        <div className="flex flex-wrap gap-2">
          {ACTIVE_AGENT_RUN_STATUSES.has(run?.status) && (
            <Button size="sm" variant="secondary" loading={busy} onClick={cancel}>
              取消
            </Button>
          )}
          {run &&
            !ACTIVE_AGENT_RUN_STATUSES.has(run.status) &&
            run.status !== AGENT_RUN_STATUS.SUCCEEDED && (
              <Button size="sm" loading={busy} onClick={rerun}>
                重新运行
              </Button>
            )}
          {run && (
            <Button size="sm" variant="secondary" onClick={() => onTrace(run)}>
              查看运行轨迹
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
