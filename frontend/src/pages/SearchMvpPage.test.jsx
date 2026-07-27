import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import SearchMvpPage from './SearchMvpPage'
import { search } from '@/api'

vi.mock('@/api', () => ({
  search: vi.fn(),
  fetchProjects: vi.fn().mockResolvedValue({ items: [] }),
  fetchProjectMembers: vi.fn().mockResolvedValue([]),
}))

function Location() { const location = useLocation(); return <span data-testid="location">{location.pathname}{location.search}{location.hash}</span> }

describe('SearchMvpPage', () => {
  it('keeps filters in the URL, debounces keyword changes, paginates and follows an exact target', async () => {
    const user = userEvent.setup()
    search.mockResolvedValue({
      items: [{ entityType: 'ATTACHMENT', id: 'a1', title: '结果图.png', snippet: '图片附件', projectName: '项目', target: { type: 'RECORD_ATTACHMENT', id: 'r1', attachmentId: 'a1' } }],
      meta: { page: 0, size: 20, total: 21, counts: { ATTACHMENT: 21 } },
    })
    render(<MemoryRouter initialEntries={['/search?entityType=ATTACHMENT']}><Routes><Route path="*" element={<><SearchMvpPage /><Location /></>} /></Routes></MemoryRouter>)
    expect(await screen.findByText('结果图.png')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '全部' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '项目' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '实验记录' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /全部\s+21/ })).not.toBeInTheDocument()
    expect(search).toHaveBeenCalledWith(expect.objectContaining({ entityType: 'ATTACHMENT' }))
    fireEvent.change(screen.getByRole('searchbox', { name: '全局搜索' }), { target: { value: 'PCR' } })
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('keyword=PCR'), { timeout: 1000 })
    expect(search).toHaveBeenCalledWith(expect.objectContaining({ keyword: 'PCR' }))
    await user.click(screen.getByRole('button', { name: /下一页/ }))
    expect(screen.getByTestId('location')).toHaveTextContent('page=1')
    await user.click(screen.getByRole('button', { name: /结果图.png/ }))
    expect(screen.getByTestId('location')).toHaveTextContent('/records/r1#attachment-a1')
  })
})
