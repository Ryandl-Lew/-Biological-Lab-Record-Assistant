import { request } from './client'

const qs = (params = {}) => {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value)
  })
  return search.toString() ? `?${search}` : ''
}

export const fetchRecords = (params) => request(`/records${qs(typeof params === 'string' ? { projectId: params } : params)}`)
export const fetchRecord = (id) => request(`/records/${id}`)
export const reserveRecord = (input) => request('/records/reservations', { method: 'POST', body: JSON.stringify(input) })
export const discardRecordReservation = (id) => request(`/records/${id}/reservation`, { method: 'DELETE' })
export const createRecord = (input) => request('/records', { method: 'POST', body: JSON.stringify(input) })
export const updateRecord = (id, input) => request(`/records/${id}`, { method: 'PUT', body: JSON.stringify(input) })
export const deleteRecord = (id) => request(`/records/${id}`, { method: 'DELETE' })
