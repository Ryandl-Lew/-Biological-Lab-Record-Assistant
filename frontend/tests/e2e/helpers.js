import { expect } from '@playwright/test'

export const API_BASE = process.env.E2E_API_BASE || 'http://127.0.0.1:8080/api/v1'
export const PASSWORD = 'E2eDemo123!'

export function unique(prefix) {
  const safePrefix = String(prefix).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'user'
  return `${safePrefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export async function apiCall(request, method, path, token, data, headers = {}) {
  const response = await request.fetch(`${API_BASE}${path}`, {
    method,
    data,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
  })
  const body = response.status() === 204 ? null : await response.json().catch(() => null)
  expect(response.ok(), `${method} ${path}: ${JSON.stringify(body)}`).toBeTruthy()
  return body?.data ?? body
}

export async function registerAccount(request, label) {
  const slug = unique(label.toLowerCase())
  const account = { displayName: label, email: `${slug}@example.com`, password: PASSWORD }
  const result = await apiCall(request, 'POST', '/auth/register', null, account)
  return { ...account, token: result.accessToken, user: result.user }
}

export async function loginUi(page, account) {
  await page.goto('/login')
  await page.getByLabel('邮箱').fill(account.email)
  await page.locator('#password').fill(account.password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
}

export async function createProjectApi(request, owner, name = unique('project')) {
  return apiCall(request, 'POST', '/projects', owner.token, { name, description: 'Playwright 独立测试项目' })
}

export async function inviteAndAcceptApi(request, owner, projectId, invitee) {
  const invitation = await apiCall(request, 'POST', `/projects/${projectId}/invitations`, owner.token, { email: invitee.email })
  await apiCall(request, 'POST', `/invitations/${invitation.id}/accept`, invitee.token)
}

export async function createRecordApi(request, creator, projectId, title = unique('record')) {
  return apiCall(request, 'POST', '/records', creator.token, {
    projectId,
    title,
    experimentType: 'PCR',
    experimentDate: '2026-07-23',
    purpose: 'Playwright 权限与并发验证',
  })
}
