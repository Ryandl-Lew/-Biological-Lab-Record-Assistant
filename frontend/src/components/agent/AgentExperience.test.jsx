import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cancelAgentRun, fetchAgentRun, fetchAgentSteps, rerunAgent } from '@/api'
import AgentArtifactView from './AgentArtifactView'
import AgentRunStatus from './AgentRunStatus'
import AgentTraceViewer from './AgentTraceViewer'

vi.mock('@/api', () => ({ fetchAgentRun: vi.fn(), fetchAgentSteps: vi.fn(), cancelAgentRun: vi.fn(), rerunAgent: vi.fn() }))

describe('Agent experience', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(() => vi.useRealTimers())
  it('renders report sections, basis labels and evidence navigation', async () => {
    const navigate = vi.fn(), evidence = { ref: 'e1', type: 'REVISION_DIFF', id: 'diff', recordId: 'record', fromRevisionId: 'r1', toRevisionId: 'r2', label: 'R1 到 R2' }
    render(<AgentArtifactView artifact={{ artifactKind: 'RECORD_SUMMARY', createdAt: '2026-07-27T00:00:00Z', content: { headline: '记录总结', executiveSummary: '已验证摘要', period: { start: 'a', end: 'b' }, progress: [{ id: 'p1', statement: '版本已变化', evidenceRefs: ['e1'] }], risks: [{ id: 'risk', statement: '仍需复核', severity: 'HIGH', evidenceRefs: ['e1'] }], nextActions: [{ id: 'a1', statement: '补充对照', basis: 'REVIEW_FEEDBACK', evidenceRefs: ['e1'] }], evidence: [evidence], limitations: ['未读取附件正文'] } }} onEvidence={navigate} />)
    expect(screen.getByText('审核反馈')).toBeInTheDocument()
    expect(screen.getByText('未读取附件正文')).toBeInTheDocument()
    await userEvent.click(screen.getAllByRole('button', { name: /查看证据/ })[0])
    expect(navigate).toHaveBeenCalledWith(evidence)
  })

  it('polls active runs, stops at success and reports the artifact', async () => {
    vi.useFakeTimers()
    fetchAgentRun.mockResolvedValueOnce({ id: 'run', status: 'QUEUED' }).mockResolvedValueOnce({ id: 'run', status: 'SUCCEEDED', provider: 'fake', model: 'deterministic' })
    const success = vi.fn()
    render(<AgentRunStatus runId="run" onRunId={vi.fn()} onSucceeded={success} onTrace={vi.fn()} />)
    await act(async () => { await Promise.resolve() })
    expect(screen.getByText('等待处理')).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(2000) })
    expect(screen.getByText('已生成')).toBeInTheDocument()
    expect(success).toHaveBeenCalledTimes(1)
    expect(fetchAgentRun).toHaveBeenCalledTimes(2)
  })

  it('replays saved trace without exposing secrets or hidden reasoning', async () => {
    fetchAgentSteps.mockResolvedValue({ items: [{ id: 's1', stepNo: 1, stepType: 'TOOL_CALL', toolName: 'get_record_overview', latencyMs: 4, request: { recordId: 'r1', apiKey: 'secret' }, response: { count: 1, reasoning: 'hidden' } }], meta: { page: 0 } })
    render(<AgentTraceViewer open run={{ id: 'run', status: 'SUCCEEDED', triggerType: 'MANUAL', provider: 'fake', model: 'deterministic', inputTokens: 4, outputTokens: 8 }} onClose={vi.fn()} />)
    expect(await screen.findByText(/get_record_overview/)).toBeInTheDocument()
    await userEvent.click(screen.getByText(/#1 TOOL_CALL/))
    await waitFor(() => expect(screen.queryByText(/secret|hidden/)).not.toBeInTheDocument())
    expect(screen.getByText(/不会重新调用模型或工具/)).toBeInTheDocument()
  })

  it('cancels active runs, reruns terminal failures and clears polling on unmount', async () => {
    const clear = vi.spyOn(globalThis, 'clearTimeout')
    fetchAgentRun.mockResolvedValue({ id: 'run', status: 'QUEUED' })
    cancelAgentRun.mockResolvedValue({ id: 'run', status: 'CANCELLED' })
    const first = render(<AgentRunStatus runId="run" onRunId={vi.fn()} onTrace={vi.fn()} />)
    await screen.findByText('等待处理')
    await userEvent.click(screen.getByRole('button', { name: '取消' }))
    expect(cancelAgentRun).toHaveBeenCalledWith('run')
    first.unmount()
    expect(clear).toHaveBeenCalled()

    fetchAgentRun.mockResolvedValue({ id: 'failed', status: 'FAILED', errorCode: 'MODEL_PROVIDER_UNAVAILABLE', errorMessage: '暂不可用' })
    rerunAgent.mockResolvedValue({ id: 'rerun', status: 'QUEUED' })
    const onRunId = vi.fn()
    render(<AgentRunStatus runId="failed" onRunId={onRunId} onTrace={vi.fn()} />)
    await userEvent.click(await screen.findByRole('button', { name: '重新运行' }))
    expect(rerunAgent).toHaveBeenCalledWith('failed', expect.any(String))
    expect(onRunId).toHaveBeenCalledWith('rerun')
  })
})
