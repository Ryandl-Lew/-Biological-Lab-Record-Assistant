import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import ProjectDetailMvpPage from './ProjectDetailMvpPage'
import { fetchProjectAuditEvents } from '@/api'

vi.mock('@/api', () => ({
  fetchProject: vi.fn().mockResolvedValue({ id: 'p1', name: '项目', description: '', status: 'ACTIVE', currentUserRole: 'OWNER', memberCount: 1, recordCount: 0, createdAt: '2026-01-01', capabilities: { canCreateRecord: true } }),
  fetchProjectMembers: vi.fn().mockResolvedValue([{ userId: 'u1', displayName: '负责人', email: 'owner@example.com', role: 'OWNER', joinedAt: '2026-01-01' }]),
  fetchRecords: vi.fn().mockResolvedValue({ items: [] }),
  fetchProjectAuditEvents: vi.fn().mockResolvedValue({ items: [{ id: 'e1', eventType: 'RECORD_CREATED', actorName: '负责人', metadata: { title: 'PCR 记录' }, createdAt: '2026-07-23T00:00:00Z', recordId: 'r1' }], meta: { page: 0, totalPages: 1 } }),
  fetchProjectAttachments: vi.fn().mockResolvedValue({ items: [], meta: { totalPages: 0 } }),
  archiveProject: vi.fn(),
  inviteProjectMember: vi.fn(),
  removeProjectMember: vi.fn(),
  updateProjectMemberRole: vi.fn(),
}))

describe('ProjectDetailMvpPage', () => {
  it('loads the audit timeline from the URL tab and applies owner filters', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={['/projects/p1?tab=timeline']}><Routes><Route path="/projects/:projectId" element={<ProjectDetailMvpPage />} /></Routes></MemoryRouter>)
    expect(await screen.findByRole('button', { name: '查看记录' })).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('事件类型'), 'RECORD_SUBMITTED')
    await waitFor(() => expect(fetchProjectAuditEvents).toHaveBeenLastCalledWith('p1', expect.objectContaining({ eventType: 'RECORD_SUBMITTED' })))
  })
})
