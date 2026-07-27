import { API_BASE_URL, ApiError, request } from './client'

export const fetchAttachments = (recordId) => request(`/records/${recordId}/attachments`)
export const fetchRevisionAttachments = (revisionId) => request(`/revisions/${revisionId}/attachments`)
export const deleteAttachment = (id) => request(`/attachments/${id}`, { method: 'DELETE' })
export const previewAttachment = (id) => request(`/attachments/${id}/preview`, { responseType: 'blob' })
export const downloadAttachment = (id) => request(`/attachments/${id}/download`, { responseType: 'blob' })

export function uploadAttachment(recordId, file, onProgress = () => {}) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${API_BASE_URL}/records/${recordId}/attachments`)
    xhr.setRequestHeader('Accept', 'application/json')
    const token = localStorage.getItem('auth_token')
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100))
    }
    xhr.onerror = () => reject(new ApiError('无法连接服务器，请稍后重试', 'NETWORK_ERROR', null, 0))
    xhr.onload = () => {
      const payload = (() => { try { return JSON.parse(xhr.responseText) } catch { return null } })()
      if (xhr.status === 401) {
        localStorage.removeItem('auth_token')
        window.dispatchEvent(new Event('bionote:unauthorized'))
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(new ApiError(payload?.message || '上传失败', payload?.code || 'UPLOAD_FAILED', payload?.fieldErrors, xhr.status))
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

export function saveBlob(blob, headers, fallbackName) {
  const disposition = headers?.get?.('Content-Disposition') || ''
  const utf8 = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  const filename = utf8 ? decodeURIComponent(utf8[1]) : fallbackName
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}
