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

async function updateRecord(request, account, record, values) {
  return apiCall(request, 'PUT', `/records/${record.id}`, account.token, {
    version: record.version,
    title: values.title,
    experimentType: record.experimentType,
    experimentDate: record.experimentDate,
    purpose: values.purpose,
    fieldValues: {},
    contentJson: {
      type: 'doc',
      content: [{ type: 'paragraph', content: [{ type: 'text', text: values.body }] }],
    },
    contentHtml: `<p>${values.body}</p>`,
  })
}

test('R1/R2 domain diff, restore preview, safe restore and R3 submission', async ({
  page,
  request,
}) => {
  const creator = await registerAccount(request, 'Phase2 Creator')
  const reviewer = await registerAccount(request, 'Phase2 Reviewer')
  const project = await createProjectApi(request, creator, unique('phase2-version-project'))
  await inviteAndAcceptApi(request, creator, project.id, reviewer)
  await apiCall(
    request,
    'PATCH',
    `/projects/${project.id}/members/${reviewer.user.id}/role`,
    creator.token,
    { role: 'REVIEWER', reassignments: {} },
  )
  let record = await createRecordApi(request, creator, project.id, '版本恢复演示记录')
  record = await updateRecord(request, creator, record, {
    title: 'R1 标题',
    purpose: 'R1 实验目的',
    body: 'R1 正文内容',
  })
  const r1 = await apiCall(
    request,
    'POST',
    `/records/${record.id}/submissions`,
    creator.token,
    { reviewerId: reviewer.user.id, submitNote: '提交 R1', expectedRecordVersion: record.version },
    { 'Idempotency-Key': unique('submit-r1') },
  )
  await apiCall(
    request,
    'POST',
    `/records/${record.id}/reviews/${r1.review.id}/request-changes`,
    reviewer.token,
    { comment: '请补充对照组' },
  )
  record = await apiCall(request, 'GET', `/records/${record.id}`, creator.token)
  record = await updateRecord(request, creator, record, {
    title: 'R2 标题',
    purpose: 'R2 补充对照组',
    body: 'R2 正文与对照组内容',
  })
  const r2 = await apiCall(
    request,
    'POST',
    `/records/${record.id}/submissions`,
    creator.token,
    { reviewerId: reviewer.user.id, submitNote: '提交 R2', expectedRecordVersion: record.version },
    { 'Idempotency-Key': unique('submit-r2') },
  )
  await apiCall(
    request,
    'POST',
    `/records/${record.id}/reviews/${r2.review.id}/request-changes`,
    reviewer.token,
    { comment: '请再次复核' },
  )

  await loginUi(page, creator)
  await page.goto(`/records/${record.id}?tab=history`)
  await expect(page.getByText('版本历史 2')).toBeVisible()
  await page.getByLabel('基准版本').selectOption(r1.id)
  await page.getByLabel('目标版本').selectOption(r2.id)
  await page.getByRole('button', { name: '查看结构化差异' }).click()
  await expect(page.getByText('R1 标题')).toBeVisible()
  await expect(
    page.locator('article').filter({ hasText: '标题' }).getByText('R2 标题'),
  ).toBeVisible()

  const r1Row = page.locator('li').filter({ hasText: 'R1' }).filter({ hasText: '提交 R1' })
  await r1Row.getByRole('button', { name: '恢复为工作副本' }).click()
  await expect(page.getByRole('dialog', { name: '恢复预览' })).toContainText(
    '历史版本不会删除或修改',
  )
  await page.getByRole('button', { name: '确认恢复工作副本' }).click()
  await expect(page).toHaveURL(new RegExp(`/records/${record.id}/edit$`))
  await expect(page.getByText('已从 R1 恢复工作副本，历史版本未改变。')).toBeVisible()

  const r1After = await apiCall(
    request,
    'GET',
    `/records/${record.id}/revisions/${r1.id}`,
    creator.token,
  )
  const r2After = await apiCall(
    request,
    'GET',
    `/records/${record.id}/revisions/${r2.id}`,
    creator.token,
  )
  expect(r1After.snapshot.title).toBe('R1 标题')
  expect(r2After.snapshot.title).toBe('R2 标题')
  record = await apiCall(request, 'GET', `/records/${record.id}`, creator.token)
  expect(record.title).toBe('R1 标题')
  await apiCall(
    request,
    'POST',
    `/records/${record.id}/submissions`,
    creator.token,
    {
      reviewerId: reviewer.user.id,
      submitNote: '恢复后提交 R3',
      expectedRecordVersion: record.version,
    },
    { 'Idempotency-Key': unique('submit-r3') },
  )
  const revisions = await apiCall(request, 'GET', `/records/${record.id}/revisions`, creator.token)
  expect(revisions).toHaveLength(3)
})
