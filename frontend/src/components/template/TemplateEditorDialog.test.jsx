import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import TemplateEditorDialog from './TemplateEditorDialog'

describe('TemplateEditorDialog', () => {
  it('validates select options and supports ordered fields', async () => {
    const user = userEvent.setup(), save = vi.fn()
    render(<TemplateEditorDialog template={null} onClose={vi.fn()} onSave={save} />)
    await user.type(screen.getByText('模板名称').nextElementSibling, '模板')
    await user.type(screen.getByLabelText('字段 1 名称'), '结果')
    await user.selectOptions(screen.getByLabelText('字段 1 类型'), 'SELECT')
    await user.click(screen.getByRole('button', { name: '保存模板' }))
    expect(screen.getByText('下拉字段至少需要一个选项')).toBeInTheDocument()
    await user.type(screen.getByLabelText('字段 1 选项'), '成功，失败')
    await user.click(screen.getByRole('button', { name: '新增字段' }))
    expect(screen.getByLabelText('字段 2 名称')).toBeInTheDocument()
  })
})
