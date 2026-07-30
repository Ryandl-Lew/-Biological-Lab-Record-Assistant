import { useEffect, useRef, useState } from 'react'
import { CircleHelp } from 'lucide-react'
import { sendProjectAgentChat } from '@/api/agentChat'
import { Button, Surface } from '@/components/ui'
import { agentErrorMessage, trimChatHistory } from './messages'
import { renderMarkdown } from '@/lib/renderMarkdown'
import DataChart, { chartFromFit } from './DataChart'
import AgentHelpDialog from './AgentHelpDialog'

function formatNum(value) {
  if (value == null || Number.isNaN(Number(value))) return '—'
  return Number(value).toFixed(4)
}

function FitResultCard({ fit }) {
  if (!fit) return null
  const params = fit.parameters
    ? Object.entries(fit.parameters)
        .map(([key, value]) => `${key}=${typeof value === 'number' ? value.toFixed(4) : value}`)
        .join(', ')
    : ''
  return (
    <div className="mt-2 rounded-xl border border-emerald-200 bg-emerald-50/80 p-3 text-slate-800">
      <p className="text-xs font-semibold uppercase tracking-wide text-emerald-700">曲线拟合结果</p>
      <p className="mt-1 font-mono text-sm">{fit.equation}</p>
      <dl className="mt-2 grid grid-cols-2 gap-x-3 gap-y-1 text-xs text-slate-600 sm:grid-cols-4">
        <div>
          <dt className="text-slate-400">n</dt>
          <dd>{fit.n}</dd>
        </div>
        <div>
          <dt className="text-slate-400">R²</dt>
          <dd>{formatNum(fit.rSquared)}</dd>
        </div>
        <div>
          <dt className="text-slate-400">RMSE</dt>
          <dd>{formatNum(fit.rmse)}</dd>
        </div>
        <div>
          <dt className="text-slate-400">记录</dt>
          <dd>{(fit.usedRecordCodes || []).length}</dd>
        </div>
      </dl>
      {params && <p className="mt-2 text-xs text-slate-700">参数：{params}</p>}
      {Array.isArray(fit.comparisons) && fit.comparisons.length > 0 && (
        <div className="mt-2 overflow-x-auto">
          <p className="text-xs font-medium text-slate-600">模型比选</p>
          <table className="mt-1 w-full min-w-[16rem] text-left text-xs text-slate-600">
            <thead>
              <tr className="border-b border-emerald-100 text-slate-400">
                <th className="py-1 pr-2 font-normal">方程</th>
                <th className="py-1 pr-2 font-normal">R²</th>
                <th className="py-1 font-normal">RMSE</th>
              </tr>
            </thead>
            <tbody>
              {fit.comparisons.map((row) => (
                <tr
                  key={row.equation}
                  className={row.selected ? 'bg-emerald-100/70 font-medium text-emerald-900' : ''}
                >
                  <td className="py-1 pr-2 font-mono">{row.equation}{row.selected ? ' ✓' : ''}</td>
                  <td className="py-1 pr-2">{formatNum(row.rSquared)}</td>
                  <td className="py-1">{formatNum(row.rmse)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {Array.isArray(fit.skipped) && fit.skipped.length > 0 && (
        <p className="mt-2 text-xs text-amber-700">
          跳过 {fit.skipped.length} 条：
          {fit.skipped
            .slice(0, 3)
            .map((item) => `${item.recordCode}（${item.reason}）`)
            .join('；')}
          {fit.skipped.length > 3 ? '…' : ''}
        </p>
      )}
      {(() => {
        const chart = chartFromFit(fit)
        return chart ? <DataChart {...chart} /> : null
      })()}
    </div>
  )
}

function ChartResultCard({ chart }) {
  if (!chart?.series?.length) return null
  return (
    <div className="mt-2 rounded-xl border border-indigo-200 bg-indigo-50/70 p-3 text-slate-800">
      <p className="text-xs font-semibold uppercase tracking-wide text-indigo-700">数据图</p>
      <DataChart
        type={chart.type}
        title={chart.title}
        xLabel={chart.xLabel}
        yLabel={chart.yLabel}
        series={chart.series}
      />
    </div>
  )
}

function AnalysisTemplateCard({ template }) {
  if (!template) return null
  return (
    <div className="mt-2 rounded-xl border border-amber-200 bg-amber-50/80 p-3 text-slate-800">
      <p className="text-xs font-semibold uppercase tracking-wide text-amber-800">分析模板（通用框架）</p>
      <p className="mt-1 text-sm font-medium">
        {template.label} <span className="font-mono text-xs text-slate-500">({template.id})</span>
      </p>
      {template.description ? <p className="mt-1 text-xs text-slate-600">{template.description}</p> : null}
      {Array.isArray(template.outputSections) && template.outputSections.length > 0 ? (
        <div className="mt-2">
          <p className="text-xs font-medium text-slate-600">输出章节</p>
          <ol className="mt-1 list-decimal space-y-0.5 pl-4 text-xs text-slate-600">
            {template.outputSections.map((section) => (
              <li key={section}>{section}</li>
            ))}
          </ol>
        </div>
      ) : null}
      {Array.isArray(template.suggestedFits) && template.suggestedFits.length > 0 ? (
        <div className="mt-2">
          <p className="text-xs font-medium text-slate-600">建议证据拟合（仍走确认拟合，不编造参数）</p>
          <ul className="mt-1 space-y-1 text-xs text-slate-600">
            {template.suggestedFits.map((item) => (
              <li key={`${item.purpose}-${item.xHint}-${item.yHint}`}>
                {item.purpose}：x={item.xHint}，y={item.yHint}
                {item.note ? `（${item.note}）` : ''}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {Array.isArray(template.uncertaintyChecklist) && template.uncertaintyChecklist.length > 0 ? (
        <p className="mt-2 text-xs text-amber-900/80">
          缺测须写入分析边界：{template.uncertaintyChecklist.join('、')}
        </p>
      ) : null}
    </div>
  )
}

export default function ProjectChatPanel({ project, variant = 'embedded', initialMessages = null, onAutoSave }) {
  const [messages, setMessages] = useState([])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [helpOpen, setHelpOpen] = useState(false)
  const bottomRef = useRef(null)
  const tall = variant === 'page'

  useEffect(() => {
    const restored = (initialMessages || []).map((msg) => {
      if (!msg.metadata) return msg
      try {
        const extra = JSON.parse(msg.metadata)
        return { ...msg, fit: extra.fit || null, fits: extra.fits || null, chart: extra.chart || null, systemError: extra.systemError || null }
      } catch {
        return msg
      }
    })
    setMessages(restored)
    setDraft('')
    setError('')
  }, [project.id, initialMessages])

  useEffect(() => {
    if (typeof bottomRef.current?.scrollIntoView === 'function') {
      bottomRef.current.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  }, [messages, sending])

  const sendPayload = async (text) => {
    const message = (text || '').trim()
    if (!message || sending) return
    setSending(true)
    setError('')
    setDraft('')
    const history = trimChatHistory(messages)
    setMessages((current) => [
      ...current,
      { role: 'user', content: message },
    ])
    try {
      const body = { message, history }
      const result = await sendProjectAgentChat(project.id, body)
      const fits = Array.isArray(result.fits) && result.fits.length > 0
        ? result.fits
        : result.fit
          ? [result.fit]
          : []
      setMessages((current) => [
        ...current,
        {
          role: 'assistant',
          content: result.reply,
          systemError: result.systemError || null,
          fit: result.fit || null,
          fits,
          chart: result.chart || null,
        },
      ])
      if (onAutoSave) {
        const meta = { fit: result.fit || null, fits, chart: result.chart || null, systemError: result.systemError || null }
        const metadata = JSON.stringify(meta)
        const savedMessages = [...messages, { role: 'user', content: message }, { role: 'assistant', content: result.reply, metadata }]
        onAutoSave(savedMessages.filter((m) => m.role === 'user' || m.role === 'assistant'))
      }
    } catch (requestError) {
      setError(agentErrorMessage(requestError))
      setMessages((current) => current.slice(0, -1))
      setDraft(text)
    } finally {
      setSending(false)
    }
  }

  const send = async () => {
    const text = draft.trim()
    if (!text) return
    await sendPayload(text)
  }

  const onKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      send()
    }
  }

  return (
    <>
    <Surface
      title="项目问答"
      extra={
        <button
          type="button"
          onClick={() => setHelpOpen(true)}
          className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-sm text-slate-500 transition hover:bg-slate-100 hover:text-slate-800"
          aria-label="打开 Agent 使用帮助"
        >
          <CircleHelp size={16} />
          <span>帮助</span>
        </button>
      }
    >
      <div
        className={`space-y-3 overflow-y-auto rounded-lg border border-slate-200 bg-slate-50 p-3 ${
          tall ? 'max-h-[min(70vh,40rem)] min-h-[22rem]' : 'max-h-80'
        }`}
      >
        {messages.length === 0 && !sending && (
          <p className="py-6 text-center text-sm text-slate-400">
            试试：线性拟合、画折线图 x轴=时间 y轴=残糖。需要说明时点右上角「帮助」。
          </p>
        )}
        {messages.map((item, index) => (
          <div
            key={`${item.role}-${index}`}
            className={`flex ${item.role === 'user' ? 'justify-end' : 'justify-start'}`}
          >
            <div
              className={`max-w-[85%] rounded-2xl px-3 py-2 text-sm ${
                item.role === 'user'
                  ? 'bg-brand-600 text-white whitespace-pre-wrap'
                  : 'border border-slate-200 bg-white text-slate-800'
              }`}
            >
              {item.role === 'assistant' && item.systemError ? (
                <div className="mb-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                  <p className="mb-1 font-semibold">文件读取失败</p>
                  <p className="whitespace-pre-wrap text-xs">{item.systemError}</p>
                  <p className="mt-2 text-xs text-red-600">
                    请删除此附件并在对应记录中重新上传文件。
                  </p>
                </div>
              ) : null}
              {item.role === 'user' ? (
                item.content
              ) : (
                <div dangerouslySetInnerHTML={{ __html: renderMarkdown(item.content) }} />
              )}
              {item.role === 'assistant' && item.analysisTemplate ? (
                <AnalysisTemplateCard template={item.analysisTemplate} />
              ) : null}
              {item.role === 'assistant' && Array.isArray(item.fits) && item.fits.length > 0
                ? item.fits.map((fit, fitIndex) => (
                    <FitResultCard key={`${fit.equation}-${fitIndex}`} fit={fit} />
                  ))
                : item.role === 'assistant' && item.fit
                  ? <FitResultCard fit={item.fit} />
                  : null}
              {item.role === 'assistant' && item.chart && !(item.fits?.length || item.fit) ? (
                <ChartResultCard chart={item.chart} />
              ) : null}
            </div>
          </div>
        ))}
        {sending && <p className="text-sm text-slate-400">AI 正在回复…</p>}
        <div ref={bottomRef} />
      </div>
      {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      <div className="mt-3 flex items-end gap-2">
        <textarea
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={onKeyDown}
          rows={3}
          maxLength={2000}
          disabled={sending}
          placeholder="输入问题。Enter 发送，Shift+Enter 换行"
          className="min-h-[4.5rem] flex-1 resize-y rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:bg-slate-100"
        />
        <Button loading={sending} disabled={!draft.trim()} onClick={send}>
          发送
        </Button>
      </div>
    </Surface>
    <AgentHelpDialog open={helpOpen} onClose={() => setHelpOpen(false)} />
    </>
  )
}
