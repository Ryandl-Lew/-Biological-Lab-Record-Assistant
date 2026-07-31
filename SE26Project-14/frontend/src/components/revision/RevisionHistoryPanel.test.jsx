import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RevisionHistoryPanel from './RevisionHistoryPanel'
import { fetchRevisionDetail, fetchRevisionDiff } from '@/api'

vi.mock('@/api', () => ({
  fetchRevisionDetail: vi.fn(),
  fetchRevisionDiff: vi.fn(),
  previewRestore: vi.fn(),
  executeRestore: vi.fn(),
}))

const revisions = [
  {
    id: 'r2',
    revisionNo: 2,
    label: 'R2',
    submitterName: '创建者',
    submittedAt: '2026-07-27T00:00:00Z',
    submitNote: '第二版',
    attachmentCount: 1,
    current: true,
    finalRevision: false,
    review: { status: 'CHANGES_REQUESTED' },
  },
  {
    id: 'r1',
    revisionNo: 1,
    label: 'R1',
    submitterName: '创建者',
    submittedAt: '2026-07-26T00:00:00Z',
    submitNote: '第一版',
    attachmentCount: 0,
    current: false,
    finalRevision: true,
    review: { status: 'APPROVED' },
  },
]

describe('RevisionHistoryPanel', () => {
  beforeEach(() => vi.clearAllMocks())
  it('shows badges, disables identical sources and requests the selected diff', async () => {
    const user = userEvent.setup()
    fetchRevisionDiff.mockResolvedValue({
      generatedAt: '2026-07-27T00:00:00Z',
      summary: {},
      sections: [],
      warnings: [],
    })
    render(
      <RevisionHistoryPanel
        record={{
          id: 'record',
          status: 'CHANGES_REQUESTED',
          version: 4,
          capabilities: { canRestore: false },
        }}
        revisions={revisions}
        meta={{ page: 0, totalPages: 1, totalElements: 2 }}
      />,
    )
    expect(screen.getByText('当前审核版本')).toBeInTheDocument()
    expect(screen.getByText('最终批准版本')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('基准版本'), 'r1')
    await user.selectOptions(screen.getByLabelText('目标版本'), 'r1')
    expect(screen.getByRole('button', { name: '查看结构化差异' })).toBeDisabled()
    await user.selectOptions(screen.getByLabelText('目标版本'), 'r2')
    await user.click(screen.getByRole('button', { name: '查看结构化差异' }))
    await waitFor(() =>
      expect(fetchRevisionDiff).toHaveBeenCalledWith('record', {
        fromRevisionId: 'r1',
        toRevisionId: 'r2',
        includeUnchanged: true,
      }),
    )
  })

  it('lazy loads revision detail', async () => {
    fetchRevisionDetail.mockResolvedValue({
      label: 'R2',
      submitterName: '创建者',
      submittedAt: '2026-07-27T00:00:00Z',
      current: true,
      finalRevision: false,
      snapshot: { title: '标题', fieldValues: {}, contentPlainText: '正文' },
      attachments: [],
    })
    render(
      <RevisionHistoryPanel
        record={{ id: 'record', status: 'IN_PROGRESS', capabilities: { canRestore: false } }}
        revisions={revisions}
        meta={{ page: 0, totalPages: 1 }}
      />,
    )
    await userEvent.click(screen.getAllByRole('button', { name: '查看详情' })[0])
    expect(await screen.findByRole('dialog', { name: '版本详情' })).toBeInTheDocument()
    expect(fetchRevisionDetail).toHaveBeenCalledWith('record', 'r2')
  })
})
