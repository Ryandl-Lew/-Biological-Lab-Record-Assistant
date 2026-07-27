import { request } from './client'

export const fetchNotifications = (params = {}) => {
  const search = new URLSearchParams(params)
  return request(`/notifications?${search}`)
}
export const fetchUnreadCount = () => request('/notifications/unread-count')
export const markNotificationRead = (id) => request(`/notifications/${id}/read`, { method: 'PATCH' })
export const markAllNotificationsRead = () => request('/notifications/read-all', { method: 'POST' })
export const acceptInvitation = (id) => request(`/invitations/${id}/accept`, { method: 'POST' })
export const rejectInvitation = (id) => request(`/invitations/${id}/reject`, { method: 'POST' })
