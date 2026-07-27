import { request } from './client'

const json = (method, body, idempotencyKey) => ({
  method,
  headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
  body: body === undefined ? undefined : JSON.stringify(body),
})

export const createRecordAgentRun = (recordId, input, idempotencyKey) =>
  request(`/records/${recordId}/agent-runs`, json('POST', input, idempotencyKey))

export const createProjectAgentRun = (projectId, input, idempotencyKey) =>
  request(`/projects/${projectId}/agent-runs`, json('POST', input, idempotencyKey))

export const fetchAgentRun = (runId) => request(`/agent-runs/${runId}`)

export const fetchAgentSteps = (runId, { page = 0, size = 50 } = {}) =>
  request(`/agent-runs/${runId}/steps?page=${page}&size=${size}`)

export const cancelAgentRun = (runId) => request(`/agent-runs/${runId}/cancel`, json('POST'))

export const rerunAgent = (runId, idempotencyKey) =>
  request(`/agent-runs/${runId}/rerun`, json('POST', undefined, idempotencyKey))

export const fetchRecordArtifacts = (recordId, { page = 0, size = 20 } = {}) =>
  request(`/records/${recordId}/agent-artifacts?page=${page}&size=${size}`)

export const fetchProjectArtifacts = (projectId, { page = 0, size = 20 } = {}) =>
  request(`/projects/${projectId}/agent-artifacts?page=${page}&size=${size}`)

export const fetchAgentArtifact = (artifactId) => request(`/agent-artifacts/${artifactId}`)
