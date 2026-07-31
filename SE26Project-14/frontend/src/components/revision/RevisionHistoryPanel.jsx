import { useEffect, useState } from 'react'
import { fetchRevisionDetail, fetchRevisionDiff } from '@/api'
import { Badge, Button, EmptyState, StatusBadge, Surface } from '@/components/ui'
import RestorePreviewDialog from '@/components/restore/RestorePreviewDialog'
import RevisionDetailDrawer from './RevisionDetailDrawer'
import RevisionDiffViewer from './RevisionDiffViewer'

export default function RevisionHistoryPanel({
  record,
  revisions,
  meta,
  loading,
  initialFrom,
  initialTo,
  initialRevisionId,
  onPageChange,
  onSelectionChange,
  onRestored,
}) {
  const [detail, setDetail] = useState(null),
    [detailLoading, setDetailLoading] = useState(false),
    [detailError, setDetailError] = useState('')
  const [from, setFrom] = useState(initialFrom || ''),
    [to, setTo] = useState(initialTo || '')
  const [diff, setDiff] = useState(null),
    [diffLoading, setDiffLoading] = useState(false),
    [diffError, setDiffError] = useState('')
  const [restoreRevision, setRestoreRevision] = useState(null)
  useEffect(() => {
    setFrom(initialFrom || '')
    setTo(initialTo || '')
  }, [initialFrom, initialTo])
  useEffect(() => {
    if (!initialFrom || !initialTo || initialFrom === initialTo) return
    setDiffLoading(true)
    setDiffError('')
    fetchRevisionDiff(record.id, {
      fromRevisionId: initialFrom,
      ...(initialTo === 'WORKING_COPY' ? { to: 'WORKING_COPY' } : { toRevisionId: initialTo }),
      includeUnchanged: true,
    })
      .then(setDiff)
      .catch((error) => setDiffError(error.message))
      .finally(() => setDiffLoading(false))
  }, [initialFrom, initialTo, record.id])
  useEffect(() => {
    if (initialRevisionId) openDetail(initialRevisionId)
  }, [initialRevisionId]) // eslint-disable-line react-hooks/exhaustive-deps
  const openDetail = async (id) => {
    setDetail(null)
    setDetailError('')
    setDetailLoading(true)
    try {
      setDetail(await fetchRevisionDetail(record.id, id))
    } catch (error) {
      setDetailError(error.message)
    } finally {
      setDetailLoading(false)
    }
  }
  const choose = (kind, value) => {
    if (kind === 'from') setFrom(value)
    else setTo(value)
    setDiff(null)
    setDiffError('')
    onSelectionChange?.(kind === 'from' ? value : from, kind === 'to' ? value : to)
  }
  const compare = async () => {
    setDiffLoading(true)
    setDiffError('')
    try {
      setDiff(
        await fetchRevisionDiff(record.id, {
          fromRevisionId: from,
          ...(to === 'WORKING_COPY' ? { to: 'WORKING_COPY' } : { toRevisionId: to }),
          includeUnchanged: true,
        }),
      )
    } catch (error) {
      setDiffError(error.message)
    } finally {
      setDiffLoading(false)
    }
  }
  const same = !from || !to || from === to
  return (
    <div className="space-y-5">
      <Surface
        title="版本历史"
        extra={
          meta?.totalElements ? (
            <span className="text-xs text-slate-400">共 {meta.totalElements} 个不可变版本</span>
          ) : null
        }
      >
        {loading ? (
          <p role="status" className="py-10 text-center text-sm text-slate-400">
            加载版本历史中…
          </p>
        ) : revisions.length ? (
          <ol className="space-y-3">
            {revisions.map((revision) => (
              <li key={revision.id} className="rounded-xl border p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <button className="text-left" onClick={() => openDetail(revision.id)}>
                    <span className="font-semibold">
                      {revision.label || `R${revision.revisionNo}`}
                    </span>
                    <span className="ml-2 text-sm text-slate-500">
                      {revision.submitterName} · {new Date(revision.submittedAt).toLocaleString()}
                    </span>
                    <p className="mt-1 text-sm text-slate-500">
                      {revision.submitNote || '无提交说明'} · {revision.attachmentCount} 个附件
                    </p>
                  </button>
                  <div className="flex flex-wrap gap-2">
                    <StatusBadge kind="review" status={revision.review?.status || 'PENDING'} />
                    {revision.current && <Badge tone="blue">当前审核版本</Badge>}
                    {revision.finalRevision && <Badge tone="green">最终批准版本</Badge>}
                  </div>
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button size="sm" variant="secondary" onClick={() => openDetail(revision.id)}>
                    查看详情
                  </Button>
                  {record.capabilities?.canRestore &&
                    !['IN_REVIEW', 'COMPLETED'].includes(record.status) && (
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => setRestoreRevision(revision)}
                      >
                        恢复为工作副本
                      </Button>
                    )}
                </div>
              </li>
            ))}
          </ol>
        ) : (
          <EmptyState
            title="尚无正式提交版本"
            description="自动保存不会生成 revision。首次提交审核后会出现 R1。"
          />
        )}
        {meta?.totalPages > 1 && (
          <div className="mt-4 flex justify-end gap-2">
            <Button
              size="sm"
              variant="secondary"
              disabled={meta.page === 0}
              onClick={() => onPageChange(meta.page - 1)}
            >
              上一页
            </Button>
            <Button
              size="sm"
              variant="secondary"
              disabled={meta.page + 1 >= meta.totalPages}
              onClick={() => onPageChange(meta.page + 1)}
            >
              下一页
            </Button>
          </div>
        )}
      </Surface>
      {revisions.length > 0 && (
        <Surface title="比较版本">
          <div className="grid gap-4 md:grid-cols-2">
            <label className="text-sm">
              <span className="field-label">基准版本</span>
              <select
                aria-label="基准版本"
                className="input"
                value={from}
                onChange={(event) => choose('from', event.target.value)}
              >
                <option value="">请选择</option>
                {revisions.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-sm">
              <span className="field-label">目标版本</span>
              <select
                aria-label="目标版本"
                className="input"
                value={to}
                onChange={(event) => choose('to', event.target.value)}
              >
                <option value="">请选择</option>
                {revisions.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.label}
                  </option>
                ))}
                <option value="WORKING_COPY">当前工作副本</option>
              </select>
            </label>
          </div>
          <div className="mt-3 flex items-center gap-3">
            <Button disabled={same} onClick={compare}>
              查看结构化差异
            </Button>
            {from && to && from === to && (
              <span className="text-sm text-amber-700">基准版本与目标版本不能相同</span>
            )}
          </div>
          <div className="mt-5">
            <RevisionDiffViewer
              diff={diff}
              loading={diffLoading}
              error={diffError}
              onRetry={compare}
            />
          </div>
        </Surface>
      )}
      <RevisionDetailDrawer
        detail={detail}
        loading={detailLoading}
        error={detailError}
        onClose={() => {
          setDetail(null)
          setDetailError('')
        }}
      />
      {restoreRevision && (
        <RestorePreviewDialog
          open
          record={record}
          revision={restoreRevision}
          onClose={() => setRestoreRevision(null)}
          onSuccess={(result) => {
            const revision = restoreRevision
            setRestoreRevision(null)
            onRestored(result, revision)
          }}
        />
      )}
    </div>
  )
}
