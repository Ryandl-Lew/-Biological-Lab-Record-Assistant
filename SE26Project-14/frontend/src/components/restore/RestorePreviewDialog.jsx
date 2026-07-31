import { useEffect, useRef, useState } from 'react'
import { executeRestore, previewRestore } from '@/api'
import { Button } from '@/components/ui'
import { createUuid } from '@/lib/uuid'

const newKey = () => createUuid()

export default function RestorePreviewDialog({ record, revision, open, onClose, onSuccess }) {
  const [restoreAttachments, setRestoreAttachments] = useState(true)
  const [preview, setPreview] = useState(null)
  const [loading, setLoading] = useState(false)
  const [executing, setExecuting] = useState(false)
  const [error, setError] = useState(null)
  const keyRef = useRef(newKey())
  const closeRef = useRef(null)
  const load = async (attachments = restoreAttachments) => {
    setLoading(true)
    setError(null)
    try {
      setPreview(
        await previewRestore(record.id, {
          sourceRevisionId: revision.id,
          expectedRecordVersion: record.version,
          restoreAttachments: attachments,
        }),
      )
    } catch (requestError) {
      setPreview(null)
      setError(requestError)
    } finally {
      setLoading(false)
    }
  }
  useEffect(() => {
    if (open) {
      setRestoreAttachments(true)
      keyRef.current = newKey()
      load(true)
      setTimeout(() => closeRef.current?.focus(), 0)
    }
  }, [open, record.id, record.version, revision.id]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (!open) return undefined
    const escape = (event) => {
      if (event.key === 'Escape' && !executing) onClose()
    }
    window.addEventListener('keydown', escape)
    return () => window.removeEventListener('keydown', escape)
  }, [executing, onClose, open])
  if (!open) return null
  const changeAttachments = (checked) => {
    setRestoreAttachments(checked)
    keyRef.current = newKey()
    load(checked)
  }
  const execute = async () => {
    if (!preview) return
    setExecuting(true)
    setError(null)
    try {
      onSuccess(
        await executeRestore(
          record.id,
          {
            sourceRevisionId: revision.id,
            expectedRecordVersion: preview.expectedRecordVersion,
            restoreAttachments,
            previewToken: preview.previewToken,
          },
          keyRef.current,
        ),
      )
    } catch (requestError) {
      setError(requestError)
    } finally {
      setExecuting(false)
    }
  }
  const stale =
    error?.code === 'RESTORE_PREVIEW_STALE' || error?.code === 'OPTIMISTIC_LOCK_CONFLICT'
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="恢复预览"
    >
      <div className="max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-white/95 p-6 shadow-2xl backdrop-blur-sm">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold">
              恢复 {revision.label || `R${revision.revisionNo}`} 为工作副本
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              历史版本不会删除或修改；下次提交才会生成新的 Rn。
            </p>
          </div>
          <Button ref={closeRef} variant="secondary" disabled={executing} onClick={onClose}>
            关闭
          </Button>
        </div>
        <label className="mt-5 flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={restoreAttachments}
            disabled={loading || executing}
            onChange={(event) => changeAttachments(event.target.checked)}
          />
          同时恢复附件状态（切换会重新生成预览）
        </label>
        {loading && (
          <p role="status" className="py-12 text-center text-slate-400">
            正在生成恢复预览…
          </p>
        )}
        {error && (
          <div role="alert" className="mt-5 rounded-lg bg-red-50 p-3 text-sm text-red-700">
            <p>{error.message}</p>
            {stale && (
              <Button className="mt-3" size="sm" variant="secondary" onClick={() => load()}>
                重新生成预览
              </Button>
            )}
          </div>
        )}
        {preview && !loading && (
          <div className="mt-5 space-y-4">
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-lg bg-slate-50 p-3">
                <div className="text-xs text-slate-400">来源</div>
                <b>R{preview.sourceRevision.revisionNo}</b>
              </div>
              <div className="rounded-lg bg-slate-50 p-3">
                <div className="text-xs text-slate-400">当前工作副本版本</div>
                <b>{preview.expectedRecordVersion}</b>
              </div>
              <div className="rounded-lg bg-slate-50 p-3">
                <div className="text-xs text-slate-400">预览有效至</div>
                <b className="text-sm">{new Date(preview.expiresAt).toLocaleTimeString()}</b>
              </div>
            </div>
            <div className="rounded-lg border p-4 text-sm">
              <h3 className="font-medium">变更概览</h3>
              <p className="mt-2">
                新增 {preview.diff.summary.added} · 删除 {preview.diff.summary.removed} · 修改{' '}
                {preview.diff.summary.modified} · 附件新增 {preview.diff.summary.attachmentAdded} ·
                附件删除 {preview.diff.summary.attachmentRemoved}
              </p>
            </div>
            <div className="rounded-lg border p-4 text-sm">
              <h3 className="font-medium">附件计划</h3>
              <p className="mt-2">
                重新启用 {preview.attachmentPlan.activate.length} · 软删除{' '}
                {preview.attachmentPlan.softDelete.length} · 保留{' '}
                {preview.attachmentPlan.keep.length}
              </p>
              {preview.attachmentPlan.missingPhysicalFiles.length > 0 && (
                <p className="mt-2 text-red-700">
                  缺失物理文件：{preview.attachmentPlan.missingPhysicalFiles.length}
                </p>
              )}
            </div>
            {preview.warnings?.map((warning) => (
              <p key={warning} className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
                {warning}
              </p>
            ))}
          </div>
        )}
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" disabled={executing} onClick={onClose}>
            取消
          </Button>
          <Button
            loading={executing}
            disabled={!preview || loading || preview.capabilities?.canExecute === false}
            onClick={execute}
          >
            确认恢复工作副本
          </Button>
        </div>
      </div>
    </div>
  )
}
