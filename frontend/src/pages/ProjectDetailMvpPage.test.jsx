import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useSearchParams } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import ProjectDetailMvpPage from './ProjectDetailMvpPage'
import { fetchProjectAuditEvents } from '@/api'

vi.mock('@/api', () => ({
  fetchProject: vi.fn().mockResolvedValue({ id: 'p1', name: 'Project', description: '', status: 'ACTIVE', currentUserRole: 'OWNER', memberCount: 1, recordCount: 0, createdAt: '2026-01-01', capabilities: { canCreateRecord: true } }),
  fetchProjectMembers: vi.fn().mockResolvedValue([{ userId: 'u1', displayName: 'Owner', email: 'owner@example.com', role: 'OWNER', joinedAt: '2026-01-01' }]),
  fetchRecords: vi.fn().mockResolvedValue({ items: [] }),
  fetchProjectAuditEvents: vi.fn().mockResolvedValue({ items: [
    { id: 'e1', eventType: 'PROJECT_CREATED', actorId: 'u1', actorName: 'Owner', metadata: {}, createdAt: '2026-07-23T00:00:00Z' },
    { id: 'e2', eventType: 'RECORD_CREATED', actorId: 'u1', actorName: 'Owner', metadata: { title: 'TestRecord' }, createdAt: '2026-07-23T01:00:00Z', recordId: 'r1' },
  ], meta: { page: 0, totalPages: 1 } }),
  fetchProjectAttachments: vi.fn().mockResolvedValue({ items: [], meta: { totalPages: 0 } }),
  archiveProject: vi.fn(),
  inviteProjectMember: vi.fn(),
  removeProjectMember: vi.fn(),
  updateProjectMemberRole: vi.fn(),
}))

describe('ProjectDetailMvpPage', () => {
  it('fetches all audit events and renders the timeline graph', async () => {
    render(<MemoryRouter initialEntries={['/projects/p1?tab=timeline']}><Routes><Route path="/projects/:projectId" element={<ProjectDetailMvpPage />} /></Routes></MemoryRouter>)
    await screen.findByText('TestRecord')
    await waitFor(() => expect(fetchProjectAuditEvents).toHaveBeenCalledWith('p1', { page: 0, size: 100 }))
  })

  it('client-side filters preserve structural nodes and do not re-fetch', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/projects/p1?tab=timeline']}><Routes><Route path="/projects/:projectId" element={<ProjectDetailMvpPage />} /></Routes></MemoryRouter>)
    await screen.findByText('TestRecord')
    await user.selectOptions(screen.getByLabelText('事件类型'), 'RECORD_CREATED')
    expect(screen.getByText('TestRecord')).toBeInTheDocument()
    expect(fetchProjectAuditEvents).toHaveBeenCalledTimes(1)
  })

  it('records tab links create flow with current projectId', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/projects/p1?tab=records']}>
        <Routes>
          <Route path="/projects/:projectId" element={<ProjectDetailMvpPage />} />
          <Route path="/records/new" element={<CreateProbe />} />
        </Routes>
      </MemoryRouter>,
    )
    await user.click(await screen.findByRole('button', { name: '新建实验记录' }))
    expect(screen.getByText('create-record:p1')).toBeInTheDocument()
  })
})

function CreateProbe() {
  const [params] = useSearchParams()
  return <p>create-record:{params.get('projectId')}</p>
}
