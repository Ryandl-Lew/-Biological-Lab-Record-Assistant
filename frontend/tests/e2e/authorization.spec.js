import { expect, test } from '@playwright/test'
import { apiCall, createProjectApi, createRecordApi, inviteAndAcceptApi, registerAccount } from './helpers'

test('object-level authorization blocks outsiders and other creators', async ({ request }) => {
  const owner = await registerAccount(request, 'Auth Owner')
  const member = await registerAccount(request, 'Auth Member')
  const outsider = await registerAccount(request, 'Auth Outsider')
  const project = await createProjectApi(request, owner)
  await inviteAndAcceptApi(request, owner, project.id, member)
  const record = await createRecordApi(request, member, project.id)

  const outsiderProject = await request.get(`http://127.0.0.1:8080/api/v1/projects/${project.id}`, { headers: { Authorization: `Bearer ${outsider.token}` } })
  expect([403, 404]).toContain(outsiderProject.status())
  const outsiderRecord = await request.get(`http://127.0.0.1:8080/api/v1/records/${record.id}`, { headers: { Authorization: `Bearer ${outsider.token}` } })
  expect([403, 404]).toContain(outsiderRecord.status())

  const ownerEdit = await request.put(`http://127.0.0.1:8080/api/v1/records/${record.id}`, {
    headers: { Authorization: `Bearer ${owner.token}` },
    data: { version: record.version, title: record.title, experimentType: record.experimentType, experimentDate: record.experimentDate, purpose: record.purpose, fieldValues: {}, contentJson: {}, contentHtml: '' },
  })
  expect(ownerEdit.status()).toBe(403)
  await apiCall(request, 'GET', `/projects/${project.id}`, member.token)
})
