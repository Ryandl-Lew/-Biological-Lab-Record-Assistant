import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchRevisionDetail, fetchRevisionDiff, fetchRevisionSummaries } from './revisions'

describe('revision API', () => {
  beforeEach(() => {
    localStorage.clear()
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers(),
      json: vi
        .fn()
        .mockResolvedValue({
          data: [],
          meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
        }),
    })
  })

  it('builds paged summary query through the shared client', async () => {
    await fetchRevisionSummaries('record-1', { page: 1, size: 20 })
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/records/record-1/revisions?page=1&size=20',
      expect.any(Object),
    )
  })

  it('uses nested detail path and encodes diff sources', async () => {
    await fetchRevisionDetail('record-1', 'revision-1')
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/v1/records/record-1/revisions/revision-1',
      expect.any(Object),
    )
    await fetchRevisionDiff('record-1', {
      fromRevisionId: 'revision-1',
      to: 'WORKING_COPY',
      includeUnchanged: true,
    })
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/v1/records/record-1/revision-diff?fromRevisionId=revision-1&to=WORKING_COPY&includeUnchanged=true',
      expect.any(Object),
    )
  })
})
