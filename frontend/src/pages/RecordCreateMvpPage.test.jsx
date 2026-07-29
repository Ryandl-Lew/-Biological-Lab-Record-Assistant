import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import RecordCreateMvpPage from './RecordCreateMvpPage'
import { fetchProjects, fetchTemplates } from '@/api'

vi.mock('@/api', () => ({
  fetchProjects: vi.fn().mockResolvedValue({
    items: [
      { id: 'p1', name: '当前项目', description: '', status: 'ACTIVE', memberCount: 2, recordCount: 1, capabilities: { canCreateRecord: true } },
      { id: 'p2', name: '其他项目', description: '', status: 'ACTIVE', memberCount: 1, recordCount: 0, capabilities: { canCreateRecord: true } },
    ],
  }),
  fetchTemplates: vi.fn().mockResolvedValue({ items: [] }),
  reserveRecord: vi.fn(),
}))

describe('RecordCreateMvpPage', () => {
  it('preselects project from projectId query', async () => {
    render(
      <MemoryRouter initialEntries={['/records/new?projectId=p1']}>
        <Routes>
          <Route path="/records/new" element={<RecordCreateMvpPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(fetchProjects).toHaveBeenCalled()
    expect(fetchTemplates).toHaveBeenCalled()
    await screen.findByRole('button', { name: /当前项目/ })
    expect(screen.getByText('已选择：').parentElement).toHaveTextContent('当前项目')
    expect(screen.queryByText('尚未选择项目')).not.toBeInTheDocument()
  })
})
