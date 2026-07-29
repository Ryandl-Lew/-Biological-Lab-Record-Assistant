import { useCallback, useEffect, useState } from 'react'
import { createRecordAgentRun, fetchAgentArtifact, fetchAgentRun, fetchRecordArtifacts } from '@/api'
import { AGENT_ARTIFACT_KIND } from '@/api/agentTypes'
import { Button, EmptyState, Surface } from '@/components/ui'
import { useAuthStore } from '@/store/authStore'
import AgentArtifactView from './AgentArtifactView'
import AgentRunStatus from './AgentRunStatus'
import AgentTraceViewer from './AgentTraceViewer'
import RecordChatPanel from './RecordChatPanel'
import { agentErrorMessage } from './messages'

const newKey = () => globalThis.crypto?.randomUUID?.() || `summary-${Date.now()}-${Math.random()}`

export default function RecordSummaryPanel({ record, runId, onRunId, onEvidence }) {
  const currentUser = useAuthStore((state) => state.currentUser)
  const [artifacts, setArtifacts] = useState([]), [artifact, setArtifact] = useState(null), [artifactRun, setArtifactRun] = useState(null), [error, setError] = useState(''), [creating, setCreating] = useState(false), [traceRun, setTraceRun] = useState(null)
  const load = useCallback(async () => { try { const result = await fetchRecordArtifacts(record.id); setArtifacts(result.items); if (result.items[0]) { const detail = await fetchAgentArtifact(result.items[0].id); setArtifact(detail); setArtifactRun(await fetchAgentRun(detail.runId)) } setError('') } catch (requestError) { setError(agentErrorMessage(requestError)) } }, [record.id])
  useEffect(() => { load() }, [load])
  const create = async () => { setCreating(true); setError(''); try { const run = await createRecordAgentRun(record.id, { artifactKind: AGENT_ARTIFACT_KIND.RECORD_SUMMARY }, newKey()); onRunId(run.id) } catch (requestError) { setError(agentErrorMessage(requestError)) } finally { setCreating(false) } }
  const select = async (summary) => { try { const detail = await fetchAgentArtifact(summary.id); setArtifact(detail); setArtifactRun(await fetchAgentRun(detail.runId)) } catch (requestError) { setError(agentErrorMessage(requestError)) } }
  const creator = currentUser?.id === record.creatorId
  return <div className="space-y-5">
    <Surface title="AI 记录总结" extra={creator ? <Button loading={creating} onClick={create}>{artifacts.length ? '重新生成' : '生成总结'}</Button> : null}><p className="text-sm text-slate-500">总结来自受限只读工具并经过 Evidence 校验，不会修改记录、审核或历史版本。</p>{record.currentRevisionNo === 0 && <p className="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">尚无正式提交版本；报告只能总结当前字段，并会明确版本演进限制。</p>}{error && <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}</Surface>
    <AgentRunStatus runId={runId} onRunId={onRunId} onSucceeded={load} onTrace={setTraceRun} />
    <AgentArtifactView artifact={artifact} run={artifactRun} onEvidence={onEvidence} />
    {artifacts.length > 1 && <Surface title="历史总结"><div className="divide-y">{artifacts.map((item) => <button key={item.id} className="flex w-full items-center justify-between py-3 text-left text-sm" onClick={() => select(item)}><span><b>{item.headline}</b><span className="ml-2 text-slate-400">{new Date(item.createdAt).toLocaleString()}</span></span><span className="text-brand-600">查看</span></button>)}</div></Surface>}
    {!creator && !artifacts.length && <EmptyState title="尚无记录总结" description="只有记录创建者可以生成；项目成员可查看已经验证的总结。" />}
    <RecordChatPanel record={record} />
    <AgentTraceViewer run={traceRun} open={Boolean(traceRun)} onClose={() => setTraceRun(null)} />
  </div>
}
