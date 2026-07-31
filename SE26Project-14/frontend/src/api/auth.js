import { request } from './client'

/**
 * 登录
 * @param {{ email: string, password: string }} credentials
 */
export function login(credentials) {
  return request('/auth/login', {
    method: 'POST',
    body: JSON.stringify(credentials),
  })
}

export function register(account) {
  return request('/auth/register', {
    method: 'POST',
    body: JSON.stringify(account),
  })
}

/**
 * 登出
 */
export function logout() {
  return request('/auth/logout', { method: 'POST' })
}

/**
 * 获取当前登录用户（通过 token 恢复会话）
 */
export function getCurrentUser() {
  return request('/auth/me')
}
