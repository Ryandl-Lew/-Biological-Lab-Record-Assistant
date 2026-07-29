import { API_BASE_URL, ApiError, request } from './client'

export const sendRecordAgentChat = (recordId, input) =>
  request(`/records/${recordId}/agent-chat`, {
    method: 'POST',
    body: JSON.stringify(input),
  })

export const sendProjectAgentChat = (projectId, input) =>
  request(`/projects/${projectId}/agent-chat`, {
    method: 'POST',
    body: JSON.stringify(input),
  })

export function uploadProjectAgentReference(projectId, file, onProgress = () => {}) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${API_BASE_URL}/projects/${projectId}/agent-chat/references`)
    xhr.setRequestHeader('Accept', 'application/json')
    const token = localStorage.getItem('auth_token')
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100))
    }
    xhr.onerror = () => reject(new ApiError('无法连接服务器，请稍后重试', 'NETWORK_ERROR', null, 0))
    xhr.onload = () => {
      const payload = (() => {
        try {
          return JSON.parse(xhr.responseText)
        } catch {
          return null
        }
      })()
      if (xhr.status === 401) {
        localStorage.removeItem('auth_token')
        window.dispatchEvent(new Event('bionote:unauthorized'))
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(
          new ApiError(
            payload?.message || '上传失败',
            payload?.code || 'UPLOAD_FAILED',
            payload?.fieldErrors,
            xhr.status,
          ),
        )
        return
      }
      onProgress(100)
      resolve(payload?.data)
    }
    const body = new FormData()
    body.append('file', file)
    xhr.send(body)
  })
}

export const deleteProjectAgentReference = (projectId, referenceId) =>
  request(`/projects/${projectId}/agent-chat/references/${referenceId}`, { method: 'DELETE' })
