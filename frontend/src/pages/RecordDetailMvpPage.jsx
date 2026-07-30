import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import DOMPurify from 'dompurify'
import { ArrowLeft, CheckCircle, Download, Eye, Pencil, Send, Trash2, XCircle } from 'lucide-react'
import { Badge, Button, EmptyState, PageHeader, StatusBadge, Surface, Tabs } from '@/components/ui'
import {
  approveReview,
  deleteRecord,
  downloadMarkdown,
  downloadPdf,
  fetchExportPreview,
  fetchRecord,
  fetchRevisionSummaries,
  requestReviewChanges,
  saveBlob,
} from '@/api'
import { saveRecordSession, appendSessionMessages } from '@/api/agentChat'
import { TEMPLATE_FIELD_TYPE_LABELS } from '@/domain'
import AttachmentManager from '@/components/record/AttachmentManager'
import SubmissionDialog from '@/components/record/SubmissionDialog'

const RevisionHistoryPanel = lazy(() => import('@/components/revision/RevisionHistoryPanel'))
const RecordSummaryPanel = lazy(() => import('@/components/agent/RecordSummaryPanel'))
const AutoSummaryPanel = lazy(() => import('@/components/agent/AutoSummaryPanel'))

export default function RecordDetailMvpPage() {
  const { recordId } = useParams(),
    navigate = useNavigate(),
    [searchParams, setSearchParams] = useSearchParams()
  const [record, setRecord] = useState(null),
    [revisions, setRevisions] = useState([]),
    [revisionMeta, setRevisionMeta] = useState(null),
    [historyLoading, setHistoryLoading] = useState(false),
    [error, setError] = useState(''),
    [comment, setComment] = useState(''),
    [deciding, setDeciding] = useState(false),
    [previewHtml, setPreviewHtml] = useState(''),
    [submitOpen, setSubmitOpen] = useState(false),
    [sessionId, setSessionId] = useState(null),
    [chatKey, setChatKey] = useState(0)
  const tab = searchParams.get('tab') || 'current'
  const loadHistory = useCallback(
    async (page = 0) => {
      setHistoryLoading(true)
      try {
        const history = await fetchRevisionSummaries(recordId, { page, size: 20 })
        setRevisions(history.items)
        setRevisionMeta(history.meta)
      } catch (requestError) {
        setError(requestError.message)
      } finally {
        setHistoryLoading(false)
      }
    },
    [recordId],
  )
  const load = useCallback(async () => {
    try {
      const value = await fetchRecord(recordId)
      setRecord(value)
      await loadHistory(0)
      setError('')
    } catch (requestError) {
      setError(requestError.message)
    }
  }, [loadHistory, recordId])
  useEffect(() => {
    load()
  }, [load])
  const displayedRevision = useMemo(
    () => revisions.find((item) => item.id === record?.displayedRevision?.id) || revisions[0],
    [record, revisions],
  )
  if (error && !record) return <EmptyState title="无法访问记录" description={error} />
  if (!record) return <p className="py-16 text-center text-slate-400">加载记录中…</p>
  const remove = async () => {
    if (confirm('确认软删除该记录？第一阶段不提供恢复入口。')) {
      await deleteRecord(record.id)
      navigate('/records', { replace: true })
    }
  }
  const decide = async (decision) => {
    setDeciding(true)
    setError('')
    try {
      if (decision === 'approve')
        await approveReview(record.id, displayedRevision.review.id, comment)
      else await requestReviewChanges(record.id, displayedRevision.review.id, comment)
      setComment('')
      await load()
    } catch (requestError) {
      setError(requestError.message)
      if (requestError.status === 409) await load()
    } finally {
      setDeciding(false)
    }
  }
  const exportFile = async (type) => {
    try {
      const result =
        type === 'pdf' ? await downloadPdf(record.id) : await downloadMarkdown(record.id)
      saveBlob(result.blob, result.headers, `${record.title}.${type === 'pdf' ? 'pdf' : 'md'}`)
    } catch (e) {
      setError(e.message)
    }
  }
  const openReport = async () => {
    try {
      const result = await fetchExportPreview(record.id)
      setPreviewHtml(result.html)
    } catch (e) {
      setError(e.message)
    }
  }
  const revisionAttachments = ['IN_REVIEW', 'COMPLETED'].includes(record.status)
    ? displayedRevision?.attachments?.map((item) => ({
        ...item,
        originalFilename: item.filename,
        canDelete: false,
      }))
    : undefined
  const updateParams = (updates) => {
    const next = new URLSearchParams(searchParams)
    Object.entries(updates).forEach(([key, value]) =>
      value ? next.set(key, value) : next.delete(key),
    )
    setSearchParams(next, { replace: true })
  }
  const handleAutoSave = async (messages) => {
    if (messages.length === 0 || !record) return
    try {
      if (sessionId) {
        await appendSessionMessages(sessionId, messages.slice(-2))
      } else {
        const title = messages[0]?.content?.slice(0, 50) || '新对话'
        const detail = await saveRecordSession(record.id, { title, messages })
        setSessionId(detail.id)
      }
    } catch (e) {}
  }
  return (
    <section className="space-y-6">
      <button
        type="button"
        aria-label="返回上一级"
        onClick={() => navigate('/records')}
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-900"
      >
        <ArrowLeft size={15} />
        返回记录目录
      </button>
      <PageHeader
        eyebrow={`${record.projectName} · ${record.code}`}
        title={record.title}
        description={`${record.creatorName} 创建 · ${new Date(record.updatedAt).toLocaleString()} 更新`}
        actions={
          <div className="flex flex-wrap gap-2">
            {record.status === 'COMPLETED' && (
              <>
                <Button variant="secondary" icon={Eye} onClick={openReport}>
                  报告预览
                </Button>
                <Button variant="secondary" icon={Download} onClick={() => exportFile('markdown')}>
                  Markdown
                </Button>
                <Button icon={Download} onClick={() => exportFile('pdf')}>
                  PDF
                </Button>
              </>
            )}
            {record.capabilities.canSubmit && (
              <Button icon={Send} onClick={() => setSubmitOpen(true)}>
                提交审核
              </Button>
            )}
            {record.capabilities.canEdit && (
              <Button
                variant={record.capabilities.canSubmit ? 'secondary' : 'primary'}
                icon={Pencil}
                onClick={() => navigate(`/records/${record.id}/edit`)}
              >
                编辑
              </Button>
            )}
            {record.capabilities.canDelete && (
              <Button variant="danger" icon={Trash2} onClick={remove}>
                删除
              </Button>
            )}
          </div>
        }
      />
      {error && (
        <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <StatusBadge kind="record" status={record.status} />
        <Badge>{record.experimentType}</Badge>
        <Badge>{record.experimentDate}</Badge>
        {record.displayedRevision && <Badge>R{record.displayedRevision.revisionNo} 快照</Badge>}
      </div>
      <div className="overflow-x-auto">
        <Tabs
          items={[
            { key: 'current', label: '当前内容' },
            { key: 'history', label: '版本历史' },
            { key: 'summary', label: '记录问答' },
          ]}
          activeKey={tab}
          onChange={(value) => updateParams({ tab: value === 'current' ? '' : value })}
        />
      </div>
      {tab === 'current' && (
        <>
          <Surface title="基本信息">
            <dl className="grid gap-4 md:grid-cols-2">
              <div>
                <dt className="text-xs text-slate-400">实验目的</dt>
                <dd className="mt-1 text-sm">{record.purpose}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-400">模板结构</dt>
                <dd className="mt-1 text-sm">
                  {record.templateSnapshot?.name || '空白记录'}（快照）
                </dd>
              </div>
            </dl>
          </Surface>
          {record.templateSnapshot?.fields?.length > 0 && (
            <Surface title="模板字段">
              <div className="grid gap-3 md:grid-cols-2">
                {record.templateSnapshot.fields.map((field) => (
                  <div key={field.fieldKey} className="rounded-lg border p-3">
                    <div className="flex justify-between text-sm">
                      <span className="font-medium">{field.label}</span>
                      <span className="text-xs text-slate-400">
                        {TEMPLATE_FIELD_TYPE_LABELS[field.fieldType]}
                      </span>
                    </div>
                    <p className="mt-2 whitespace-pre-wrap text-sm text-slate-600">
                      {Array.isArray(record.fieldValues[field.fieldKey])
                        ? record.fieldValues[field.fieldKey].join('、')
                        : String(record.fieldValues[field.fieldKey] ?? '—')}
                    </p>
                  </div>
                ))}
              </div>
            </Surface>
          )}
          <Surface title="自由正文">
            {record.contentHtml ? (
              <div
                className="prose max-w-none"
                dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(record.contentHtml) }}
              />
            ) : (
              <p className="text-sm text-slate-400">暂无正文</p>
            )}
          </Surface>
          <AttachmentManager
            recordId={record.id}
            readOnly={!record.capabilities.canEdit}
            initialItems={revisionAttachments}
          />
          {displayedRevision?.review && (
            <Surface title={`R${displayedRevision.revisionNo} 审核`}>
              <div id="record-review" className="space-y-2 text-sm">
                <p>
                  审核人：<b>{displayedRevision.review.reviewerName}</b>
                </p>
                <p>状态：{displayedRevision.review.status}</p>
                {displayedRevision.review.decisionComment && (
                  <p className="rounded-lg bg-slate-50 p-3">
                    {displayedRevision.review.decisionComment}
                  </p>
                )}
              </div>
              {displayedRevision.review.canDecide && (
                <div className="mt-4">
                  <label className="field-label">审核意见（退回时必填）</label>
                  <textarea
                    aria-label="审核意见"
                    className="input min-h-24"
                    value={comment}
                    onChange={(e) => setComment(e.target.value)}
                  />
                  <div className="mt-3 flex justify-end gap-2">
                    <Button
                      variant="danger"
                      icon={XCircle}
                      loading={deciding}
                      disabled={!comment.trim()}
                      onClick={() => decide('changes')}
                    >
                      退回修改
                    </Button>
                    <Button icon={CheckCircle} loading={deciding} onClick={() => decide('approve')}>
                      审核通过
                    </Button>
                  </div>
                </div>
              )}
            </Surface>
          )}
        </>
      )}
      {tab === 'history' && (
        <Suspense
          fallback={
            <p role="status" className="py-12 text-center text-slate-400">
              加载版本组件中…
            </p>
          }
        >
          <RevisionHistoryPanel
            record={record}
            revisions={revisions}
            meta={revisionMeta}
            loading={historyLoading}
            initialFrom={searchParams.get('from')}
            initialTo={searchParams.get('to')}
            initialRevisionId={searchParams.get('revision')}
            onPageChange={loadHistory}
            onSelectionChange={(from, to) => updateParams({ tab: 'history', from, to })}
            onRestored={(_, revision) =>
              navigate(`/records/${record.id}/edit`, {
                state: {
                  restoreMessage: `已从 R${revision.revisionNo} 恢复工作副本，历史版本未改变。`,
                },
              })
            }
          />
        </Suspense>
      )}
      {tab === 'summary' && (
        <>
          <Suspense
            fallback={
              <p role="status" className="py-12 text-center text-slate-400">
                加载问答组件中…
              </p>
            }
          >
            <RecordSummaryPanel
              key={chatKey}
              record={record}
              onAutoSave={handleAutoSave}
              onNewChat={() => {
                setSessionId(null)
                setChatKey((k) => k + 1)
              }}
            />
          </Suspense>
          <Suspense
            fallback={
              <p role="status" className="py-12 text-center text-slate-400">
                加载总结组件中…
              </p>
            }
          >
            <AutoSummaryPanel subjectType="record" subjectId={record.id} />
          </Suspense>
        </>
      )}
      <SubmissionDialog
        record={record}
        open={submitOpen}
        onClose={() => setSubmitOpen(false)}
        onSubmitted={async () => {
          setSubmitOpen(false)
          await load()
        }}
      />
      {previewHtml && (
        <div
          role="dialog"
          aria-label="报告预览"
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4"
        >
          <div className="flex h-[90vh] w-full max-w-5xl flex-col rounded-xl bg-white/95 p-4 backdrop-blur-sm">
            <div className="mb-3 flex justify-end">
              <Button variant="secondary" onClick={() => setPreviewHtml('')}>
                关闭
              </Button>
            </div>
            <iframe
              title="记录报告预览"
              sandbox=""
              srcDoc={DOMPurify.sanitize(previewHtml, { WHOLE_DOCUMENT: true })}
              className="min-h-0 flex-1 rounded border"
            />
          </div>
        </div>
      )}
    </section>
  )
}
