import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import AgentHelpDialog from './AgentHelpDialog'

describe('AgentHelpDialog', () => {
  it('renders help sections and closes', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<AgentHelpDialog open onClose={onClose} />)
    expect(screen.getByRole('dialog', { name: 'Agent 使用帮助' })).toBeInTheDocument()
    expect(screen.getByText('曲线拟合')).toBeInTheDocument()
    expect(screen.getByText('数据图')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '知道了' }))
    expect(onClose).toHaveBeenCalled()
  })

  it('renders nothing when closed', () => {
    const { container } = render(<AgentHelpDialog open={false} onClose={vi.fn()} />)
    expect(container).toBeEmptyDOMElement()
  })
})
