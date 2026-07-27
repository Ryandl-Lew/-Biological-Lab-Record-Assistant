import { useCallback, useEffect, useState } from 'react'
import { createProjectAgentRun, fetchAgentArtifact, fetchAgentRun, fetchProjectArtifacts } from '@/api'
import { AGENT_ARTIFACT_KIND } from '@/api/agentTypes'
import { Button, Surface } from '@/components/ui'
import AgentArtifactView from './AgentArtifactView'
import AgentRunStatus from './AgentRunStatus'
import AgentTraceViewer from './AgentTraceViewer'
import { agentErrorMessage } from './messages'

const date = (offset = 0) => { const value = new Date(); value.setDate(value.getDate() + offset); return value.toISOString().slice(0, 10) }
const newKey = () => globalThis.crypto?.randomUUID?.() || `progress-${Date.now()}-${Math.random()}`

export default function ProgressReportPanel({ project, runId, onRunId, onEvidence }) {
  const [periodStart, setPeriodStart] = useState(date(-7)), [periodEnd, setPeriodEnd] = useState(date())
  const [artifacts, setArtifacts] = useState([]), [artifact, setArtifact] = useState(null), [artifactRun, setArtifactRun] = useState(null), [error, setError] = useState(''), [creating, setCreating] = useState(false), [traceRun, setTraceRun] = useState(null)
  const load = useCallback(async () => { try { const result = await fetchProjectArtifacts(project.id); setArtifacts(result.items); if (result.items[0]) { const detail = await fetchAgentArtifact(result.items[0].id); setArtifact(detail); setArtifactRun(await fetchAgentRun(detail.runId)) } setError('') } catch (requestError) { setError(agentErrorMessage(requestError)) } }, [project.id])
  useEffect(() => { load() }, [load])
  const create = async () => { setCreating(true); setError(''); try { const run = await createProjectAgentRun(project.id, { artifactKind: AGENT_ARTIFACT_KIND.PROJECT_PROGRESS, periodStart: `${periodStart}T00:00:00Z`, periodEnd: `${periodEnd}T23:59:59Z` }, newKey()); onRunId(run.id) } catch (requestError) { setError(agentErrorMessage(requestError)) } finally { setCreating(false) } }
  const select = async (summary) => { try { const detail = await fetchAgentArtifact(summary.id); setArtifact(detail); setArtifactRun(await fetchAgentRun(detail.runId)) } catch (requestError) { setError(agentErrorMessage(requestError)) } }
  const owner = project.currentUserRole === 'OWNER'
  return <div className="space-y-5">
    <Surface title="智能进展" extra={owner ? <Button loading={creating} onClick={create}>生成进展报告</Button> : null}><p className="text-sm text-slate-500">报告截止时间与实时项目状态分开显示；`COMPLETED` 不代表实验结果成功。</p><div className="mt-4 grid gap-3 sm:grid-cols-2"><label><span className="field-label">开始日期</span><input aria-label="报告开始日期" type="date" className="input" value={periodStart} max={periodEnd} onChange={(event) => setPeriodStart(event.target.value)} /></label><label><span className="field-label">结束日期</span><input aria-label="报告结束日期" type="date" className="input" value={periodEnd} min={periodStart} onChange={(event) => setPeriodEnd(event.target.value)} /></label></div>{!owner && <p className="mt-3 text-sm text-slate-400">仅项目负责人可以生成，所有当前成员可查看已有报告。</p>}{error && <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}</Surface>
    <AgentRunStatus runId={runId} onRunId={onRunId} onSucceeded={load} onTrace={setTraceRun} />
    <AgentArtifactView artifact={artifact} run={artifactRun} onEvidence={onEvidence} />
    {artifacts.length > 1 && <Surface title="历史进展报告"><div className="divide-y">{artifacts.map((item) => <button key={item.id} className="flex w-full items-center justify-between py-3 text-left text-sm" onClick={() => select(item)}><span><b>{item.headline}</b><span className="ml-2 text-slate-400">{new Date(item.createdAt).toLocaleString()}</span></span><span className="text-brand-600">查看</span></button>)}</div></Surface>}
    <AgentTraceViewer run={traceRun} open={Boolean(traceRun)} onClose={() => setTraceRun(null)} />
  </div>
}
