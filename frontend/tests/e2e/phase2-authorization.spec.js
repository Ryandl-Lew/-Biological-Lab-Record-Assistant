import { expect, test } from '@playwright/test'
import { API_BASE, apiCall, createProjectApi, createRecordApi, inviteAndAcceptApi, registerAccount, unique } from './helpers'

test('phase2 revision, restore, artifact and trace authorization remains object scoped', async ({ request }) => {
  const owner = await registerAccount(request, 'Phase2 Auth Owner')
  const member = await registerAccount(request, 'Phase2 Auth Member')
  const outsider = await registerAccount(request, 'Phase2 Auth Outsider')
  const project = await createProjectApi(request, owner, unique('phase2-auth'))
  await inviteAndAcceptApi(request, owner, project.id, member)
  let record = await createRecordApi(request, member, project.id, 'Phase2 Auth Record')
  record = await apiCall(request, 'PUT', `/records/${record.id}`, member.token, { version: record.version, title: record.title, experimentType: record.experimentType, experimentDate: record.experimentDate, purpose: record.purpose, fieldValues: {}, contentJson: {}, contentHtml: '<p>auth</p>' })
  const revision = await apiCall(request, 'POST', `/records/${record.id}/submissions`, member.token, { reviewerId: owner.user.id, expectedRecordVersion: record.version }, { 'Idempotency-Key': unique('phase2-auth-submit') })
  await apiCall(request, 'POST', `/records/${record.id}/reviews/${revision.review.id}/request-changes`, owner.token, { comment: '退回以验证恢复权限' })
  record = await apiCall(request, 'GET', `/records/${record.id}`, member.token)

  const outsiderRevision = await request.get(`${API_BASE}/records/${record.id}/revisions/${revision.id}`, { headers: { Authorization: `Bearer ${outsider.token}` } })
  expect([403, 404]).toContain(outsiderRevision.status())
  const ownerRestore = await request.post(`${API_BASE}/records/${record.id}/restore-preview`, { headers: { Authorization: `Bearer ${owner.token}` }, data: { sourceRevisionId: revision.id, expectedRecordVersion: record.version, restoreAttachments: true } })
  expect([403, 404]).toContain(ownerRestore.status())
  const memberProjectRun = await request.post(`${API_BASE}/projects/${project.id}/agent-runs`, { headers: { Authorization: `Bearer ${member.token}`, 'Idempotency-Key': unique('member-agent') }, data: { artifactKind: 'PROJECT_PROGRESS' } })
  expect(memberProjectRun.status()).toBe(403)

  const run = await apiCall(request, 'POST', `/projects/${project.id}/agent-runs`, owner.token, { artifactKind: 'PROJECT_PROGRESS' }, { 'Idempotency-Key': unique('owner-agent') })
  let current = run
  for (let index = 0; index < 30 && ['QUEUED', 'RUNNING'].includes(current.status); index += 1) {
    await new Promise((resolve) => setTimeout(resolve, 200))
    current = await apiCall(request, 'GET', `/agent-runs/${run.id}`, owner.token)
  }
  expect(current.status).toBe('SUCCEEDED')
  const outsiderRun = await request.get(`${API_BASE}/agent-runs/${run.id}/steps`, { headers: { Authorization: `Bearer ${outsider.token}` } })
  expect([403, 404]).toContain(outsiderRun.status())
  const outsiderArtifact = await request.get(`${API_BASE}/agent-artifacts/${current.artifactId}`, { headers: { Authorization: `Bearer ${outsider.token}` } })
  expect([403, 404]).toContain(outsiderArtifact.status())
})
