import { Button, EmptyState } from '@/components/ui'

const STATUS = { ADDED: '新增', REMOVED: '删除', MODIFIED: '修改', UNCHANGED: '未变化' }
const TONE = {
  context: { cell: '', gutter: '', mark: '' },
  del: { cell: 'bg-red-50', gutter: 'bg-red-100', mark: 'bg-red-200/80' },
  add: { cell: 'bg-emerald-50', gutter: 'bg-emerald-100', mark: 'bg-emerald-200/80' },
}

// 行号 gutter 样式对齐 GitHub：统一灰色底、右侧分隔线、等宽数字；变更行 gutter 加深底色
const gutterClass = (side) => `select-none border-r border-slate-200 px-2 text-right font-mono text-xs text-slate-500 ${side && TONE[side.tone].gutter ? TONE[side.tone].gutter : 'bg-slate-50'}`

function Value({ value }) {
  if (value === null || value === undefined || value === '') return <span className="text-slate-400">—</span>
  if (typeof value === 'string') return <span className="whitespace-pre-wrap break-words">{value}</span>
  return <pre className="whitespace-pre-wrap break-words text-xs">{JSON.stringify(value, null, 2)}</pre>
}

const sourceLabel = (ref) => {
  if (!ref) return '—'
  if (ref.type === 'WORKING_COPY') return '当前工作副本'
  if (ref.revisionNo !== null && ref.revisionNo !== undefined) return `R${ref.revisionNo}`
  return '历史版本'
}

// 将片段流按换行拆成行，保留“是否变更”标记，供双栏逐行高亮
const toLines = (segments) => {
  const lines = [[]]
  segments.forEach((segment) => {
    String(segment.text ?? '').split('\n').forEach((part, index) => {
      if (index > 0) lines.push([])
      if (part) lines[lines.length - 1].push({ text: part, changed: segment.changed })
    })
  })
  return lines
}

const plainLines = (text, changed) => toLines([{ text, changed }])

// 从词级 hunk 还原某一侧的行：基准侧保留 EQUAL+DELETE，目标侧保留 EQUAL+INSERT
const hunkLines = (hunks, side) => toLines((hunks || [])
  .filter((hunk) => hunk.operation === 'EQUAL' || (side === 'before' ? hunk.operation === 'DELETE' : hunk.operation === 'INSERT'))
  .map((hunk) => ({ text: hunk.text, changed: hunk.operation !== 'EQUAL' })))

function buildBodyRows(sections) {
  const rows = []
  sections.forEach((section) => {
    if (section.status === 'ADDED') {
      plainLines(section.after, true).forEach((line) => rows.push({ left: null, right: { line, tone: 'add' } }))
    } else if (section.status === 'REMOVED') {
      plainLines(section.before, true).forEach((line) => rows.push({ left: { line, tone: 'del' }, right: null }))
    } else if (section.status === 'MODIFIED') {
      const leftLines = section.textHunks?.length ? hunkLines(section.textHunks, 'before') : plainLines(section.before, true)
      const rightLines = section.textHunks?.length ? hunkLines(section.textHunks, 'after') : plainLines(section.after, true)
      const count = Math.max(leftLines.length, rightLines.length)
      for (let index = 0; index < count; index += 1) {
        rows.push({
          left: leftLines[index] ? { line: leftLines[index], tone: 'del' } : null,
          right: rightLines[index] ? { line: rightLines[index], tone: 'add' } : null,
        })
      }
    } else {
      plainLines(section.before ?? section.after, false).forEach((line) => rows.push({ left: { line, tone: 'context' }, right: { line, tone: 'context' } }))
    }
  })
  return rows
}

function DiffLine({ line, tone }) {
  if (!line.length) return ' '
  return line.map((segment, index) => (segment.changed
    ? <mark key={index} className={`rounded-sm px-0.5 ${TONE[tone].mark}`}>{segment.text}</mark>
    : <span key={index}>{segment.text}</span>))
}

function SideBySideTable({ sections, from, to, ariaLabel }) {
  const rows = buildBodyRows(sections)
  let leftNo = 0
  let rightNo = 0
  return <table aria-label={ariaLabel} className="w-full table-fixed border-collapse text-sm leading-6">
    <colgroup><col className="w-11" /><col /><col className="w-11" /><col /></colgroup>
    <thead><tr className="border-b border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
      <th colSpan={2} className="border-r border-slate-200 px-3 py-2 font-medium">基准：{sourceLabel(from)}</th>
      <th colSpan={2} className="px-3 py-2 font-medium">目标：{sourceLabel(to)}</th>
    </tr></thead>
    <tbody>{rows.map((row, rowIndex) => {
      const leftNumber = row.left ? (leftNo += 1) : null
      const rightNumber = row.right ? (rightNo += 1) : null
      return <tr key={rowIndex} className="align-top">
        <td aria-hidden="true" className={gutterClass(row.left)}>{leftNumber ?? ''}</td>
        <td className={`whitespace-pre-wrap break-words border-r border-slate-200 px-3 ${row.left ? TONE[row.left.tone].cell : 'bg-slate-50'}`}>{row.left ? <DiffLine line={row.left.line} tone={row.left.tone} /> : ''}</td>
        <td aria-hidden="true" className={gutterClass(row.right)}>{rightNumber ?? ''}</td>
        <td className={`whitespace-pre-wrap break-words px-3 ${row.right ? TONE[row.right.tone].cell : 'bg-slate-50'}`}>{row.right ? <DiffLine line={row.right.line} tone={row.right.tone} /> : ''}</td>
      </tr>
    })}</tbody>
  </table>
}

function BodySideBySide({ sections, from, to }) {
  return <article className="overflow-hidden rounded-xl border border-slate-200">
    <header className="border-b border-slate-200 px-4 py-3"><h3 className="font-medium">正文</h3><p className="text-xs text-slate-400">RICH_TEXT · 双栏对比，左侧为基准版本，右侧为目标版本</p></header>
    <div className="overflow-x-auto"><SideBySideTable sections={sections} from={from} to={to} ariaLabel="正文版本对比" /></div>
  </article>
}

function SectionCard({ section, from, to }) {
  // 多行文本（实验目的、多行模板字段）带词级 hunk，直接用双栏展示，避免“修改前/修改后 + hunk”重复一遍内容
  const hasHunks = Boolean(section.textHunks?.length)
  return <article className="rounded-xl border border-slate-200 p-4">
    <div className="flex flex-wrap items-center justify-between gap-2"><div><h3 className="font-medium">{section.label}</h3><p className="text-xs text-slate-400">{section.kind}{section.valueType ? ` · ${section.valueType}` : ''}</p></div><span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium">{STATUS[section.status] || section.status}</span></div>
    {hasHunks ? <div className="mt-4 overflow-hidden overflow-x-auto rounded-lg border border-slate-200"><SideBySideTable sections={[section]} from={from} to={to} ariaLabel={`${section.label}对比`} /></div>
      : <div className="mt-4 grid gap-3 lg:grid-cols-2">
        <div className="rounded-lg border border-red-100 bg-red-50/50 p-3"><div className="mb-2 text-xs font-semibold text-red-700"><span aria-label="删除或旧值">−</span> 修改前</div><Value value={section.before} /></div>
        <div className="rounded-lg border border-emerald-100 bg-emerald-50/50 p-3"><div className="mb-2 text-xs font-semibold text-emerald-700"><span aria-label="新增或新值">+</span> 修改后</div><Value value={section.after} /></div>
      </div>}
  </article>
}

// 版本对比只展示实验内容差异：附件与审核元数据区段不渲染；正文双栏对比保持在后端区段顺序中的原始位置（固定字段/模板字段之后）
function groupSections(sections) {
  const groups = []
  ;(sections || []).forEach((section) => {
    if (section.kind === 'ATTACHMENT_SET' || section.kind === 'REVIEW_METADATA') return
    if (section.kind === 'RICH_TEXT') {
      const last = groups[groups.length - 1]
      if (last?.body) last.body.push(section)
      else groups.push({ key: `body:${section.key}`, body: [section] })
    } else if (section.status !== 'UNCHANGED') groups.push({ key: section.key, section })
  })
  return groups.filter((group) => group.section || group.body.some((section) => section.status !== 'UNCHANGED'))
}

export default function RevisionDiffViewer({ diff, loading, error, onRetry }) {
  if (loading) return <p role="status" aria-live="polite" className="py-10 text-center text-sm text-slate-400">正在计算结构化差异…</p>
  if (error) return <EmptyState title="无法加载差异" description={error} action={<Button variant="secondary" onClick={onRetry}>重试</Button>} />
  if (!diff) return <EmptyState title="请选择两个不同的版本进行比较" />
  const groups = groupSections(diff.sections)
  return <div className="space-y-4">
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{[
      ['新增', diff.summary?.added], ['删除', diff.summary?.removed], ['修改', diff.summary?.modified], ['未变化', diff.summary?.unchanged],
    ].map(([label, value]) => <div key={label} className="rounded-lg border bg-slate-50 p-3"><div className="text-xs text-slate-500">{label}</div><div className="mt-1 text-xl font-semibold">{value ?? 0}</div></div>)}</div>
    <p className="text-xs text-slate-400">生成于 {new Date(diff.generatedAt).toLocaleString()}</p>
    {diff.truncated && <p role="alert" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">内容较大，差异结果已截断。请以具体版本详情为准。</p>}
    {diff.warnings?.map((warning) => <p key={warning} className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">{warning}</p>)}
    {!groups.length ? <EmptyState title="两个来源没有领域差异" /> : groups.map((group) => (group.body
      ? <BodySideBySide key={group.key} sections={group.body} from={diff.from} to={diff.to} />
      : <SectionCard key={group.key} section={group.section} from={diff.from} to={diff.to} />))}
  </div>
}
