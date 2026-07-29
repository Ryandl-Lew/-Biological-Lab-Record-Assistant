import { expect, test } from '@playwright/test'
import { PASSWORD, unique } from './helpers'

test('register, restore session, update profile, logout and email login', async ({ page }) => {
  const slug = unique('auth')
  const email = `${slug}@example.com`
  await page.goto('/register')
  await page.getByLabel('用户名').fill('E2E 新用户')
  await page.getByLabel('邮箱').fill(email)
  await page.getByLabel('密码', { exact: true }).fill(PASSWORD)
  await page.getByLabel('确认密码').fill(PASSWORD)
  await page.getByRole('button', { name: '注册并登录' }).click()
  await expect(page).toHaveURL(/\/$/)
  await page.reload()
  await expect(page.getByRole('heading', { name: '你好，E2E 新用户' })).toBeVisible()

  await page.goto('/profile')
  await page.getByRole('button', { name: '编辑资料' }).click()
  await page.getByLabel('用户名').fill('E2E 已更新用户')
  await page.getByRole('button', { name: '保存资料' }).click()
  await expect(page.getByText('资料已更新')).toBeVisible()
  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login/)
  await page.getByLabel('邮箱').fill(email.toUpperCase())
  await page.locator('#password').fill(PASSWORD)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '你好，E2E 已更新用户' })).toBeVisible()
})
