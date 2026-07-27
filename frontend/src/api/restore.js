import { request } from './client'

function query(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

export const previewRestore = (recordId, input) => request(`/records/${recordId}/restore-preview`, { method: 'POST', body: JSON.stringify(input) })
export const executeRestore = (recordId, input, idempotencyKey) => request(`/records/${recordId}/restore`, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify(input) })
export const fetchRestoreOperations = (recordId, params = {}) => request(`/records/${recordId}/restore-operations${query(params)}`)
