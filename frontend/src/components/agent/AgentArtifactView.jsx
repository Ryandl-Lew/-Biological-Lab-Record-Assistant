import { Badge, EmptyState, Surface } from '@/components/ui'
import EvidenceLink from './EvidenceLink'

function Section({ title, items, evidence, onEvidence, renderMeta }) {
  if (!items?.length) return null
  const byRef = new Map((evidence || []).map((item) => [item.ref, item]))
  return <Surface title={title}><ul className="space-y-3">{items.map((item) => <li key={item.id || item.statement} className="rounded-lg border p-4 text-sm">
    <div className="flex flex-wrap items-start justify-between gap-2"><p className="whitespace-pre-wrap leading-6">{item.statement}</p>{renderMeta?.(item)}</div>
    {item.evidenceRefs?.length > 0 && <div className="mt-3 flex flex-wrap gap-2">{item.evidenceRefs.map((ref) => byRef.get(ref) ? <EvidenceLink key={ref} evidence={byRef.get(ref)} onNavigate={onEvidence} /> : <span key={ref} className="text-xs text-red-600">证据 {ref} 不可用</span>)}</div>}
  </li>)}</ul></Surface>
}

export default function AgentArtifactView({ artifact, run, onEvidence }) {
  if (!artifact) return <EmptyState title="尚无已验证报告" description="生成成功后，报告和可跳转证据会显示在这里。" />
  const content = artifact.content || {}
  return <div className="space-y-5">
    <Surface title={content.headline || 'Agent 报告'} extra={<div className="flex flex-wrap gap-2"><Badge>{artifact.artifactKind === 'RECORD_SUMMARY' ? '记录总结' : artifact.artifactKind === 'PROJECT_PROGRESS' ? '项目进展' : artifact.artifactKind}</Badge>{run && <Badge>{run.provider} / {run.model}</Badge>}</div>}>
      <p className="whitespace-pre-wrap text-sm leading-7 text-slate-700">{content.executiveSummary}</p>
      <div className="mt-4 flex flex-wrap gap-x-5 gap-y-1 text-xs text-slate-400"><span>生成于 {new Date(artifact.createdAt).toLocaleString()}</span><span>报告范围 {content.period?.start} 至 {content.period?.end}</span></div>
    </Surface>
    <Section title="进展与版本演进" items={content.progress} evidence={content.evidence} onEvidence={onEvidence} />
    <Section title="风险与阻塞" items={content.risks} evidence={content.evidence} onEvidence={onEvidence} renderMeta={(item) => <Badge tone={item.severity === 'HIGH' ? 'red' : item.severity === 'MEDIUM' ? 'amber' : 'gray'}>{item.severity === 'HIGH' ? '高' : item.severity === 'MEDIUM' ? '中' : '低'}</Badge>} />
    <Section title="下一步" items={content.nextActions} evidence={content.evidence} onEvidence={onEvidence} renderMeta={(item) => <Badge tone={item.basis === 'REVIEW_FEEDBACK' ? 'amber' : 'blue'}>{item.basis === 'REVIEW_FEEDBACK' ? '审核反馈' : 'AI 建议'}</Badge>} />
    {content.limitations?.length > 0 && <Surface title="局限性"><ul className="list-disc space-y-2 pl-5 text-sm text-slate-600">{content.limitations.map((item) => <li key={item}>{item}</li>)}</ul></Surface>}
    {content.evidence?.length > 0 && <Surface title="全部证据"><div className="flex flex-wrap gap-2">{content.evidence.map((item) => <EvidenceLink key={item.ref} evidence={item} onNavigate={onEvidence} />)}</div></Surface>}
  </div>
}
