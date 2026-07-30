import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import RecordEditorMvpPage from './RecordEditorMvpPage'
import { updateRecord } from '@/api'

vi.mock('@/api', () => ({
  fetchRecord: vi
    .fn()
    .mockResolvedValue({
      id: 'r1',
      projectId: 'p1',
      projectName: '项目',
      code: 'EXP-1',
      title: '未命名记录',
      experimentType: '未分类',
      experimentDate: '2026-07-23',
      purpose: '',
      provisional: true,
      fieldValues: {},
      contentJson: {},
      contentHtml: '',
      templateSnapshot: { name: '空白记录', fields: [] },
      version: 0,
      capabilities: { canEdit: true, canSubmit: false },
    }),
  fetchAttachments: vi.fn().mockResolvedValue([]),
  fetchRevisionSummaries: vi
    .fn()
    .mockResolvedValue({ items: [], meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 } }),
  discardRecordReservation: vi.fn(),
  updateRecord: vi
    .fn()
    .mockResolvedValue({
      id: 'r1',
      projectId: 'p1',
      projectName: '项目',
      code: 'EXP-1',
      title: 'PCR',
      experimentType: 'PCR',
      experimentDate: '2026-07-23',
      purpose: '目的',
      provisional: false,
      fieldValues: {},
      contentJson: {},
      contentHtml: '',
      templateSnapshot: { name: '空白记录', fields: [] },
      version: 1,
      currentRevisionNo: 0,
      capabilities: { canEdit: true, canSubmit: true },
    }),
}))
function Location() {
  return <span data-testid="location">{useLocation().pathname}</span>
}
describe('RecordEditorMvpPage', () => {
  it('formalizes the reserved record on first save and removes the new marker', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter initialEntries={['/records/r1/edit?new=1']}>
        <Routes>
          <Route
            path="/records/:recordId/edit"
            element={
              <>
                <RecordEditorMvpPage />
                <Location />
              </>
            }
          />
        </Routes>
      </MemoryRouter>,
    )
    await user.type(await screen.findByLabelText('实验名称'), 'PCR')
    await user.type(screen.getByLabelText('实验类型'), 'PCR')
    await user.type(screen.getByLabelText('实验目的'), '目的')
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(updateRecord).toHaveBeenCalledWith(
      'r1',
      expect.objectContaining({ title: 'PCR', version: 0 }),
    )
    expect(await screen.findByTestId('location')).toHaveTextContent('/records/r1/edit')
  })
})
