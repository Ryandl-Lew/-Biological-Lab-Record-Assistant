import { expect, test } from '@playwright/test'
import {
  apiCall,
  createProjectApi,
  createRecordApi,
  inviteAndAcceptApi,
  loginUi,
  registerAccount,
  unique,
} from './helpers'

async function prepareTwoRevisions(request, creator, reviewer, projectId) {
  let record = await createRecordApi(request, creator, projectId, 'Agent Evidence 记录')
  record = await apiCall(request, 'PUT', `/records/${record.id}`, creator.token, {
    version: record.version,
    title: 'Agent R1',
    experimentType: record.experimentType,
    experimentDate: record.experimentDate,
    purpose: '第一版',
    fieldValues: {},
    contentJson: {},
    contentHtml: '<p>第一版</p>',
  })
  const r1 = await apiCall(
    request,
    'POST',
    `/records/${record.id}/submissions`,
    creator.token,
    { reviewerId: reviewer.user.id, expectedRecordVersion: record.version },
    { 'Idempotency-Key': unique('agent-r1') },
  )
  await apiCall(
    request,
    'POST',
    `/records/${record.id}/reviews/${r1.review.id}/request-changes`,
    reviewer.token,
    { comment: '补充第二版' },
  )
  record = await apiCall(request, 'GET', `/records/${record.id}`, creator.token)
  record = await apiCall(request, 'PUT', `/records/${record.id}`, creator.token, {
    version: record.version,
    title: 'Agent R2',
    experimentType: record.experimentType,
    experimentDate: record.experimentDate,
    purpose: '第二版',
    fieldValues: {},
    contentJson: {},
    contentHtml: '<p>第二版</p>',
  })
  const r2 = await apiCall(
    request,
    'POST',
    `/records/${record.id}/submissions`,
    creator.token,
    { reviewerId: reviewer.user.id, expectedRecordVersion: record.version },
    { 'Idempotency-Key': unique('agent-r2') },
  )
  await apiCall(
    request,
    'POST',
    `/records/${record.id}/reviews/${r2.review.id}/request-changes`,
    reviewer.token,
    { comment: '等待总结' },
  )
  return record.id
}

test('fake provider creates project/record artifacts, evidence navigation and trace replay', async ({
  page,
  request,
}) => {
  const owner = await registerAccount(request, 'Agent E2E Owner')
  const reviewer = await registerAccount(request, 'Agent E2E Reviewer')
  const project = await createProjectApi(request, owner, unique('agent-project'))
  await inviteAndAcceptApi(request, owner, project.id, reviewer)
  await apiCall(
    request,
    'PATCH',
    `/projects/${project.id}/members/${reviewer.user.id}/role`,
    owner.token,
    { role: 'REVIEWER', reassignments: {} },
  )
  const recordId = await prepareTwoRevisions(request, owner, reviewer, project.id)

  await loginUi(page, owner)
  await page.goto(`/projects/${project.id}?tab=progress`)
  await page.getByRole('button', { name: '生成进展报告' }).click()
  await expect(page.getByText('项目进展证据摘要')).toBeVisible({ timeout: 30_000 })
  await page.getByRole('button', { name: '查看运行轨迹' }).click()
  await expect(page.getByRole('dialog', { name: 'Agent 运行轨迹' })).toContainText(
    'Replay 仅逐步展开已保存的脱敏步骤',
  )
  await page.getByRole('button', { name: '展开全部' }).click()
  await expect(page.getByRole('dialog', { name: 'Agent 运行轨迹' })).toContainText(
    'get_project_overview',
  )
  await expect(
    page
      .getByRole('dialog', { name: 'Agent 运行轨迹' })
      .getByText(/#\d+ TOOL_CALL · list_project_activity/),
  ).toBeVisible()
  await page.getByRole('button', { name: '关闭' }).click()

  await page.goto(`/records/${recordId}?tab=summary`)
  await page.getByRole('button', { name: '生成总结' }).click()
  await expect(page.getByText('记录证据摘要')).toBeVisible({ timeout: 30_000 })
  const diffEvidence = page.getByRole('button', { name: /查看证据：Revision diff/ }).last()
  await expect(diffEvidence).toBeVisible()
  await diffEvidence.click()
  await expect(page).toHaveURL(/tab=history.*from=.*to=/)
  await expect(page.getByText('Agent R1')).toBeVisible()
  await expect(page.getByText('Agent R2', { exact: true }).last()).toBeVisible()
})
