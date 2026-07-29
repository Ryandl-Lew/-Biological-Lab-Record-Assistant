import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from './LoginPage'
import { useAuthStore } from '@/store/authStore'

describe('LoginPage', () => {
  beforeEach(() => useAuthStore.setState({ currentUser: null, loading: false, login: vi.fn().mockResolvedValue({}) }))
  it('submits email and password only', async () => {
    const user = userEvent.setup(); render(<MemoryRouter><LoginPage/></MemoryRouter>)
    await user.type(screen.getByLabelText('邮箱'), 'user@example.com')
    await user.type(screen.getByLabelText('密码'), 'Password123!')
    await user.click(screen.getByRole('button', { name: '登录' }))
    expect(useAuthStore.getState().login).toHaveBeenCalledWith({ email: 'user@example.com', password: 'Password123!' })
    expect(screen.queryByText('本地测试账号')).not.toBeInTheDocument()
  })
})
