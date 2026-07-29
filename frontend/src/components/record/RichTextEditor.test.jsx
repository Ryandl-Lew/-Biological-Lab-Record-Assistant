import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import RichTextEditor from './RichTextEditor'

describe('RichTextEditor', () => {
  it('provides structured formatting controls and reports editor changes', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<RichTextEditor html="<p>初始正文</p>" onChange={onChange}/>)
    expect(screen.getByRole('button', { name: '二级标题' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '有序列表' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '引用' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '添加或编辑链接' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '二级标题' }))
    expect(onChange).toHaveBeenCalled()
    expect(screen.getByText(/4 字符/)).toBeInTheDocument()
  })
})
