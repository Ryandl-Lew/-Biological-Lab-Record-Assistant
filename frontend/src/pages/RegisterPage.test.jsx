import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RegisterPage from './RegisterPage'
import { useAuthStore } from '@/store/authStore'

describe('RegisterPage', () => {
  beforeEach(() => useAuthStore.setState({ currentUser: null, loading: false, register: vi.fn() }))
  it('shows confirmation mismatch at the field', async () => {
    const user=userEvent.setup(); render(<MemoryRouter><RegisterPage/></MemoryRouter>)
    await user.type(screen.getByLabelText('用户名'),'测试用户'); await user.type(screen.getByLabelText('邮箱'),'user@example.com')
    await user.type(screen.getByLabelText('密码'),'Password123!'); await user.type(screen.getByLabelText('确认密码'),'Different123!')
    await user.click(screen.getByRole('button',{name:'注册并登录'}))
    expect(screen.getByText('两次输入的密码不一致')).toBeInTheDocument()
  })
})
