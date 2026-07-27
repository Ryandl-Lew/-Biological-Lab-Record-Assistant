import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TopbarMvp from './TopbarMvp'
import {
  acceptInvitation,
  fetchNotifications,
  fetchUnreadCount,
  markNotificationRead,
} from '@/api'

vi.mock('@/api', () => ({
  acceptInvitation: vi.fn().mockResolvedValue({}),
  rejectInvitation: vi.fn().mockResolvedValue({}),
  fetchNotifications: vi.fn(),
  fetchUnreadCount: vi.fn(),
  markNotificationRead: vi.fn().mockResolvedValue(null),
  markAllNotificationsRead: vi.fn().mockResolvedValue(null),
}))

const invitation = {
  id: 'n1',
  type: 'PROJECT_INVITATION',
  title: '项目邀请',
  body: '邀请你加入项目',
  target: { type: 'INVITATION', id: 'i1' },
  actions: ['ACCEPT', 'REJECT'],
  stale: false,
  createdAt: '2026-07-23T00:00:00Z',
  readAt: null,
}

afterEach(() => vi.restoreAllMocks())

describe('TopbarMvp', () => {
  it('polls unread count, refreshes on focus and cleans up the interval', async () => {
    fetchUnreadCount.mockResolvedValue({ count: 2 })
    fetchNotifications.mockResolvedValue({ items: [], meta: { totalPages: 0 } })
    const intervalSpy = vi.spyOn(window, 'setInterval').mockReturnValue(77)
    const clearSpy = vi.spyOn(window, 'clearInterval').mockImplementation(() => {})
    const { unmount } = render(<MemoryRouter><TopbarMvp /></MemoryRouter>)
    expect(await screen.findByRole('button', { name: '通知，2 条未读' })).toBeInTheDocument()
    expect(intervalSpy).toHaveBeenCalledWith(expect.any(Function), 20_000)
    fireEvent.focus(window)
    await waitFor(() => expect(fetchUnreadCount).toHaveBeenCalledTimes(2))
    unmount()
    expect(clearSpy).toHaveBeenCalledWith(77)
  })

  it('uses controlled invitation actions and marks the notification read', async () => {
    const user = userEvent.setup()
    fetchUnreadCount.mockResolvedValue({ count: 1 })
    fetchNotifications.mockResolvedValueOnce({ items: [invitation], meta: { totalPages: 1 } }).mockResolvedValue({ items: [{ ...invitation, stale: true, actions: [] }], meta: { totalPages: 1 } })
    render(<MemoryRouter><TopbarMvp /></MemoryRouter>)
    await user.click(await screen.findByRole('button', { name: '通知，1 条未读' }))
    await user.click(await screen.findByRole('button', { name: '接受' }))
    await waitFor(() => expect(acceptInvitation).toHaveBeenCalledWith('i1'))
    expect(markNotificationRead).toHaveBeenCalledWith('n1')
  })
})
