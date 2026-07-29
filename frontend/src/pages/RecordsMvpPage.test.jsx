import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RecordsMvpPage from './RecordsMvpPage'
import { fetchProjects, fetchRecords, fetchReviewerCandidates, submitRecord } from '@/api'

vi.mock('@/api', () => ({
  fetchProjects: vi.fn(),
  fetchRecords: vi.fn(),
  fetchReviewerCandidates: vi.fn(),
  submitRecord: vi.fn(),
}))

const record = (overrides = {}) => ({
  id: 'r1', projectId: 'p1', projectName: '示例项目', code: 'EXP-1', title: '可提交记录',
  creatorName: '创建者', status: 'IN_PROGRESS', experimentType: 'PCR', experimentDate: '2026-07-23',
  createdAt: '2026-07-23T00:00:00Z', updatedAt: '2026-07-23T01:00:00Z', purpose: '验证提交入口',
  contentHtml: '<p>实验正文</p>', currentRevisionNo: 0, version: 2,
  capabilities: { canEdit: true, canSubmit: true }, ...overrides,
})

describe('RecordsMvpPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fetchProjects.mockResolvedValue({ items: [{ id: 'p1', name: '示例项目' }] })
    fetchRecords.mockResolvedValue({ items: [record(), record({ id: 'r2', code: 'EXP-2', title: '审核中记录', status: 'IN_REVIEW', capabilities: { canEdit: false, canSubmit: false } })] })
    fetchReviewerCandidates.mockResolvedValue([{ userId: 'u2', displayName: '审核人', role: 'REVIEWER' }])
    submitRecord.mockResolvedValue({ id: 'rv1' })
  })

  it('shows a prominent submission action only for records that can be submitted', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><RecordsMvpPage/></MemoryRouter>)
    await user.click(await screen.findByRole('button', { name: '提交审核' }))
    expect(screen.getByRole('dialog', { name: '提交审核' })).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: '确认提交' }))
    await waitFor(() => expect(submitRecord).toHaveBeenCalledWith('r1', expect.objectContaining({ reviewerId: 'u2', expectedRecordVersion: 2 })))
    await user.click(screen.getByRole('button', { name: /审核中记录/ }))
    expect(screen.queryByRole('button', { name: '提交审核' })).not.toBeInTheDocument()
  })
})
