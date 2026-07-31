import { describe, expect, it } from 'vitest'
import { createUuid } from './uuid'

const UUID_V4_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

describe('createUuid', () => {
  it('uses the platform UUID generator when available', () => {
    const value = '7d9f508b-85cc-4f45-9bdf-52ac7d06bdf1'
    expect(createUuid({ randomUUID: () => value })).toBe(value)
  })

  it('generates a valid UUID v4 when randomUUID is unavailable', () => {
    const cryptoApi = {
      getRandomValues(bytes) {
        bytes.set(Array.from({ length: 16 }, (_, index) => index))
        return bytes
      },
    }

    expect(createUuid(cryptoApi)).toMatch(UUID_V4_PATTERN)
  })

  it('still generates a valid UUID v4 without Web Crypto', () => {
    expect(createUuid(null)).toMatch(UUID_V4_PATTERN)
  })
})
