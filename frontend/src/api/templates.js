import { request } from './client'

const qs = (params = {}) => {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value)
  })
  return search.toString() ? `?${search}` : ''
}

export const fetchTemplates = (params) => request(`/templates${qs(params)}`)
export const fetchTemplate = (id) => request(`/templates/${id}`)
export const createTemplate = (input) => request('/templates', { method: 'POST', body: JSON.stringify(input) })
export const updateTemplate = (id, input) => request(`/templates/${id}`, { method: 'PUT', body: JSON.stringify(input) })
export const deleteTemplate = (id) => request(`/templates/${id}`, { method: 'DELETE' })
export const copyTemplate = (id) => request(`/templates/${id}/copy`, { method: 'POST' })
