import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { executeRestore, previewRestore } from '@/api'
import RestorePreviewDialog from './RestorePreviewDialog'

vi.mock('@/api', () => ({ previewRestore: vi.fn(), executeRestore: vi.fn() }))
const preview = {
  sourceRevision: { revisionNo: 1 },
  expectedRecordVersion: 3,
  previewToken: 'token',
  expiresAt: '2026-07-27T01:00:00Z',
  diff: {
    summary: { added: 1, removed: 0, modified: 1, attachmentAdded: 1, attachmentRemoved: 0 },
  },
  attachmentPlan: { activate: ['a'], softDelete: [], keep: [], missingPhysicalFiles: [] },
  warnings: ['将覆盖当前工作副本'],
  capabilities: { canExecute: true },
}

describe('RestorePreviewDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    previewRestore.mockResolvedValue(preview)
    executeRestore.mockResolvedValue({ record: { id: 'record' } })
  })
  it('regenerates preview when attachment mode changes and executes with token/version/idempotency key', async () => {
    const user = userEvent.setup(),
      success = vi.fn()
    render(
      <RestorePreviewDialog
        open
        record={{ id: 'record', version: 3 }}
        revision={{ id: 'r1', revisionNo: 1, label: 'R1' }}
        onClose={vi.fn()}
        onSuccess={success}
      />,
    )
    expect(await screen.findByText('将覆盖当前工作副本')).toBeInTheDocument()
    await user.click(screen.getByRole('checkbox'))
    await waitFor(() =>
      expect(previewRestore).toHaveBeenLastCalledWith('record', {
        sourceRevisionId: 'r1',
        expectedRecordVersion: 3,
        restoreAttachments: false,
      }),
    )
    await user.click(screen.getByRole('button', { name: '确认恢复工作副本' }))
    await waitFor(() =>
      expect(executeRestore).toHaveBeenCalledWith(
        'record',
        {
          sourceRevisionId: 'r1',
          expectedRecordVersion: 3,
          restoreAttachments: false,
          previewToken: 'token',
        },
        expect.any(String),
      ),
    )
    expect(success).toHaveBeenCalled()
  })

  it('keeps the dialog open and offers a fresh preview when stale', async () => {
    previewRestore
      .mockRejectedValueOnce(
        Object.assign(new Error('工作副本已变化'), { code: 'RESTORE_PREVIEW_STALE' }),
      )
      .mockResolvedValue(preview)
    render(
      <RestorePreviewDialog
        open
        record={{ id: 'record', version: 3 }}
        revision={{ id: 'r1', revisionNo: 1 }}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
      />,
    )
    await userEvent.click(await screen.findByRole('button', { name: '重新生成预览' }))
    expect(await screen.findByText('将覆盖当前工作副本')).toBeInTheDocument()
  })
})
