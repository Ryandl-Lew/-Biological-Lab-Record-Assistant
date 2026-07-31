import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import DashboardMvpPage from './DashboardMvpPage'

vi.mock('@/store/authStore', () => ({
  useAuthStore: (selector) => selector({ currentUser: { displayName: '成员 B' } }),
}))

vi.mock('@/lib/recentTracker', () => ({
  getRecent: vi.fn(() => []),
  clearRecent: vi.fn(),
  trackVisitByPath: vi.fn(),
}))

vi.mock('@/api', () => ({
  fetchDashboardTasks: vi.fn().mockResolvedValue([
    {
      id: 't1',
      type: 'CHANGES_REQUESTED',
      targetId: 'r1',
      title: 'PCR 记录',
      projectName: '演示项目',
      time: new Date().toISOString(),
      action: '继续修改',
      stale: false,
    },
    {
      id: 't2',
      type: 'PROJECT_INVITATION',
      targetId: 'p2',
      title: '加入项目「细胞培养」',
      projectName: '细胞培养',
      time: new Date().toISOString(),
      stale: false,
    },
  ]),
  fetchDashboardSummary: vi.fn().mockResolvedValue({
    projectCount: 1,
    editableRecordCount: 1,
    changesRequestedCount: 1,
    pendingReviewCount: 0,
    pendingInvitationCount: 1,
    unreadNotificationCount: 0,
  }),
  acceptInvitation: vi.fn().mockResolvedValue({}),
  rejectInvitation: vi.fn().mockResolvedValue({}),
}))

/** 探测：把当前路径渲染出来，验证跳转 */
function LocationProbe() {
  const loc = useLocation()
  return (
    <span data-testid="location">
      {loc.pathname}
      {loc.search}
    </span>
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('DashboardMvpPage（欢迎页）', () => {
  it('渲染欢迎区与「开始」动作列表', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<DashboardMvpPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('工作台')).toBeInTheDocument()
    expect(screen.getByText(/欢迎回来/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /新建实验记录/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /新建项目/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /全局搜索/ })).toBeInTheDocument()
  })

  it('最近访问为空时给出友好提示', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<DashboardMvpPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText(/暂无最近访问/)).toBeInTheDocument()
  })

  it('QuickStart 点击「新建项目」跳转到 /projects?new=1', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<DashboardMvpPage />} />
          <Route path="/projects" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>,
    )
    await user.click(await screen.findByRole('button', { name: /新建项目/ }))
    expect(screen.getByTestId('location').textContent).toBe('/projects?new=1')
  })

  it('右侧渲染待处理事项及合计数', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<DashboardMvpPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText('PCR 记录')).toBeInTheDocument()
    expect(screen.getByText('加入项目「细胞培养」')).toBeInTheDocument()
    expect(screen.getByText(/2 项待处理/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /接受/ })).toBeInTheDocument()
  })

  it('点击需修改任务的「继续修改」跳到编辑器', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<DashboardMvpPage />} />
          <Route path="/records/:id/edit" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>,
    )
    await user.click(await screen.findByRole('button', { name: /继续修改/ }))
    expect(screen.getByTestId('location').textContent).toBe('/records/r1/edit')
  })
})
