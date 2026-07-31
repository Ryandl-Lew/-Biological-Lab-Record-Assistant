import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./client', () => ({ request: vi.fn() }))
vi.mock('@/lib/uuid', () => ({
  createUuid: vi.fn(() => '7d9f508b-85cc-4f45-9bdf-52ac7d06bdf1'),
}))

import { request } from './client'
import { createUuid } from '@/lib/uuid'
import { submitRecord } from './reviews'

describe('reviews api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('generates a compatible idempotency key when submitting a record', async () => {
    const input = {
      reviewerId: 'reviewer-1',
      submitNote: 'ready',
      expectedRecordVersion: 3,
    }

    await submitRecord('record-1', input)

    expect(createUuid).toHaveBeenCalledOnce()
    expect(request).toHaveBeenCalledWith('/records/record-1/submissions', {
      method: 'POST',
      headers: { 'Idempotency-Key': '7d9f508b-85cc-4f45-9bdf-52ac7d06bdf1' },
      body: JSON.stringify(input),
    })
  })

  it('preserves a caller-provided idempotency key', async () => {
    const input = { reviewerId: 'reviewer-1', expectedRecordVersion: 3 }

    await submitRecord('record-1', input, 'retry-key')

    expect(createUuid).not.toHaveBeenCalled()
    expect(request).toHaveBeenCalledWith('/records/record-1/submissions', {
      method: 'POST',
      headers: { 'Idempotency-Key': 'retry-key' },
      body: JSON.stringify(input),
    })
  })
})
