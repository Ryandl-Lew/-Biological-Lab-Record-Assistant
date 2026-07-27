import { expect, test } from '@playwright/test'
import { API_BASE, apiCall, createProjectApi, createRecordApi, inviteAndAcceptApi, registerAccount, unique } from './helpers'

test('optimistic lock rejects stale saves and submission key is idempotent', async ({ request }) => {
  const creator = await registerAccount(request, 'Conflict Creator')
  const reviewer = await registerAccount(request, 'Conflict Reviewer')
  const project = await createProjectApi(request, creator)
  await inviteAndAcceptApi(request, creator, project.id, reviewer)
  await apiCall(request, 'PATCH', `/projects/${project.id}/members/${reviewer.user.id}/role`, creator.token, { role: 'REVIEWER', reassignments: {} })
  const record = await createRecordApi(request, creator, project.id)

  const first = await apiCall(request, 'PUT', `/records/${record.id}`, creator.token, { version: record.version, title: record.title, experimentType: record.experimentType, experimentDate: record.experimentDate, purpose: '第一次保存', fieldValues: {}, contentJson: {}, contentHtml: '<p>第一次保存</p>' })
  const stale = await request.put(`${API_BASE}/records/${record.id}`, {
    headers: { Authorization: `Bearer ${creator.token}` },
    data: { version: record.version, title: record.title, experimentType: record.experimentType, experimentDate: record.experimentDate, purpose: '过期保存', fieldValues: {}, contentJson: {}, contentHtml: '<p>过期</p>' },
  })
  expect(stale.status()).toBe(409)

  const idempotencyKey = unique('submission')
  const payload = { reviewerId: reviewer.user.id, expectedRecordVersion: first.version }
  const firstSubmit = await request.post(`${API_BASE}/records/${record.id}/submissions`, { headers: { Authorization: `Bearer ${creator.token}`, 'Idempotency-Key': idempotencyKey }, data: payload })
  const secondSubmit = await request.post(`${API_BASE}/records/${record.id}/submissions`, { headers: { Authorization: `Bearer ${creator.token}`, 'Idempotency-Key': idempotencyKey }, data: payload })
  expect(firstSubmit.ok()).toBeTruthy()
  expect(secondSubmit.ok()).toBeTruthy()
  const revisions = await apiCall(request, 'GET', `/records/${record.id}/revisions`, creator.token)
  expect(revisions).toHaveLength(1)
})
