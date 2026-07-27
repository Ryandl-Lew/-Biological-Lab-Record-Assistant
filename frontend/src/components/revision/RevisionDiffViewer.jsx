import { Button, EmptyState } from '@/components/ui'

const STATUS = { ADDED: '新增', REMOVED: '删除', MODIFIED: '修改', UNCHANGED: '未变化' }

function Value({ value }) {
  if (value === null || value === undefined || value === '') return <span className="text-slate-400">—</span>
  if (typeof value === 'string') return <span className="whitespace-pre-wrap break-words">{value}</span>
  return <pre className="whitespace-pre-wrap break-words text-xs">{JSON.stringify(value, null, 2)}</pre>
}

function TextHunks({ hunks = [] }) {
  if (!hunks.length) return null
  return <div className="mt-3 space-y-1 rounded-lg bg-slate-950 p-3 font-mono text-xs text-slate-100">{hunks.map((hunk, index) => {
    const marker = hunk.operation === 'INSERT' ? '+' : hunk.operation === 'DELETE' ? '−' : ' '
    return <div key={`${hunk.operation}-${index}`} className={hunk.operation === 'INSERT' ? 'text-emerald-300' : hunk.operation === 'DELETE' ? 'text-red-300' : 'text-slate-400'}>
      <span className="mr-2 select-none" aria-label={hunk.operation === 'INSERT' ? '新增文本' : hunk.operation === 'DELETE' ? '删除文本' : '未变化文本'}>{marker}</span>
      <span>{hunk.text}</span>
    </div>
  })}</div>
}

export default function RevisionDiffViewer({ diff, loading, error, onRetry }) {
  if (loading) return <p role="status" aria-live="polite" className="py-10 text-center text-sm text-slate-400">正在计算结构化差异…</p>
  if (error) return <EmptyState title="无法加载差异" description={error} action={<Button variant="secondary" onClick={onRetry}>重试</Button>} />
  if (!diff) return <EmptyState title="请选择两个不同的版本进行比较" />
  const changed = diff.sections?.filter((section) => section.status !== 'UNCHANGED') || []
  return <div className="space-y-4">
    <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">{[
      ['新增', diff.summary?.added], ['删除', diff.summary?.removed], ['修改', diff.summary?.modified],
      ['未变化', diff.summary?.unchanged], ['附件新增', diff.summary?.attachmentAdded], ['附件删除', diff.summary?.attachmentRemoved],
    ].map(([label, value]) => <div key={label} className="rounded-lg border bg-slate-50 p-3"><div className="text-xs text-slate-500">{label}</div><div className="mt-1 text-xl font-semibold">{value ?? 0}</div></div>)}</div>
    <p className="text-xs text-slate-400">生成于 {new Date(diff.generatedAt).toLocaleString()}</p>
    {diff.truncated && <p role="alert" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">内容较大，差异结果已截断。请以具体版本详情为准。</p>}
    {diff.warnings?.map((warning) => <p key={warning} className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">{warning}</p>)}
    {!changed.length ? <EmptyState title="两个来源没有领域差异" /> : changed.map((section) => <article key={section.key} className="rounded-xl border border-slate-200 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2"><div><h3 className="font-medium">{section.label}</h3><p className="text-xs text-slate-400">{section.kind}{section.valueType ? ` · ${section.valueType}` : ''}</p></div><span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium">{STATUS[section.status] || section.status}</span></div>
      <div className="mt-4 grid gap-3 lg:grid-cols-2">
        <div className="rounded-lg border border-red-100 bg-red-50/50 p-3"><div className="mb-2 text-xs font-semibold text-red-700"><span aria-label="删除或旧值">−</span> 修改前</div><Value value={section.before} /></div>
        <div className="rounded-lg border border-emerald-100 bg-emerald-50/50 p-3"><div className="mb-2 text-xs font-semibold text-emerald-700"><span aria-label="新增或新值">+</span> 修改后</div><Value value={section.after} /></div>
      </div>
      <TextHunks hunks={section.textHunks} />
      {section.kind === 'REVIEW_METADATA' && <p className="mt-3 text-xs text-slate-500">该部分仅表示提交与审核元数据变化，不计入实验内容结论。</p>}
    </article>)}
  </div>
}
