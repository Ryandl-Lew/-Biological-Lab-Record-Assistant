import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Download, ExternalLink, Eye, FileText, Paperclip, Trash2, Upload } from 'lucide-react'
import { Button, ConfirmDialog, EmptyState, Surface } from '@/components/ui'
import {
  deleteAttachment,
  downloadAttachment,
  fetchAttachments,
  previewAttachment,
  saveBlob,
  uploadAttachment,
} from '@/api'

const PdfPreview = lazy(() => import('./PdfPreview'))

const prettySize = (bytes) =>
  bytes < 1024
    ? `${bytes} B`
    : bytes < 1024 ** 2
      ? `${(bytes / 1024).toFixed(1)} KB`
      : `${(bytes / 1024 ** 2).toFixed(1)} MB`

const parseCSV = (text) => {
  const rows = []
  let row = []
  let cell = ''
  let inQuotes = false
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (inQuotes) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          cell += '"'
          i++
        } else inQuotes = false
      } else cell += ch
    } else if (ch === '"') {
      inQuotes = true
    } else if (ch === ',') {
      row.push(cell)
      cell = ''
    } else if (ch === '\n' || (ch === '\r' && text[i + 1] === '\n')) {
      if (ch === '\r') i++
      row.push(cell)
      cell = ''
      if (row.length > 0) {
        rows.push(row)
        row = []
      }
    } else cell += ch
  }
  row.push(cell)
  if (row.length > 0) rows.push(row)
  return rows
}

export default function AttachmentManager({
  recordId,
  readOnly = false,
  initialItems,
  onChange,
  compact = false,
}) {
  const [items, setItems] = useState(initialItems || []),
    [error, setError] = useState(''),
    [progress, setProgress] = useState(null),
    [preview, setPreview] = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const inputRef = useRef(null),
    objectUrl = useRef(null)
  const load = useCallback(async () => {
    if (initialItems) {
      setItems(initialItems)
      return
    }
    try {
      setItems(await fetchAttachments(recordId))
    } catch (e) {
      setError(e.message)
    }
  }, [initialItems, recordId])
  useEffect(() => {
    load()
  }, [load])
  useEffect(
    () => () => {
      if (objectUrl.current) URL.revokeObjectURL(objectUrl.current)
    },
    [],
  )
  const upload = async (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    setError('')
    setProgress(0)
    try {
      await uploadAttachment(recordId, file, setProgress)
      await load()
      onChange?.()
      setProgress(null)
    } catch (e) {
      setError(e.message)
      setProgress('failed')
    } finally {
      event.target.value = ''
    }
  }
  const csvRows = useMemo(
    () => (preview?.csv ? parseCSV(preview.text) : []),
    [preview?.csv, preview?.text],
  )
  const remove = (item) => {
    setDeleteTarget(item)
  }
  const doDelete = async () => {
    if (!deleteTarget) return
    try {
      await deleteAttachment(deleteTarget.id)
      await load()
      onChange?.()
      setDeleteTarget(null)
    } catch (e) {
      setError(e.message)
      setDeleteTarget(null)
    }
  }
  const showPreview = async (item) => {
    try {
      if (objectUrl.current) URL.revokeObjectURL(objectUrl.current)
      const { blob } = await previewAttachment(item.id)
      objectUrl.current = URL.createObjectURL(blob)
      const name = item.originalFilename || item.filename || ''
      const lowerName = name.toLowerCase()
      const markdown = item.mediaType === 'text/markdown' || lowerName.endsWith('.md')
      const csv = item.mediaType === 'text/csv' || lowerName.endsWith('.csv')
      const text = markdown || csv ? await blob.text() : null
      setPreview({ ...item, blob, url: objectUrl.current, markdown, csv, text })
    } catch (e) {
      setError(e.message)
    }
  }
  const closePreview = () => {
    setPreview(null)
    if (objectUrl.current) {
      URL.revokeObjectURL(objectUrl.current)
      objectUrl.current = null
    }
  }
  const download = async (item) => {
    try {
      const result = await downloadAttachment(item.id)
      saveBlob(result.blob, result.headers, item.originalFilename || item.filename)
    } catch (e) {
      setError(e.message)
    }
  }
  return (
    <>
      <Surface
        className={compact ? 'p-4' : ''}
        title={
          <span className="flex items-center gap-2">
            <Paperclip size={18} />
            记录附件
          </span>
        }
        extra={
          !readOnly && (
            <>
              <input
                ref={inputRef}
                aria-label="选择附件"
                type="file"
                className="hidden"
                onChange={upload}
              />
              <Button size="sm" icon={Upload} onClick={() => inputRef.current?.click()}>
                上传附件
              </Button>
            </>
          )
        }
      >
        {error && (
          <p role="alert" className="mb-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">
            {error}
            {progress === 'failed' && (
              <button className="ml-2 underline" onClick={() => inputRef.current?.click()}>
                重试
              </button>
            )}
          </p>
        )}
        {typeof progress === 'number' && (
          <div aria-label="上传进度" className="mb-3 h-2 overflow-hidden rounded bg-slate-100">
            <div className="h-full bg-brand-600 transition-all" style={{ width: `${progress}%` }} />
          </div>
        )}
        {items.length === 0 ? (
          <EmptyState
            title="暂无附件"
            description={
              compact ? undefined : readOnly ? '该修订没有附件' : '上传图片、PDF 或实验数据文件'
            }
          />
        ) : (
          <ul className="divide-y">
            {items.map((item) => {
              const name = item.originalFilename || item.filename
              return (
                <li
                  id={`attachment-${item.id}`}
                  key={item.id}
                  className={`scroll-mt-24 py-3 target:bg-brand-50 ${compact ? 'space-y-2' : 'flex flex-wrap items-center gap-3'}`}
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium" title={name}>
                      {name}
                    </p>
                    <p className="text-xs text-slate-400">
                      {prettySize(item.sizeBytes)}
                      {!compact && item.uploaderName ? ` · ${item.uploaderName}` : ''}
                      {!compact && item.createdAt
                        ? ` · ${new Date(item.createdAt).toLocaleString()}`
                        : ''}
                    </p>
                  </div>
                  <div className={`flex flex-wrap gap-1 ${compact ? 'justify-start' : ''}`}>
                    {item.previewable && (
                      <Button
                        size="sm"
                        variant="ghost"
                        icon={Eye}
                        onClick={() => showPreview(item)}
                      >
                        预览
                      </Button>
                    )}
                    <Button
                      size="sm"
                      variant="ghost"
                      icon={Download}
                      onClick={() => download(item)}
                    >
                      下载
                    </Button>
                    {!readOnly && item.canDelete !== false && (
                      <Button size="sm" variant="danger" icon={Trash2} onClick={() => remove(item)}>
                        删除
                      </Button>
                    )}
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </Surface>
      {preview && (
        <div
          role="dialog"
          aria-label="附件预览"
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4"
          onClick={closePreview}
        >
          <div
            className="flex h-[88vh] w-full max-w-6xl flex-col overflow-hidden rounded-2xl bg-white/95 shadow-2xl backdrop-blur-sm"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-5 py-4">
              <div className="min-w-0">
                <h2 className="truncate font-semibold">
                  {preview.originalFilename || preview.filename}
                </h2>
                <p className="mt-0.5 text-xs text-slate-400">
                  {preview.markdown
                    ? 'Markdown 文本预览'
                    : preview.csv
                      ? 'CSV 表格预览'
                      : preview.mediaType === 'application/pdf'
                        ? 'PDF 安全预览'
                        : '图片预览'}
                </p>
              </div>
              <div className="flex gap-2">
                {preview.mediaType === 'application/pdf' && (
                  <Button
                    size="sm"
                    variant="secondary"
                    icon={ExternalLink}
                    onClick={() => window.open(preview.url, '_blank', 'noopener,noreferrer')}
                  >
                    新窗口打开
                  </Button>
                )}
                <Button size="sm" variant="secondary" onClick={closePreview}>
                  关闭
                </Button>
              </div>
            </div>
            <div className="min-h-0 flex-1 bg-slate-100 p-3">
              {preview.mediaType?.startsWith('image/') ? (
                <img
                  src={preview.url}
                  alt={preview.originalFilename || preview.filename}
                  className="h-full w-full object-contain"
                />
              ) : preview.markdown ? (
                <div className="h-full overflow-auto rounded-xl border border-slate-200 bg-white">
                  <div className="flex items-center gap-2 border-b border-slate-100 px-5 py-3 text-xs font-medium text-slate-500">
                    <FileText size={14} />
                    UTF-8 Markdown 源文件
                  </div>
                  <pre className="whitespace-pre-wrap break-words p-5 font-mono text-sm leading-7 text-slate-700">
                    {preview.text}
                  </pre>
                </div>
              ) : preview.csv ? (
                <div className="h-full overflow-auto rounded-xl border border-slate-200 bg-white">
                  <div className="flex items-center gap-2 border-b border-slate-100 px-5 py-3 text-xs font-medium text-slate-500">
                    <FileText size={14} />
                    CSV 表格预览
                  </div>
                  <div className="overflow-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-slate-200 bg-slate-50">
                          {csvRows[0]?.map((header, i) => (
                            <th
                              key={i}
                              className="sticky top-0 whitespace-nowrap border-r border-slate-200 bg-slate-50 px-4 py-2 text-left font-semibold text-slate-600"
                            >
                              {header}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {csvRows.slice(1).map((row, ri) => (
                          <tr key={ri} className="border-b border-slate-100 hover:bg-slate-50">
                            {row.map((cell, ci) => (
                              <td
                                key={ci}
                                className="whitespace-nowrap border-r border-slate-100 px-4 py-1.5 text-slate-700"
                              >
                                {cell}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              ) : preview.mediaType === 'application/pdf' ? (
                <Suspense
                  fallback={
                    <p role="status" className="py-12 text-center text-sm text-slate-500">
                      正在加载 PDF 预览器…
                    </p>
                  }
                >
                  <PdfPreview blob={preview.blob} />
                </Suspense>
              ) : (
                <p className="py-12 text-center text-sm text-slate-500">该文件暂不支持预览</p>
              )}
            </div>
          </div>
        </div>
      )}
      <ConfirmDialog
        open={!!deleteTarget}
        title="确认删除附件"
        message={`确认删除"${deleteTarget?.originalFilename || deleteTarget?.filename || ''}"？历史已提交修订仍会保留该文件。`}
        onConfirm={doDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </>
  )
}
