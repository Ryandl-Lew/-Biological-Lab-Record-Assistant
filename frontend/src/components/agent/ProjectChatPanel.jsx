import { useEffect, useRef, useState } from 'react'
import { CircleHelp, Paperclip, X } from 'lucide-react'
import {
  deleteProjectAgentReference,
  sendProjectAgentChat,
  uploadProjectAgentReference,
} from '@/api/agentChat'
import { Button, Surface } from '@/components/ui'
import { agentErrorMessage, trimChatHistory } from './messages'
import DataChart, { chartFromFit } from './DataChart'
import AgentHelpDialog from './AgentHelpDialog'

function formatNum(value) {
  if (value == null || Number.isNaN(Number(value))) return '—'
  return Number(value).toFixed(4)
}

function isConfirmPhrase(text) {
  const value = (text || '').trim().toLowerCase()
  return (
    value === '确认' ||
    value === '确认拟合' ||
    value === '用这个' ||
    value === '按方案拟合' ||
    value.includes('确认按拟定方案') ||
    value.includes('进行拟合')
  )
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

function FitProposalCard({ proposal, onConfirm, confirming }) {
  if (!proposal) return null
  const mode = proposal.multivariate
    ? '多元线性回归'
    : proposal.autoCompare
      ? `多模型比选（${(proposal.candidateIds || []).join(' / ') || '预置目录'}）`
      : `单方程 ${proposal.equation || '—'}`
  return (
    <div className="mt-2 rounded-xl border border-sky-200 bg-sky-50/80 p-3 text-slate-800">
      <p className="text-xs font-semibold uppercase tracking-wide text-sky-700">拟定拟合方案</p>
      <p className="mt-1 text-sm">{mode}</p>
      <dl className="mt-2 space-y-1 text-xs text-slate-600">
        <div>
          <span className="text-slate-400">映射：</span>x={proposal.xSpec || '—'}, y={proposal.ySpec || '—'}
        </div>
        <div>
          <span className="text-slate-400">来源：</span>
          {proposal.pointSource || 'AUTO'}
          {proposal.csvNameHint ? `（${proposal.csvNameHint}）` : ''}
        </div>
        {proposal.timeToMinutes ? (
          <div>
            <span className="text-slate-400">时间：</span>转换为相对分钟（最早为 0）
          </div>
        ) : null}
        {(proposal.recordCodes || []).length > 0 && (
          <div>
            <span className="text-slate-400">记录：</span>
            {proposal.recordCodes.join(', ')}
          </div>
        )}
        {(proposal.statuses || []).length > 0 && (
          <div>
            <span className="text-slate-400">状态：</span>
            {proposal.statuses.join(', ')}
          </div>
        )}
      </dl>
      {proposal.rationale && <p className="mt-2 text-xs text-slate-600">{proposal.rationale}</p>}
      <div className="mt-3">
        <Button loading={confirming} disabled={confirming} onClick={onConfirm}>
          确认拟合
        </Button>
      </div>
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

export default function ProjectChatPanel({ project, variant = 'embedded' }) {
  const [messages, setMessages] = useState([])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [pendingProposal, setPendingProposal] = useState(null)
  const [references, setReferences] = useState([])
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(null)
  const [helpOpen, setHelpOpen] = useState(false)
  const bottomRef = useRef(null)
  const fileInputRef = useRef(null)
  const tall = variant === 'page'

  useEffect(() => {
    setMessages([])
    setDraft('')
    setError('')
    setPendingProposal(null)
    setReferences([])
    setUploading(false)
    setUploadProgress(null)
  }, [project.id])

  useEffect(() => {
    if (typeof bottomRef.current?.scrollIntoView === 'function') {
      bottomRef.current.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  }, [messages, sending, pendingProposal, references])

  const sendPayload = async (text, fitConfirm = null, keepReferences = false) => {
    const message = (text || '').trim() || (references.length > 0 ? '请参考我关联的文件。' : '')
    if (!message || sending) return
    setSending(true)
    setError('')
    if (!fitConfirm) setDraft('')
    const history = trimChatHistory(messages)
    const referenceIds = references.map((item) => item.id)
    const attachedNames = references.map((item) => item.filename)
    setMessages((current) => [
      ...current,
      {
        role: 'user',
        content: attachedNames.length
          ? `${message}\n\n[关联文件: ${attachedNames.join('、')}]`
          : message,
      },
    ])
    try {
      const body = { message, history }
      if (fitConfirm) body.fitConfirm = fitConfirm
      if (referenceIds.length > 0) body.referenceIds = referenceIds
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
          fit: result.fit || null,
          fits,
          proposal: result.proposal || null,
          analysisTemplate: result.analysisTemplate || null,
          chart: result.chart || null,
        },
      ])
      if (result.proposal) {
        setPendingProposal(result.proposal)
      } else if (fits.length > 0) {
        setPendingProposal(null)
      }
      if (!keepReferences) setReferences([])
    } catch (requestError) {
      setError(agentErrorMessage(requestError))
      setMessages((current) => current.slice(0, -1))
      if (!fitConfirm) setDraft(text)
    } finally {
      setSending(false)
    }
  }

  const send = async () => {
    const text = draft.trim()
    if (!text && references.length === 0) return
    if (isConfirmPhrase(text) && pendingProposal) {
      await sendPayload(text, pendingProposal, true)
      return
    }
    await sendPayload(text)
  }

  const confirmProposal = async (proposal) => {
    await sendPayload('确认按拟定方案拟合', proposal || pendingProposal, true)
  }

  const onPickFile = async (event) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    if (references.length >= 5) {
      setError('单次最多关联 5 个参考文件')
      return
    }
    setUploading(true)
    setUploadProgress(0)
    setError('')
    try {
      const uploaded = await uploadProjectAgentReference(project.id, file, setUploadProgress)
      setReferences((current) => [...current, uploaded])
    } catch (requestError) {
      setError(agentErrorMessage(requestError))
    } finally {
      setUploading(false)
      setUploadProgress(null)
    }
  }

  const removeReference = async (reference) => {
    setReferences((current) => current.filter((item) => item.id !== reference.id))
    try {
      await deleteProjectAgentReference(project.id, reference.id)
    } catch {
      // local remove still ok if server delete fails
    }
  }

  const onKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      send()
    }
  }

  return (
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
      <AgentHelpDialog open={helpOpen} onClose={() => setHelpOpen(false)} />
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
              className={`max-w-[85%] whitespace-pre-wrap rounded-2xl px-3 py-2 text-sm ${
                item.role === 'user'
                  ? 'bg-brand-600 text-white'
                  : 'border border-slate-200 bg-white text-slate-800'
              }`}
            >
              {item.content}
              {item.role === 'assistant' && item.analysisTemplate ? (
                <AnalysisTemplateCard template={item.analysisTemplate} />
              ) : null}
              {item.role === 'assistant' && item.proposal ? (
                <FitProposalCard
                  proposal={item.proposal}
                  confirming={sending}
                  onConfirm={() => confirmProposal(item.proposal)}
                />
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
        {pendingProposal && !messages.some((item) => item.proposal === pendingProposal) ? (
          <div className="rounded-xl border border-sky-200 bg-white p-3">
            <p className="text-xs text-slate-500">待确认方案仍有效，可点击下方按钮或发送「确认拟合」。</p>
            <FitProposalCard
              proposal={pendingProposal}
              confirming={sending}
              onConfirm={() => confirmProposal(pendingProposal)}
            />
          </div>
        ) : null}
        {sending && <p className="text-sm text-slate-400">AI 正在回复…</p>}
        <div ref={bottomRef} />
      </div>
      {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {references.length > 0 ? (
        <div className="mt-3 flex flex-wrap gap-2">
          {references.map((item) => (
            <span
              key={item.id}
              className="inline-flex max-w-full items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs text-slate-700"
            >
              <Paperclip size={12} className="shrink-0 text-slate-400" />
              <span className="truncate">{item.filename}</span>
              <button
                type="button"
                aria-label={`移除 ${item.filename}`}
                className="rounded-full p-0.5 text-slate-400 hover:bg-slate-200 hover:text-slate-700"
                onClick={() => removeReference(item)}
                disabled={sending || uploading}
              >
                <X size={12} />
              </button>
            </span>
          ))}
        </div>
      ) : null}
      {typeof uploadProgress === 'number' ? (
        <p className="mt-2 text-xs text-slate-400">上传中 {uploadProgress}%</p>
      ) : null}
      <div className="mt-3 flex items-end gap-2">
        <input
          ref={fileInputRef}
          type="file"
          className="hidden"
          accept=".csv,.xlsx,.txt,.md,.pdf,.png,.jpg,.jpeg,.webp,.docx"
          onChange={onPickFile}
        />
        <Button
          type="button"
          variant="secondary"
          disabled={sending || uploading || references.length >= 5}
          onClick={() => fileInputRef.current?.click()}
          aria-label="关联本机文件"
        >
          <span className="inline-flex items-center gap-1">
            <Paperclip size={16} />
            文件
          </span>
        </Button>
        <textarea
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={onKeyDown}
          rows={3}
          maxLength={2000}
          disabled={sending}
          placeholder="输入问题；可点「文件」关联本机文件供 Agent 参考。Enter 发送，Shift+Enter 换行"
          className="min-h-[4.5rem] flex-1 resize-y rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:bg-slate-100"
        />
        <Button loading={sending} disabled={(!draft.trim() && references.length === 0) || uploading} onClick={send}>
          发送
        </Button>
      </div>
    </Surface>
  )
}
