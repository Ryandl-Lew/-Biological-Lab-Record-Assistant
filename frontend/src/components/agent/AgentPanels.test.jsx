import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createProjectAgentRun, fetchProjectArtifacts, fetchRecordArtifacts } from '@/api'
import ProgressReportPanel from './ProgressReportPanel'
import RecordSummaryPanel from './RecordSummaryPanel'

vi.mock('@/api', () => ({
  createProjectAgentRun: vi.fn(),
  createRecordAgentRun: vi.fn(),
  fetchProjectArtifacts: vi.fn(),
  fetchRecordArtifacts: vi.fn(),
  fetchAgentArtifact: vi.fn(),
  fetchAgentRun: vi.fn(),
  fetchAgentSteps: vi.fn(),
  cancelAgentRun: vi.fn(),
  rerunAgent: vi.fn(),
}))

describe('Agent report panels', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fetchRecordArtifacts.mockResolvedValue({ items: [], meta: { totalElements: 0 } })
    fetchProjectArtifacts.mockResolvedValue({ items: [], meta: { totalElements: 0 } })
  })

  it('renders the record Q&A chat panel', async () => {
    render(
      <RecordSummaryPanel record={{ id: 'record', creatorId: 'creator', currentRevisionNo: 0 }} />,
    )
    expect(screen.getByText('记录问答')).toBeInTheDocument()
    expect(
      screen.getByPlaceholderText('输入问题，Enter 发送，Shift+Enter 换行'),
    ).toBeInTheDocument()
  })

  it('lets only the project owner generate progress reports', async () => {
    createProjectAgentRun.mockResolvedValue({ id: 'run-project', status: 'QUEUED' })
    const onRunId = vi.fn()
    const { rerender } = render(
      <ProgressReportPanel
        project={{ id: 'project', currentUserRole: 'OWNER' }}
        onRunId={onRunId}
      />,
    )
    await userEvent.click(await screen.findByRole('button', { name: '生成进展报告' }))
    expect(createProjectAgentRun).toHaveBeenCalledWith(
      'project',
      expect.objectContaining({ artifactKind: 'PROJECT_PROGRESS' }),
      expect.any(String),
    )
    expect(onRunId).toHaveBeenCalledWith('run-project')
    rerender(
      <ProgressReportPanel
        project={{ id: 'project', currentUserRole: 'MEMBER' }}
        onRunId={vi.fn()}
      />,
    )
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: '生成进展报告' })).not.toBeInTheDocument(),
    )
  })
})
