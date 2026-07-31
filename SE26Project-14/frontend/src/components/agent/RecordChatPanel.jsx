import { useEffect, useRef, useState } from 'react'
import { History, MessageSquarePlus, Trash2 } from 'lucide-react'
import {
  sendRecordAgentChat,
  listRecordSessions,
  getSession,
  saveRecordSession,
  appendSessionMessages,
  deleteSession,
} from '@/api/agentChat'
import { Button, Surface } from '@/components/ui'
import { renderMarkdown } from '@/lib/renderMarkdown'
import { agentErrorMessage } from './messages'

function AnalysisTemplateCard({ template }) {
  if (!template) return null
  return (
    <div className="mt-2 rounded-xl border border-indigo-200 bg-indigo-50/80 p-3 text-slate-800">
      <p className="text-xs font-semibold uppercase tracking-wide text-indigo-700">分析模板</p>
      <p className="mt-1 text-sm font-medium">
        {template.label} <span className="font-mono text-xs text-slate-500">({template.id})</span>
      </p>
      {template.description ? (
        <p className="mt-1 text-xs text-slate-600">{template.description}</p>
      ) : null}
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

export default function RecordChatPanel({ record, initialMessages, onAutoSave, onNewChat }) {
  const [messages, setMessages] = useState([])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [showHistory, setShowHistory] = useState(false)
  const [sessions, setSessions] = useState([])
  const bottomRef = useRef(null)

  useEffect(() => {
    setMessages(initialMessages || [])
    setDraft('')
    setError('')
  }, [record.id, initialMessages])

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
      if (onAutoSave) {
        const savedMessages = [
          ...messages,
          { role: 'user', content: message },
          { role: 'assistant', content: result.reply },
        ]
        onAutoSave(savedMessages.filter((m) => m.role === 'user' || m.role === 'assistant'))
      }
    } catch (requestError) {
      setError(agentErrorMessage(requestError))
      setMessages((current) => current.slice(0, -1))
      if (!text) setDraft(message)
    } finally {
      setSending(false)
    }
  }

  const handleLoadSessions = async () => {
    try {
      const result = await listRecordSessions(record.id)
      setSessions(result)
      setShowHistory(!showHistory)
    } catch (e) {}
  }

  const handleLoadSession = async (sessionId) => {
    try {
      const detail = await getSession(sessionId)
      setMessages(detail.messages)
      setShowHistory(false)
      setShowQuickStart(false)
    } catch (e) {
      setError(e.message || '加载失败')
    }
  }

  const handleDeleteSession = async (sessionId) => {
    try {
      await deleteSession(sessionId)
      const result = await listRecordSessions(record.id)
      setSessions(result)
    } catch (e) {
      setError(e.message || '删除失败')
    }
  }

  const handleNewChat = () => {
    setMessages([])
    setShowHistory(false)
    setShowQuickStart(true)
    if (onNewChat) onNewChat()
  }

  const onKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      send()
    }
  }

  return (
    <Surface
      title="记录问答"
      extra={
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" icon={MessageSquarePlus} onClick={handleNewChat}>
            新对话
          </Button>
          <Button variant="secondary" size="sm" icon={History} onClick={handleLoadSessions}>
            对话历史
          </Button>
        </div>
      }
    >
      {showHistory && (
        <div className="mb-4 rounded-xl border border-slate-200 p-4">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-semibold text-slate-900">对话历史</h3>
            <button
              onClick={() => setShowHistory(false)}
              className="text-xs text-slate-400 hover:text-slate-600"
            >
              关闭
            </button>
          </div>
          {sessions.length === 0 ? (
            <p className="py-4 text-center text-sm text-slate-400">暂无保存的对话</p>
          ) : (
            <div className="divide-y">
              {sessions.map((s) => (
                <div key={s.id} className="flex items-center justify-between py-2">
                  <button onClick={() => handleLoadSession(s.id)} className="flex-1 text-left">
                    <p className="truncate text-sm font-medium text-slate-900">{s.title}</p>
                    <p className="mt-0.5 text-xs text-slate-400">
                      {s.messageCount} 条消息 · {new Date(s.updatedAt).toLocaleString()}
                    </p>
                  </button>
                  <Button
                    variant="danger"
                    size="sm"
                    icon={Trash2}
                    onClick={() => handleDeleteSession(s.id)}
                  />
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Chat messages */}
      <div className="mt-4 max-h-[65vh] min-h-[24rem] space-y-3 overflow-y-auto rounded-lg border border-slate-200 bg-slate-50 p-3">
        {messages.length === 0 && !sending && (
          <p className="py-6 text-center text-sm text-slate-400">
            可以向 AI 询问这条记录的状态、目的或字段含义； 或点击上方快捷按钮一键生成结构化分析。
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
              {item.role === 'user' ? (
                item.content
              ) : (
                <div dangerouslySetInnerHTML={{ __html: renderMarkdown(item.content) }} />
              )}
              {item.role === 'assistant' && item.analysisTemplate ? (
                <AnalysisTemplateCard template={item.analysisTemplate} />
              ) : null}
            </div>
          </div>
        ))}
        {sending && <p className="text-sm text-slate-400">AI 正在回复…</p>}
        <div ref={bottomRef} />
      </div>
      {error && (
        <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

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
