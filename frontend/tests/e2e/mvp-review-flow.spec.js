import path from 'node:path'
import { expect, test } from '@playwright/test'
import { registerAccount, loginUi, unique } from './helpers'

test('three-account R1 return, R2 approval, search, export and archive flow', async ({
  browser,
  request,
}) => {
  const owner = await registerAccount(request, '演示负责人 A')
  const member = await registerAccount(request, '演示成员 B')
  const reviewer = await registerAccount(request, '演示审核者 C')
  const projectName = unique('软件工程实践演示项目')
  const recordTitle = unique('PCR 扩增记录')
  const ownerContext = await browser.newContext()
  const memberContext = await browser.newContext()
  const reviewerContext = await browser.newContext()
  const pageA = await ownerContext.newPage()
  const pageB = await memberContext.newPage()
  const pageC = await reviewerContext.newPage()

  await loginUi(pageA, owner)
  await pageA.goto('/projects')
  await pageA.getByRole('button', { name: '新建项目' }).click()
  await pageA.getByLabel('项目名称').fill(projectName)
  await pageA.getByLabel('项目简介').fill('Playwright 三账号完整演示')
  await pageA
    .getByLabel('项目详细描述')
    .fill('验证项目协作、记录审核、附件、搜索、导出和不可逆归档的完整流程。')
  await pageA.getByRole('button', { name: '创建项目' }).click()
  await pageA
    .getByText(projectName, { exact: true })
    .locator('xpath=ancestor::article')
    .getByRole('button', { name: '进入项目' })
    .click()
  const projectId = pageA.url().match(/projects\/([^?]+)/)[1]
  await pageA.getByRole('button', { name: /^成员/ }).click()
  for (const account of [member, reviewer]) {
    await pageA.getByRole('button', { name: '邀请成员' }).click()
    await pageA.getByLabel('注册邮箱').fill(account.email)
    await pageA.getByRole('button', { name: '发送邀请' }).click()
  }

  await loginUi(pageB, member)
  await pageB.getByRole('button', { name: /通知，1 条未读/ }).click()
  await pageB.getByRole('button', { name: '接受' }).click()
  await loginUi(pageC, reviewer)
  await pageC.getByRole('button', { name: /通知，1 条未读/ }).click()
  await pageC.getByRole('button', { name: '接受' }).click()

  await pageA.reload()
  await pageA.getByRole('button', { name: /^成员/ }).click()
  const reviewerRole = pageA.getByLabel(`修改 ${reviewer.displayName} 角色`)
  await reviewerRole.selectOption('REVIEWER')
  await expect(reviewerRole).toHaveValue('REVIEWER')

  await pageB.goto('/templates')
  await pageB.getByRole('button', { name: '新建模板' }).click()
  const templateName = unique('PCR 个人模板')
  await pageB.getByLabel('模板名称').fill(templateName)
  await pageB.getByLabel('实验类型').fill('PCR')
  await pageB.getByLabel('字段 1 名称').fill('预期条带大小')
  await pageB.getByLabel('字段 1 类型').selectOption('NUMBER')
  await pageB.getByRole('button', { name: '新增字段' }).click()
  await pageB.getByLabel('字段 2 名称').fill('实验结果')
  await pageB.getByLabel('字段 2 类型').selectOption('MULTI_LINE_TEXT')
  await pageB.getByRole('button', { name: '新增字段' }).click()
  await pageB.getByLabel('字段 3 名称').fill('结果文件')
  await pageB.getByLabel('字段 3 类型').selectOption('FILE')
  await pageB.getByRole('button', { name: '保存模板' }).click()
  await expect(pageB.getByText(templateName, { exact: true })).toBeVisible()

  await pageB.goto('/records/new')
  await pageB.getByText(projectName, { exact: true }).locator('xpath=ancestor::button[1]').click()
  await pageB.getByText(templateName, { exact: true }).locator('xpath=ancestor::button[1]').click()
  await pageB.getByRole('button', { name: '进入编辑器' }).click()
  await expect(pageB).toHaveURL(/\/records\/[^/]+\/edit\?new=1$/)
  await pageB
    .getByLabel('选择附件')
    .setInputFiles(path.resolve(process.cwd(), '../output/pdf/bionote-mvp-sample.pdf'))
  await expect(
    pageB.getByRole('paragraph').filter({ hasText: 'bionote-mvp-sample.pdf' }),
  ).toBeVisible()
  await pageB.getByRole('button', { name: '预览' }).click()
  await expect(pageB.getByTitle('PDF 附件预览')).toBeVisible()
  await expect(pageB.getByTitle('PDF 附件预览')).not.toHaveAttribute('sandbox')
  await pageB.getByRole('button', { name: '关闭' }).click()
  await pageB.getByLabel('实验名称').fill(recordTitle)
  await pageB.getByLabel('实验类型').fill('PCR')
  await pageB.getByLabel('实验目的').fill('验证三账号审核闭环')
  await pageB.getByText('预期条带大小', { exact: true }).locator('..').locator('input').fill('850')
  await pageB
    .getByText('实验结果', { exact: true })
    .locator('..')
    .locator('textarea')
    .fill('初次结果，等待审核。')
  await pageB.getByLabel('结果文件').selectOption({ label: 'bionote-mvp-sample.pdf' })
  await pageB.locator('.ProseMirror').fill('PCR 扩增过程与初次实验结果。')
  await pageB.getByRole('button', { name: '保存', exact: true }).click()
  await expect(pageB).toHaveURL(
    (url) =>
      /\/records\/[^/]+\/edit$/.test(url.pathname) && !url.pathname.includes('/records/new/'),
  )
  const recordId = pageB.url().match(/records\/([^/]+)\/edit/)[1]
  await pageB.goto(`/records/${recordId}`)
  await expect(pageB.getByRole('button', { name: '返回上一级' })).toBeVisible()
  await pageB.getByRole('button', { name: '提交审核' }).click()
  await pageB.getByLabel('指定审核人').selectOption({ label: `${reviewer.displayName}（审核者）` })
  await pageB.getByRole('button', { name: '确认提交' }).click()
  await expect(pageB.getByText('R1 快照')).toBeVisible()

  await pageC.goto('/')
  await pageC.getByRole('button', { name: /开始审核/ }).click()
  await pageC.getByLabel('审核意见').fill('请补充对照组说明')
  await pageC.getByRole('button', { name: '退回修改' }).click()
  await expect(pageC.getByText('需修改').first()).toBeVisible()
  await pageB.goto('/')
  await pageB.getByRole('button', { name: /继续修改/ }).click()
  await expect(pageB.getByText(/请补充对照组说明/)).toBeVisible()
  await pageB.getByLabel('实验目的').fill('验证三账号审核闭环，并补充对照组说明')
  await pageB.locator('.ProseMirror').fill('PCR 扩增过程、初次结果与对照组补充说明。')
  await pageB.getByRole('button', { name: '保存', exact: true }).click()
  await pageB.getByRole('button', { name: '提交审核' }).click()
  await pageB.getByLabel('指定审核人').selectOption({ label: `${reviewer.displayName}（审核者）` })
  await pageB.getByRole('button', { name: '确认提交' }).click()
  await expect(pageB.getByText('R2 快照')).toBeVisible()

  await pageC.goto('/')
  await pageC.getByRole('button', { name: /开始审核/ }).click()
  await pageC.getByRole('button', { name: '审核通过' }).click()
  await expect(pageC.getByText('已完成').first()).toBeVisible()
  await pageB.goto(`/search?keyword=${encodeURIComponent(recordTitle)}&entityType=RECORD`)
  await pageB.getByText(recordTitle, { exact: true }).click()
  const markdownDownload = pageB.waitForEvent('download')
  await pageB.getByRole('button', { name: 'Markdown' }).click()
  expect((await markdownDownload).suggestedFilename()).toMatch(/\.md$/)
  const pdfDownload = pageB.waitForEvent('download')
  await pageB.getByRole('button', { name: 'PDF' }).click()
  expect((await pdfDownload).suggestedFilename()).toMatch(/\.pdf$/)

  await pageA.goto(`/projects/${projectId}?tab=timeline`)
  await expect(
    pageA
      .locator('ol')
      .getByText(/通过了实验记录审核/)
      .first(),
  ).toBeVisible()
  pageA.once('dialog', (dialog) => dialog.accept())
  await pageA.getByRole('button', { name: '归档项目' }).click()
  await expect(pageA.getByText(/不可逆归档/)).toBeVisible()
  await Promise.all([ownerContext.close(), memberContext.close(), reviewerContext.close()])
})
