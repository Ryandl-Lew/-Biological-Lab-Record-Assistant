import { request } from './client'
export const fetchExportPreview = (recordId) => request(`/records/${recordId}/exports/preview`)
export const downloadMarkdown = (recordId) => request(`/records/${recordId}/exports/markdown`, { responseType: 'blob' })
export const downloadPdf = (recordId) => request(`/records/${recordId}/exports/pdf`, { responseType: 'blob' })
