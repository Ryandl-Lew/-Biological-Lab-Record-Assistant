import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { AlertTriangle, ArrowLeft, Clock3, Save, Send, Trash2 } from 'lucide-react'
import { Button, Surface } from '@/components/ui'
import {
  discardRecordReservation,
  fetchAttachments,
  fetchRecord,
  fetchRevisionSummaries,
  updateRecord,
} from '@/api'
import RichTextEditor from '@/components/record/RichTextEditor'
import AttachmentManager from '@/components/record/AttachmentManager'
import SubmissionDialog from '@/components/record/SubmissionDialog'

const today = () => new Date().toISOString().slice(0, 10)
const empty = {
  title: '',
  experimentType: '',
  experimentDate: today(),
  purpose: '',
  fieldValues: {},
  contentJson: {},
  contentHtml: '',
}

function TemplateField({ field, value, onChange, attachments = [] }) {
  const common = {
    value: value ?? '',
    onChange: (event) => onChange(event.target.value),
    className: 'input',
  }
  if (field.fieldType === 'MULTI_LINE_TEXT')
    return <textarea {...common} className="input min-h-28" placeholder={field.placeholder || ''} />
  if (field.fieldType === 'NUMBER')
    return <input {...common} type="number" placeholder={field.placeholder || ''} />
  if (field.fieldType === 'DATE') return <input {...common} type="date" />
  if (field.fieldType === 'SELECT')
    return (
      <select {...common}>
        <option value="">请选择</option>
        {field.options?.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    )
  if (field.fieldType === 'FILE')
    return (
      <select
        aria-label={field.label}
        multiple
        className="input min-h-28"
        value={Array.isArray(value) ? value : value ? [value] : []}
        onChange={(event) =>
          onChange(Array.from(event.target.selectedOptions, (option) => option.value))
        }
      >
        {attachments.map((item) => (
          <option key={item.id} value={item.id}>
            {item.originalFilename}
          </option>
        ))}
      </select>
    )
  return <input {...common} placeholder={field.placeholder || ''} />
}

export default function RecordEditorMvpPage() {
  const { recordId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const [record, setRecord] = useState(null),
    [project, setProject] = useState(null),
    [template, setTemplate] = useState(null)
  const [form, setForm] = useState(empty),
    [dirty, setDirty] = useState(false),
    [state, setState] = useState('未保存'),
    [error, setError] = useState(''),
    [conflict, setConflict] = useState(false)
  const [attachments, setAttachments] = useState([]),
    [revisions, setRevisions] = useState([]),
    [submitOpen, setSubmitOpen] = useState(false)
  const [lastSavedAt, setLastSavedAt] = useState(null)
  const savingRef = useRef(false),
    editSequenceRef = useRef(0),
    autoSaveRef = useRef(null)

  const hydrate = useCallback((value) => {
    setRecord(value)
    setTemplate(value.templateSnapshot || null)
    setForm({
      title: value.provisional && value.title === '未命名记录' ? '' : value.title,
      experimentType:
        value.provisional && value.experimentType === '未分类' ? '' : value.experimentType,
      experimentDate: value.experimentDate,
      purpose: value.purpose || '',
      fieldValues: value.fieldValues || {},
      contentJson: value.contentJson || {},
      contentHtml: value.contentHtml || '',
    })
    setDirty(false)
    setState(value.provisional ? '首次保存前' : '已保存')
    if (!value.provisional) setLastSavedAt(new Date())
  }, [])
  const loadRelated = useCallback(async (id) => {
    const [files, history] = await Promise.all([fetchAttachments(id), fetchRevisionSummaries(id)])
    setAttachments(files)
    setRevisions(history.items)
  }, [])
  const loadExisting = useCallback(async () => {
    try {
      const value = await fetchRecord(recordId)
      hydrate(value)
      setProject({ id: value.projectId, name: value.projectName })
      await loadRelated(recordId)
    } catch (requestError) {
      setError(requestError.message)
    }
  }, [hydrate, loadRelated, recordId])
  useEffect(() => {
    if (recordId) loadExisting()
    else setError('缺少记录编号，请返回创建流程重新选择项目与模板')
  }, [loadExisting, recordId])
  useEffect(() => {
    const before = (event) => {
      if (dirty) {
        event.preventDefault()
        event.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', before)
    return () => window.removeEventListener('beforeunload', before)
  }, [dirty])

  useEffect(() => {
    const handleKeyDown = (event) => {
      if ((event.ctrlKey || event.metaKey) && event.key === 's') {
        event.preventDefault()
        save(event)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  })

  const markChanged = () => {
    editSequenceRef.current += 1
    setDirty(true)
    setState('未保存')
  }
  const change = (key, value) => {
    setForm((current) => ({ ...current, [key]: value }))
    markChanged()
  }
  const fields = record?.templateSnapshot?.fields || template?.fields || []
  const formComplete = Boolean(
    form.title.trim() &&
    form.experimentType.trim() &&
    form.experimentDate &&
    form.purpose.trim() &&
    fields
      .filter((field) => field.required)
      .every((field) => {
        const value = form.fieldValues[field.fieldKey]
        return Array.isArray(value) ? value.length > 0 : String(value ?? '').trim()
      }),
  )
  const save = async (event, automatic = false) => {
    event?.preventDefault()
    if (!record || savingRef.current || !dirty || !formComplete) return
    const savedSequence = editSequenceRef.current
    const wasProvisional = record.provisional
    savingRef.current = true
    setError('')
    setState(automatic ? '自动保存中' : '保存中')
    try {
      const updated = await updateRecord(record.id, { ...form, version: record.version })
      setRecord(updated)
      setLastSavedAt(new Date())
      if (editSequenceRef.current === savedSequence) {
        hydrate(updated)
      } else {
        setDirty(true)
        setState('有新修改未保存')
      }
      if (wasProvisional) navigate(`/records/${record.id}/edit`, { replace: true })
    } catch (requestError) {
      if (requestError.code === 'OPTIMISTIC_LOCK_CONFLICT') {
        setConflict(true)
        setState('版本冲突')
      } else {
        setError(requestError.message)
        setState('保存失败')
      }
    } finally {
      savingRef.current = false
    }
  }
  autoSaveRef.current = () => {
    if (record && !record.provisional && dirty && formComplete) save(null, true)
  }
  useEffect(() => {
    const timer = window.setInterval(() => autoSaveRef.current?.(), 30000)
    return () => window.clearInterval(timer)
  }, [])

  const cancel = async () => {
    if (!record) {
      navigate('/records')
      return
    }
    if (record.provisional) {
      if (!confirm('舍弃本次新记录？已上传的临时附件也会一并删除。')) return
      try {
        await discardRecordReservation(record.id)
        navigate('/records/new', { replace: true })
      } catch (requestError) {
        setError(requestError.message)
      }
      return
    }
    if (dirty && !confirm('舍弃本次尚未保存的编辑？')) return
    navigate(`/records/${record.id}`)
  }
  const latestReview = revisions[0]?.review
  const saving = state.includes('保存中')
  if (!record && !error)
    return <p className="py-16 text-center text-sm text-slate-400">加载编辑器中…</p>
  if (!record)
    return (
      <Surface title="无法打开编辑器">
        <p className="text-sm text-red-600">{error}</p>
        <Button className="mt-4" onClick={() => navigate('/records/new')}>
          返回创建流程
        </Button>
      </Surface>
    )
  if (record && !record.capabilities.canEdit)
    return (
      <Surface title="记录不可编辑">
        <p className="text-sm text-slate-600">你不是创建者，或记录已进入只读状态。</p>
        <Button className="mt-4" onClick={() => navigate(`/records/${record.id}`)}>
          返回详情
        </Button>
      </Surface>
    )
  return (
    <section className="space-y-5">
      <button
        type="button"
        onClick={cancel}
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-900"
      >
        <ArrowLeft size={15} />
        {record.provisional ? '返回创建流程' : '返回记录详情'}
      </button>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-brand-600">
            实验记录编辑器
          </p>
          <h1 className="mt-1 text-2xl font-bold">
            {record.provisional ? '填写新记录' : record.title}
          </h1>
          <p className="mt-2 text-sm text-slate-500">
            {project?.name} · {record.code} · {record.templateSnapshot?.name || '空白结构'}
          </p>
        </div>
        <span
          className={`rounded-full px-3 py-1 text-xs font-medium ${state === '保存失败' || state === '版本冲突' ? 'bg-red-50 text-red-700' : dirty ? 'bg-amber-50 text-amber-700' : 'bg-emerald-50 text-emerald-700'}`}
        >
          {state}
        </span>
      </div>
      {location.state?.restoreMessage && (
        <p
          role="status"
          className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800"
        >
          {location.state.restoreMessage}
        </p>
      )}
      {error && (
        <p role="alert" className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}
      {record?.status === 'CHANGES_REQUESTED' && latestReview && (
        <Surface
          title={`R${revisions[0].revisionNo} 审核意见`}
          className="mb-6 border-amber-200 bg-amber-50"
        >
          <p className="text-sm text-amber-900">{latestReview.decisionComment}</p>
        </Surface>
      )}
      <form onSubmit={save} className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr),340px]">
        <div className="space-y-6">
          <Surface title="基本信息">
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label className="field-label">
                  实验名称<span className="text-red-500"> *</span>
                </label>
                <input
                  aria-label="实验名称"
                  value={form.title}
                  onChange={(e) => change('title', e.target.value)}
                  maxLength={255}
                  required
                  className="input"
                />
              </div>
              <div>
                <label className="field-label">
                  实验类型<span className="text-red-500"> *</span>
                </label>
                <input
                  aria-label="实验类型"
                  value={form.experimentType}
                  onChange={(e) => change('experimentType', e.target.value)}
                  maxLength={100}
                  required
                  className="input"
                />
              </div>
              <div>
                <label className="field-label">
                  实验日期<span className="text-red-500"> *</span>
                </label>
                <input
                  aria-label="实验日期"
                  type="date"
                  value={form.experimentDate}
                  onChange={(e) => change('experimentDate', e.target.value)}
                  required
                  className="input"
                />
              </div>
              <div>
                <label className="field-label">所属项目</label>
                <input value={project?.name || ''} readOnly className="input bg-slate-50" />
              </div>
              <div className="md:col-span-2">
                <label className="field-label">
                  实验目的<span className="text-red-500"> *</span>
                </label>
                <textarea
                  aria-label="实验目的"
                  value={form.purpose}
                  onChange={(e) => change('purpose', e.target.value)}
                  maxLength={3000}
                  required
                  className="input min-h-28"
                />
              </div>
            </div>
          </Surface>
          {fields.length > 0 && (
            <Surface title="模板字段">
              <div className="grid gap-4 md:grid-cols-2">
                {fields.map((field) => (
                  <div
                    key={field.fieldKey}
                    className={field.fieldType === 'MULTI_LINE_TEXT' ? 'md:col-span-2' : ''}
                  >
                    <label className="field-label">
                      {field.label}
                      {field.required && <span className="text-red-500"> *</span>}
                    </label>
                    <TemplateField
                      field={field}
                      value={form.fieldValues[field.fieldKey]}
                      attachments={attachments}
                      onChange={(value) =>
                        change('fieldValues', { ...form.fieldValues, [field.fieldKey]: value })
                      }
                    />
                  </div>
                ))}
              </div>
            </Surface>
          )}
          <Surface title="记录正文">
            <RichTextEditor
              html={form.contentHtml}
              onChange={(json, html) => {
                setForm((current) => ({ ...current, contentJson: json, contentHtml: html }))
                markChanged()
              }}
            />
          </Surface>
        </div>
        <aside className="space-y-4 xl:sticky xl:top-20">
          <Surface title="保存与审核">
            <div className="rounded-lg bg-slate-50 p-3">
              <p className="flex items-center gap-2 text-sm font-medium">
                <Clock3 size={15} className="text-slate-400" />
                {record.provisional ? '首次保存前不自动保存' : '每 30 秒自动保存'}
              </p>
              <p className="mt-1 text-xs text-slate-400">
                {lastSavedAt ? `最近保存：${lastSavedAt.toLocaleTimeString()}` : '尚未保存'}
              </p>
            </div>
            {!formComplete && dirty && (
              <p className="mt-3 text-xs text-amber-700">请填写所有必填字段后保存。</p>
            )}
            <div className="mt-4 grid gap-2">
              <Button type="submit" icon={Save} loading={saving} disabled={!dirty || !formComplete}>
                保存
              </Button>
              <Button
                type="button"
                variant="secondary"
                icon={record.provisional ? Trash2 : ArrowLeft}
                disabled={saving}
                onClick={cancel}
              >
                {record.provisional ? '舍弃本次记录' : '取消本次编辑'}
              </Button>
              <Button
                type="button"
                variant="secondary"
                icon={Send}
                disabled={record.provisional || dirty || saving || !record.capabilities.canSubmit}
                onClick={() => setSubmitOpen(true)}
              >
                提交审核
              </Button>
            </div>
          </Surface>
          <AttachmentManager compact recordId={record.id} onChange={() => loadRelated(record.id)} />
        </aside>
      </form>
      {conflict && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white/95 p-6 backdrop-blur-sm">
            <AlertTriangle className="text-amber-500" />
            <h2 className="mt-3 text-lg font-semibold">检测到版本冲突</h2>
            <p className="mt-2 text-sm text-slate-600">
              另一页面已经保存了更新。当前本地内容会保留在页面中供复制。
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setConflict(false)}>
                保留本地内容
              </Button>
              <Button
                onClick={async () => {
                  setConflict(false)
                  await loadExisting()
                }}
              >
                重新加载最新内容
              </Button>
            </div>
          </div>
        </div>
      )}
      {record && (
        <SubmissionDialog
          record={record}
          open={submitOpen}
          onClose={() => setSubmitOpen(false)}
          onSubmitted={() => navigate(`/records/${record.id}`, { replace: true })}
        />
      )}
    </section>
  )
}
