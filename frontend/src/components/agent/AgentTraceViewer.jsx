import { useEffect, useMemo, useState } from 'react'
import { fetchAgentSteps } from '@/api'
import { Button, EmptyState } from '@/components/ui'

const BLOCKED = /authorization|api.?key|secret|password|reasoning|chain.?of.?thought/i
function safe(value) {
  if (Array.isArray(value)) return value.slice(0, 20).map(safe)
  if (value && typeof value === 'object')
    return Object.fromEntries(
      Object.entries(value)
        .filter(([key]) => !BLOCKED.test(key))
        .map(([key, item]) => [key, safe(item)]),
    )
  return typeof value === 'string' && value.length > 1500 ? `${value.slice(0, 1500)}…` : value
}

export default function AgentTraceViewer({ run, open, onClose }) {
  const [steps, setSteps] = useState([]),
    [error, setError] = useState(''),
    [loading, setLoading] = useState(false),
    [visible, setVisible] = useState(1),
    [playing, setPlaying] = useState(false)
  useEffect(() => {
    if (!open || !run) return
    setLoading(true)
    setError('')
    fetchAgentSteps(run.id)
      .then((result) => {
        setSteps(result.items)
        setVisible(1)
      })
      .catch((requestError) => setError(requestError.message))
      .finally(() => setLoading(false))
  }, [open, run])
  useEffect(() => {
    if (!playing) return undefined
    const timer = setInterval(
      () =>
        setVisible((count) => {
          if (count >= steps.length) {
            setPlaying(false)
            return count
          }
          return count + 1
        }),
      700,
    )
    return () => clearInterval(timer)
  }, [playing, steps.length])
  useEffect(() => () => setPlaying(false), [])
  const shown = useMemo(() => steps.slice(0, visible), [steps, visible])
  if (!open || !run) return null
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Agent 运行轨迹"
    >
      <div className="max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-2xl bg-white/95 p-6 shadow-2xl backdrop-blur-sm">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold">运行轨迹</h2>
            <p className="mt-1 break-all text-xs text-slate-400">Run {run.id}</p>
          </div>
          <Button variant="secondary" onClick={onClose}>
            关闭
          </Button>
        </div>
        <dl className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {[
            ['状态', run.status],
            ['触发方式', run.triggerType],
            ['模型', `${run.provider} / ${run.model}`],
            ['Token', `${run.inputTokens} in / ${run.outputTokens} out`],
          ].map(([label, value]) => (
            <div key={label} className="rounded-lg bg-slate-50 p-3">
              <dt className="text-xs text-slate-400">{label}</dt>
              <dd className="mt-1 text-sm font-medium">{value}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-4 rounded-lg bg-blue-50 p-3 text-xs text-blue-800">
          Replay 仅逐步展开已保存的脱敏步骤，不会重新调用模型或工具，也不展示隐藏推理。
        </p>
        {loading && (
          <p role="status" className="py-12 text-center text-slate-400">
            加载轨迹中…
          </p>
        )}
        {error && (
          <p role="alert" className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
            {error}
          </p>
        )}
        {!loading && !error && !steps.length && <EmptyState title="该运行尚无可见步骤" />}
        {steps.length > 0 && (
          <>
            <div className="mt-5 flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="secondary"
                disabled={visible >= steps.length}
                onClick={() => setVisible((count) => Math.min(steps.length, count + 1))}
              >
                下一步
              </Button>
              <Button
                size="sm"
                variant="secondary"
                disabled={visible >= steps.length}
                onClick={() => setPlaying((value) => !value)}
              >
                {playing ? '暂停 Replay' : '播放 Replay'}
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => {
                  setPlaying(false)
                  setVisible(steps.length)
                }}
              >
                展开全部
              </Button>
            </div>
            <ol className="mt-5 space-y-3">
              {shown.map((step) => (
                <li key={step.id} className="rounded-xl border p-4">
                  <details>
                    <summary className="cursor-pointer font-medium">
                      #{step.stepNo} {step.stepType}
                      {step.toolName ? ` · ${step.toolName}` : ''}{' '}
                      <span className="ml-2 text-xs font-normal text-slate-400">
                        {step.latencyMs} ms
                      </span>
                    </summary>
                    <div className="mt-3 grid gap-3 lg:grid-cols-2">
                      <div>
                        <h3 className="text-xs font-semibold text-slate-500">请求摘要</h3>
                        <pre className="mt-1 overflow-auto rounded-lg bg-slate-950 p-3 text-xs text-slate-100">
                          {JSON.stringify(safe(step.request), null, 2)}
                        </pre>
                      </div>
                      <div>
                        <h3 className="text-xs font-semibold text-slate-500">结果摘要</h3>
                        <pre className="mt-1 overflow-auto rounded-lg bg-slate-950 p-3 text-xs text-slate-100">
                          {JSON.stringify(safe(step.response), null, 2)}
                        </pre>
                      </div>
                    </div>
                  </details>
                </li>
              ))}
            </ol>
          </>
        )}
      </div>
    </div>
  )
}
