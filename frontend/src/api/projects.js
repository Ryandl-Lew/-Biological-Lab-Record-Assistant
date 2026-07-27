import { request } from './client'

const query = (params = {}) => {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== '') search.set(key, value) })
  const value = search.toString()
  return value ? `?${value}` : ''
}

export const fetchProjects = (params) => request(`/projects${query(params)}`)
export const fetchProject = (id) => request(`/projects/${id}`)
export const createProject = (input) => request('/projects', { method: 'POST', body: JSON.stringify(input) })
export const archiveProject = (id) => request(`/projects/${id}/archive`, { method: 'POST' })
export const fetchProjectMembers = (id) => request(`/projects/${id}/members`)
export const inviteProjectMember = (id, email) => request(`/projects/${id}/invitations`, { method: 'POST', body: JSON.stringify({ email }) })
export const updateProjectMemberRole = (projectId, userId, role, reassignments = {}) => request(`/projects/${projectId}/members/${userId}/role`, { method: 'PATCH', body: JSON.stringify({ role, reassignments }) })
export const removeProjectMember = (projectId, userId, reassignments = {}) => request(`/projects/${projectId}/members/${userId}/remove`, { method: 'POST', body: JSON.stringify({ reassignments }) })

export const fetchProjectAuditEvents = (id, params) => request(`/projects/${id}/audit-events${query(params)}`)
export const fetchProjectAttachments = (id, params) => request(`/projects/${id}/attachments${query(params)}`)
