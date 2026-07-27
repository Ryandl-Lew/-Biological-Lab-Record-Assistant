import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import DashboardMvpPage from './DashboardMvpPage'

vi.mock('@/api', () => ({
  fetchDashboardTasks: vi.fn().mockResolvedValue([{ id: 't1', type: 'CHANGES_REQUESTED', targetId: 'r1', title: 'PCR 记录', projectName: '演示项目', time: '2026-07-23T00:00:00Z', action: '继续修改', stale: false }]),
  fetchDashboardSummary: vi.fn().mockResolvedValue({ projectCount: 1, editableRecordCount: 1, pendingReviewCount: 0, unreadNotificationCount: 0 }),
  acceptInvitation: vi.fn(),
  rejectInvitation: vi.fn(),
}))

vi.mock('@/store/authStore', () => ({ useAuthStore: (selector) => selector({ currentUser: { displayName: '成员 B' } }) }))

function Location() { return <span data-testid="location">{useLocation().pathname}</span> }

describe('DashboardMvpPage', () => {
  it('loads real tasks and navigates a changes-requested task to the editor', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><Routes><Route path="*" element={<><DashboardMvpPage /><Location /></>} /></Routes></MemoryRouter>)
    expect(await screen.findByText('PCR 记录')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /继续修改/ }))
    expect(screen.getByTestId('location')).toHaveTextContent('/records/r1/edit')
  })
})
