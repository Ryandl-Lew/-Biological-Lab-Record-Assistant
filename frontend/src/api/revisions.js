import { request } from './client'

function query(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

export const fetchRevisionSummaries = (recordId, params = {}) =>
  request(`/records/${recordId}/revisions${query(params)}`)
export const fetchRevisionDetail = (recordId, revisionId) =>
  request(`/records/${recordId}/revisions/${revisionId}`)
export const fetchRevisionDiff = (recordId, params) =>
  request(`/records/${recordId}/revision-diff${query(params)}`)
