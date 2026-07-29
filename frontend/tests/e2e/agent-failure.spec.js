import { expect, test } from '@playwright/test'
import { API_BASE, apiCall, createProjectApi, loginUi, registerAccount, unique } from './helpers'

const TERMINAL = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED', 'LIMIT_EXCEEDED', 'INVALID_OUTPUT'])

async function createScenarioRun(request, owner, projectId, scenario) {
  return apiCall(request, 'POST', `/projects/${projectId}/agent-runs`, owner.token, {
    artifactKind: 'PROJECT_PROGRESS',
    focus: `__FAKE_SCENARIO__:${scenario}`,
  }, { 'Idempotency-Key': unique(`agent-${scenario}`) })
}

async function waitForRun(request, owner, runId) {
  const deadline = Date.now() + 30_000
  while (Date.now() < deadline) {
    const response = await request.get(`${API_BASE}/agent-runs/${runId}`, {
      headers: { Authorization: `Bearer ${owner.token}` },
    })
    expect(response.ok()).toBeTruthy()
    const run = (await response.json()).data
    if (TERMINAL.has(run.status)) return run
    await new Promise((resolve) => setTimeout(resolve, 150))
  }
  throw new Error(`Agent run ${runId} did not become terminal`)
}

test('fake failure modes and cancellation never create an empty success artifact', async ({ page, request }) => {
  const owner = await registerAccount(request, 'Agent Failure Owner')
  const project = await createProjectApi(request, owner, unique('agent-failure-project'))
  const expected = [
    ['invalid-output', 'INVALID_OUTPUT', 'AGENT_INVALID_OUTPUT'],
    ['unknown-tool', 'FAILED', 'AGENT_UNKNOWN_TOOL'],
    ['timeout', 'FAILED', 'AGENT_TIMEOUT'],
    ['evidence-invalid', 'INVALID_OUTPUT', 'AGENT_EVIDENCE_INVALID'],
  ]

  const runs = []
  for (const [scenario, status, errorCode] of expected) {
    const created = await createScenarioRun(request, owner, project.id, scenario)
    const run = await waitForRun(request, owner, created.id)
    expect(run.status).toBe(status)
    expect(run.errorCode).toBe(errorCode)
    expect(run.artifactId).toBeNull()
    runs.push(run)
  }

  const cancellable = await createScenarioRun(request, owner, project.id, 'invalid-output')
  const cancelled = await apiCall(request, 'POST', `/agent-runs/${cancellable.id}/cancel`, owner.token)
  expect(cancelled.status).toBe('CANCELLED')
  expect((await waitForRun(request, owner, cancellable.id)).artifactId).toBeNull()

  await loginUi(page, owner)
  await page.goto(`/projects/${project.id}?tab=progress&run=${runs[0].id}`)
  await expect(page.getByText('模型结果未通过结构或证据校验', { exact: true })).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('项目进展证据摘要')).toHaveCount(0)

  await page.goto(`/projects/${project.id}?tab=progress&run=${runs[2].id}`)
  await expect(page.getByText('生成失败，可查看原因并重试')).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('模型服务或运行超时，未保存报告。')).toBeVisible()

  await page.goto(`/projects/${project.id}?tab=progress&run=${cancelled.id}`)
  await expect(page.getByText('已取消')).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('项目进展证据摘要')).toHaveCount(0)
})
