import { request } from './client'

export function updateMyProfile(displayName) {
  return request('/users/me', { method: 'PUT', body: JSON.stringify({ displayName }) })
}

export function uploadMyAvatar(file) {
  const body = new FormData()
  body.append('file', file)
  return request('/users/me/avatar', { method: 'POST', body })
}
