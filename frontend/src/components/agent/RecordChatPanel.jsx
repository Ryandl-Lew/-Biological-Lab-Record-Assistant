import { useEffect, useRef, useState } from 'react'
import { FlaskConical, Microscope, Zap } from 'lucide-react'
import { sendRecordAgentChat } from '@/api/agentChat'
import { Button, Surface } from '@/components/ui'
import { agentErrorMessage } from './messages'

/** Quick-start suggestion chips for common record analysis scenarios. */
const QUICK_STARTS = [
  {
    label: '总结这个实验',
    icon: Microscope,
    prompt: '帮我总结这个实验，包括实验目的、材料、步骤、结果、异常分析和改进建议。',
    color: 'hover:bg-brand-50 hover:text-brand-700 hover:border-brand-300',
  },
  {
    label: '分析实验步骤',
    icon: FlaskConical,
    prompt: '请帮我分析这个实验记录的步骤是否完整，有什么可以改进的地方？',
    color: 'hover:bg-amber-50 hover:text-amber-700 hover:border-amber-300',
  },
  {
    label: '快速摘要',
    icon: Zap,
    prompt: '用一段话简要总结这个实验记录做了什么、结果如何。',
    color: 'hover:bg-emerald-50 hover:text-emerald-700 hover:border-emerald-300',
  },
]

function AnalysisTemplateCard({ template }) {
  if (!template) return null
  return (
    <div className="mt-2 rounded-xl border border-indigo-200 bg-indigo-50/80 p-3 text-slate-800">
      <p className="text-xs font-semibold uppercase tracking-wide text-indigo-700">分析模板</p>
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
    </div>
  )
}

export default function RecordChatPanel({ record }) {
  const [messages, setMessages] = useState([])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [showQuickStart, setShowQuickStart] = useState(true)
  const bottomRef = useRef(null)

  useEffect(() => {
    setMessages([])
    setDraft('')
    setError('')
    setShowQuickStart(true)
  }, [record.id])

  useEffect(() => {
    if (typeof bottomRef.current?.scrollIntoView === 'function') {
      bottomRef.current.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  }, [messages, sending])

  const send = async (text = null) => {
    const message = (text || draft).trim()
    if (!message || sending) return
    setSending(true)
    setError('')
    setShowQuickStart(false)
    if (!text) setDraft('')
    const history = messages.map((item) => ({ role: item.role, content: item.content }))
    setMessages((current) => [...current, { role: 'user', content: message }])
    try {
      const result = await sendRecordAgentChat(record.id, { message, history })
      setMessages((current) => [
        ...current,
        {
          role: 'assistant',
          content: result.reply,
          analysisTemplate: result.analysisTemplate || null,
        },
      ])
    } catch (requestError) {
      setError(agentErrorMessage(requestError))
      setMessages((current) => current.slice(0, -1))
      if (!text) setDraft(message)
    } finally {
      setSending(false)
    }
  }

  const onQuickStart = (prompt) => {
    setDraft(prompt)
    send(prompt)
  }

  const onKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      send()
    }
  }

  return (
    <Surface title="记录问答">
      <p className="text-sm text-slate-500">
        基于当前实验条目的只读上下文问答（含版本历史与审核意见），
        支持结构化分析模板；不会修改记录数据，刷新页面后对话会清空。
      </p>

      {/* Quick-start suggestion chips */}
      {showQuickStart && messages.length === 0 && !sending && (
        <div className="mt-3 flex flex-wrap gap-2">
          {QUICK_STARTS.map((item) => {
            const Icon = item.icon
            return (
              <button
                key={item.label}
                type="button"
                disabled={sending}
                onClick={() => onQuickStart(item.prompt)}
                className={`inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 transition-colors disabled:opacity-50 ${item.color}`}
              >
                <Icon size={14} />
                {item.label}
              </button>
            )
          })}
        </div>
      )}

      {/* Chat messages */}
      <div className="mt-4 max-h-80 space-y-3 overflow-y-auto rounded-lg border border-slate-200 bg-slate-50 p-3">
        {messages.length === 0 && !sending && (
          <p className="py-6 text-center text-sm text-slate-400">
            可以向 AI 询问这条记录的状态、目的或字段含义；
            或点击上方快捷按钮一键生成结构化分析。
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
            </div>
          </div>
        ))}
        {sending && <p className="text-sm text-slate-400">AI 正在回复…</p>}
        <div ref={bottomRef} />
      </div>
      {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      {/* Input area */}
      <div className="mt-3 flex items-end gap-2">
        <textarea
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={onKeyDown}
          rows={3}
          maxLength={2000}
          disabled={sending}
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          className="min-h-[4.5rem] flex-1 resize-y rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:bg-slate-100"
        />
        <Button loading={sending} disabled={!draft.trim()} onClick={() => send()}>
          发送
        </Button>
      </div>
    </Surface>
  )
}
