import { beforeEach, describe, expect, it, vi } from 'vitest'
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
} from './agentRuns'

describe('agent runs api', () => {
  beforeEach(() => {
    localStorage.clear()
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue({ ok: true, status: 200, json: async () => ({ data: { id: 'x' } }) })
  })

  it('uses unified client and idempotency headers for writes', async () => {
    await createRecordAgentRun('r1', { artifactKind: 'RECORD_SUMMARY' }, 'key-1')
    await createProjectAgentRun('p1', { artifactKind: 'PROJECT_PROGRESS' }, 'key-2')
    await rerunAgent('run-1', 'key-3')
    await cancelAgentRun('run-1')
    expect(fetch).toHaveBeenNthCalledWith(
      1,
      '/api/v1/records/r1/agent-runs',
      expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
    )
    expect(fetch.mock.calls[0][1].headers.get('Idempotency-Key')).toBe('key-1')
    expect(fetch.mock.calls[1][1].headers.get('Idempotency-Key')).toBe('key-2')
    expect(fetch.mock.calls[2][1].headers.get('Idempotency-Key')).toBe('key-3')
  })

  it('builds run, step and artifact read paths', async () => {
    await fetchAgentRun('run')
    await fetchAgentSteps('run', { page: 1, size: 25 })
    await fetchRecordArtifacts('record')
    await fetchProjectArtifacts('project', { page: 2, size: 10 })
    await fetchAgentArtifact('artifact')
    expect(fetch.mock.calls.map(([url]) => url)).toEqual([
      '/api/v1/agent-runs/run',
      '/api/v1/agent-runs/run/steps?page=1&size=25',
      '/api/v1/records/record/agent-artifacts?page=0&size=20',
      '/api/v1/projects/project/agent-artifacts?page=2&size=10',
      '/api/v1/agent-artifacts/artifact',
    ])
  })
})
