import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchRecord, fetchReviewerCandidates } from '@/api'
import RecordDetailMvpPage from './RecordDetailMvpPage'

vi.mock('@/components/agent/AutoSummaryPanel', () => ({
  default: () => <div>记录总结入口</div>,
}))

vi.mock('@/api', () => ({
  fetchRecord: vi.fn(),
  fetchRevisionSummaries: vi
    .fn()
    .mockResolvedValue({ items: [], meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 } }),
  fetchAttachments: vi.fn().mockResolvedValue([]),
  fetchReviewerCandidates: vi.fn(),
  submitRecord: vi.fn(),
  deleteRecord: vi.fn(),
  approveReview: vi.fn(),
  requestReviewChanges: vi.fn(),
  downloadMarkdown: vi.fn(),
  downloadPdf: vi.fn(),
  fetchExportPreview: vi.fn(),
  saveBlob: vi.fn(),
}))

const record = (overrides = {}) => ({
  id: 'r1',
  code: 'EXP-1',
  projectName: '项目',
  title: '记录',
  creatorName: 'B',
  updatedAt: '2026-07-23',
  status: 'IN_PROGRESS',
  experimentType: 'PCR',
  experimentDate: '2026-07-23',
  purpose: '目的',
  templateSnapshot: { name: '空白', fields: [] },
  fieldValues: {},
  contentHtml: '<p>安全正文</p><script>x()</script>',
  currentRevisionNo: 0,
  version: 2,
  capabilities: { canEdit: false, canDelete: false, canSubmit: false },
  ...overrides,
})

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/records/r1']}>
      <Routes>
        <Route path="/records/:recordId" element={<RecordDetailMvpPage />} />
        <Route path="/records" element={<p>记录目录页</p>} />
      </Routes>
    </MemoryRouter>,
  )

describe('RecordDetailMvpPage', () => {
  beforeEach(() => {
    fetchRecord.mockResolvedValue(record())
    fetchReviewerCandidates.mockResolvedValue([])
  })

  it('provides a parent-level return action and hides write actions for read-only participants', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('记录')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '删除' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '提交审核' })).not.toBeInTheDocument()
    expect(screen.queryByText('x()')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '返回上一级' }))
    expect(screen.getByText('记录目录页')).toBeInTheDocument()
  })

  it('shows the submission dialog only when the backend capability allows submission', async () => {
    const user = userEvent.setup()
    fetchRecord.mockResolvedValue(
      record({ capabilities: { canEdit: true, canDelete: true, canSubmit: true } }),
    )
    fetchReviewerCandidates.mockResolvedValue([
      { userId: 'reviewer-1', displayName: '审核人', role: 'REVIEWER' },
    ])
    renderPage()

    await user.click(await screen.findByRole('button', { name: '提交审核' }))

    expect(screen.getByRole('dialog', { name: '提交审核' })).toBeInTheDocument()
    expect(await screen.findByRole('option', { name: /审核人/ })).toBeInTheDocument()
  })

  it('keeps only the record summary entry and removes record Q&A', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '记录总结' }))

    expect(await screen.findByText('记录总结入口')).toBeInTheDocument()
    expect(screen.queryByText('记录问答')).not.toBeInTheDocument()
    expect(screen.queryByPlaceholderText(/输入问题/)).not.toBeInTheDocument()
  })
})
