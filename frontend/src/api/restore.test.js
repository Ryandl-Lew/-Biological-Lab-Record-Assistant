import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./client', () => ({ request: vi.fn() }))

import { request } from './client'
import { executeRestore, fetchRestoreOperations, previewRestore } from './restore'

describe('restore api', () => {
  beforeEach(() => request.mockReset())

  it('sends preview and execute through the shared client', async () => {
    const input = { sourceRevisionId: 'rev-1', expectedRecordVersion: 7, restoreAttachments: true }
    const idempotencyKey = '7d9f508b-85cc-4f45-9bdf-52ac7d06bdf1'
    await previewRestore('record-1', input)
    expect(request).toHaveBeenCalledWith('/records/record-1/restore-preview', {
      method: 'POST',
      body: JSON.stringify(input),
    })
    await executeRestore('record-1', { ...input, previewToken: 'signed' }, idempotencyKey)
    expect(request).toHaveBeenLastCalledWith('/records/record-1/restore', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify({ ...input, previewToken: 'signed' }),
    })
  })

  it('builds restore history pagination without leaking keys', async () => {
    await fetchRestoreOperations('record-1', { page: 2, size: 10 })
    expect(request).toHaveBeenCalledWith('/records/record-1/restore-operations?page=2&size=10')
  })
})
