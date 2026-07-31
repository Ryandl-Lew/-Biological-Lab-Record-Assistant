import { useEffect, useRef } from 'react'
import { Button, StatusBadge } from '@/components/ui'

export default function RevisionDetailDrawer({ detail, loading, error, onClose }) {
  const closeRef = useRef(null)
  useEffect(() => {
    if (!detail && !loading && !error) return undefined
    closeRef.current?.focus()
    const escape = (event) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', escape)
    return () => window.removeEventListener('keydown', escape)
  }, [detail, error, loading, onClose])
  if (!detail && !loading && !error) return null
  return (
    <div
      className="fixed inset-0 z-50 flex justify-end bg-slate-950/50"
      role="dialog"
      aria-modal="true"
      aria-label="版本详情"
    >
      <div className="h-full w-full max-w-2xl overflow-y-auto bg-white/85 p-5 shadow-2xl backdrop-blur-sm">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-semibold">{detail?.label || '版本详情'}</h2>
            {detail && (
              <p className="mt-1 text-sm text-slate-500">
                {detail.submitterName} · {new Date(detail.submittedAt).toLocaleString()}
              </p>
            )}
          </div>
          <Button ref={closeRef} variant="secondary" onClick={onClose}>
            关闭
          </Button>
        </div>
        {loading && (
          <p role="status" className="py-16 text-center text-slate-400">
            加载版本详情中…
          </p>
        )}
        {error && (
          <p role="alert" className="mt-6 rounded-lg bg-red-50 p-3 text-sm text-red-700">
            {error}
          </p>
        )}
        {detail && (
          <div className="mt-6 space-y-6">
            <div className="flex flex-wrap gap-2">
              {detail.current && (
                <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs text-blue-700">
                  当前审核版本
                </span>
              )}
              {detail.finalRevision && (
                <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs text-emerald-700">
                  最终批准版本
                </span>
              )}
              {detail.review && <StatusBadge kind="review" status={detail.review.status} />}
            </div>
            <section>
              <h3 className="font-medium">固定字段</h3>
              <dl className="mt-3 grid gap-3 sm:grid-cols-2">
                {[
                  ['标题', detail.snapshot.title],
                  ['实验类型', detail.snapshot.experimentType],
                  ['实验日期', detail.snapshot.experimentDate],
                  ['实验目的', detail.snapshot.purpose],
                ].map(([label, value]) => (
                  <div key={label} className="rounded-lg bg-slate-50 p-3">
                    <dt className="text-xs text-slate-400">{label}</dt>
                    <dd className="mt-1 whitespace-pre-wrap text-sm">{value || '—'}</dd>
                  </div>
                ))}
              </dl>
            </section>
            <section>
              <h3 className="font-medium">模板字段</h3>
              <pre className="mt-3 overflow-auto rounded-lg bg-slate-50 p-3 text-xs">
                {JSON.stringify(detail.snapshot.fieldValues || {}, null, 2)}
              </pre>
            </section>
            <section>
              <h3 className="font-medium">正文</h3>
              <p className="mt-3 whitespace-pre-wrap rounded-lg bg-slate-50 p-4 text-sm leading-7">
                {detail.snapshot.contentPlainText || '暂无正文'}
              </p>
            </section>
            <section>
              <h3 className="font-medium">附件</h3>
              {detail.attachments?.length ? (
                <ul className="mt-3 space-y-2">
                  {detail.attachments.map((item) => (
                    <li key={item.id} className="rounded-lg border p-3 text-sm">
                      <b>{item.filename}</b>
                      <span className="ml-2 text-xs text-slate-400">
                        {item.mediaType} · {item.sizeBytes} B
                      </span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="mt-2 text-sm text-slate-400">该版本无附件</p>
              )}
            </section>
            <section>
              <h3 className="font-medium">提交与审核</h3>
              <div className="mt-3 space-y-2 rounded-lg border p-4 text-sm">
                <p>提交说明：{detail.submitNote || '无'}</p>
                {detail.review ? (
                  <>
                    <p>审核人：{detail.review.reviewerName}</p>
                    <p>审核状态：{detail.review.status}</p>
                    <p>审核意见：{detail.review.decisionComment || '无'}</p>
                  </>
                ) : (
                  <p>尚无审核信息</p>
                )}
              </div>
            </section>
          </div>
        )}
      </div>
    </div>
  )
}
