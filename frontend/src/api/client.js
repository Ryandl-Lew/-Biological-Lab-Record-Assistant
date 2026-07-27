/** 后端基础地址，可通过 VITE_API_BASE_URL 覆盖。 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export class ApiError extends Error {
  constructor(message, code, fieldErrors, status) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.fieldErrors = fieldErrors
    this.status = status
  }
}

/**
 * 发送请求并解包后端统一 ApiResponse。
 * @param {string} path
 * @param {RequestInit} [options]
 */
export async function request(path, options = {}) {
  const { responseType = 'json', ...fetchOptions } = options
  const token = localStorage.getItem('auth_token')
  const headers = new Headers(fetchOptions.headers)
  headers.set('Accept', responseType === 'blob' ? '*/*' : 'application/json')
  if (fetchOptions.body && !(fetchOptions.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...fetchOptions, headers })
  } catch {
    throw new ApiError('无法连接服务器，请稍后重试', 'NETWORK_ERROR', null, 0)
  }

  const payload = response.status === 204 ? null : responseType === 'blob'
    ? await response.blob()
    : await response.json().catch(() => null)
  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem('auth_token')
      window.dispatchEvent(new Event('bionote:unauthorized'))
    }
    throw new ApiError(
      payload?.message || '请求失败，请稍后重试',
      payload?.code || 'REQUEST_FAILED',
      payload?.fieldErrors || null,
      response.status,
    )
  }
  if (responseType === 'blob') return { blob: payload, headers: response.headers }
  if (payload?.meta) return { items: payload.data, meta: payload.meta }
  return payload?.data ?? null
}
