import { describe, expect, it } from 'vitest'
import { buildTimelineGraph, graphNodeLabel, graphNodeContext } from './timelineGraph'

function ev(id, type, createdAt, overrides = {}) {
  return { id, eventType: type, actorId: overrides.actorId || 'u1', actorName: '张三', metadata: overrides.metadata || {}, recordId: overrides.recordId || null, createdAt }
}

describe('buildTimelineGraph', () => {
  it('creates a trunk-only graph for project-level events without records', () => {
    const graph = buildTimelineGraph([
      ev('e1', 'PROJECT_CREATED', '2026-07-01T00:00:00Z'),
      ev('e2', 'INVITATION_CREATED', '2026-07-02T00:00:00Z', { metadata: { email: 'a@b.com' } }),
      ev('e3', 'MEMBER_ROLE_CHANGED', '2026-07-03T00:00:00Z'),
    ])
    expect(graph.totalRows).toBe(3)
    expect(graph.lanes).toHaveLength(1)
    expect(graph.lanes[0].index).toBe(0)
    expect(graph.nodes.every((n) => n.lane === 0)).toBe(true)
    expect(graph.nodes.every((n) => n.type === 'trunk')).toBe(true)
    expect(graph.forks).toHaveLength(0)
    expect(graph.merges).toHaveLength(0)
  })

  it('forks a branch when a record is created and merges it on approval', () => {
    const graph = buildTimelineGraph([
      ev('e1', 'PROJECT_CREATED', '2026-07-01T00:00:00Z'),
      ev('e2', 'RECORD_CREATED', '2026-07-02T00:00:00Z', { recordId: 'r1' }),
      ev('e3', 'RECORD_SUBMITTED', '2026-07-03T00:00:00Z', { recordId: 'r1' }),
      ev('e4', 'REVIEW_APPROVED', '2026-07-04T00:00:00Z', { recordId: 'r1' }),
    ])
    expect(graph.totalRows).toBe(4)
    const forkNode = graph.nodes.find((n) => n.type === 'fork')
    expect(forkNode).toBeTruthy()
    expect(forkNode.lane).toBe(0)
    expect(forkNode.recordId).toBe('r1')
    const commitNode = graph.nodes.find((n) => n.type === 'commit')
    expect(commitNode).toBeTruthy()
    expect(commitNode.lane).toBe(1)
    expect(commitNode.recordId).toBe('r1')
    const mergeNode = graph.nodes.find((n) => n.type === 'merge')
    expect(mergeNode).toBeTruthy()
    expect(mergeNode.lane).toBe(0)
    expect(graph.lanes).toHaveLength(2)
    expect(graph.forks).toHaveLength(1)
    expect(graph.forks[0].from.id).toBe('e2')
    expect(graph.forks[0].to.id).toBe('e3')
    expect(graph.merges).toHaveLength(1)
    expect(graph.merges[0].from.id).toBe('e3')
    expect(graph.merges[0].to.id).toBe('e4')
  })

  it('terminates a branch with ✕ when a record is deleted', () => {
    const graph = buildTimelineGraph([
      ev('e1', 'RECORD_CREATED', '2026-07-02T00:00:00Z', { recordId: 'r1' }),
      ev('e2', 'ATTACHMENT_UPLOADED', '2026-07-03T00:00:00Z', { recordId: 'r1' }),
      ev('e3', 'RECORD_DELETED', '2026-07-04T00:00:00Z', { recordId: 'r1' }),
    ])
    const terminusNode = graph.nodes.find((n) => n.type === 'terminus')
    expect(terminusNode).toBeTruthy()
    expect(terminusNode.lane).toBe(0)
    expect(graph.merges).toHaveLength(1)
    expect(graph.merges[0].from.id).toBe('e2')
    expect(graph.merges[0].to.id).toBe('e3')
    // Open branch lanes that terminated should not extend
    const lane1 = graph.lanes.find((l) => l.index === 1)
    expect(lane1.isOpen).toBe(false)
    expect(lane1.segments).toHaveLength(1)
    expect(lane1.segments[0].toRow).toBe(graph.nodes.find((n) => n.id === 'e2').row)
  })

  it('puts post-merge events on trunk when branch is already closed', () => {
    const graph = buildTimelineGraph([
      ev('e1', 'RECORD_CREATED', '2026-07-02T00:00:00Z', { recordId: 'r1' }),
      ev('e2', 'RECORD_SUBMITTED', '2026-07-03T00:00:00Z', { recordId: 'r1' }),
      ev('e3', 'REVIEW_APPROVED', '2026-07-04T00:00:00Z', { recordId: 'r1' }),
      ev('e4', 'RECORD_EXPORT_PDF', '2026-07-05T00:00:00Z', { recordId: 'r1' }),
    ])
    const exportNode = graph.nodes.find((n) => n.id === 'e4')
    expect(exportNode).toBeTruthy()
    expect(exportNode.lane).toBe(0)
    expect(exportNode.type).toBe('trunk')
  })

  it('handles multiple concurrent records with parallel branches', () => {
    const graph = buildTimelineGraph([
      ev('e1', 'RECORD_CREATED', '2026-07-02T00:00:00Z', { recordId: 'r1' }),
      ev('e2', 'RECORD_CREATED', '2026-07-03T00:00:00Z', { recordId: 'r2' }),
      ev('e3', 'RECORD_SUBMITTED', '2026-07-04T00:00:00Z', { recordId: 'r1' }),
      ev('e4', 'REVIEW_APPROVED', '2026-07-05T00:00:00Z', { recordId: 'r1' }),
      ev('e5', 'RECORD_SUBMITTED', '2026-07-06T00:00:00Z', { recordId: 'r2' }),
      ev('e6', 'RECORD_CREATED', '2026-07-07T00:00:00Z', { recordId: 'r3' }),
    ])
    // r1 → lane 1, r2 → lane 2, r3 → lane 3 (each record gets its own permanent lane)
    const lanes = graph.nodes
      .filter((n) => n.type === 'commit' || n.type === 'fork')
      .map((n) => n.lane)
    expect(new Set(lanes).size).toBeGreaterThanOrEqual(3)
  })

  it('aggregates consecutive same-type same-actor events into a single node', () => {
    const graph = buildTimelineGraph([
      ev('e1', 'RECORD_CREATED', '2026-07-02T00:00:00Z', { recordId: 'r1' }),
      ev('e2', 'ATTACHMENT_UPLOADED', '2026-07-03T00:00:00Z', { recordId: 'r1' }),
      ev('e3', 'ATTACHMENT_UPLOADED', '2026-07-04T00:00:00Z', { recordId: 'r1' }),
      ev('e4', 'ATTACHMENT_UPLOADED', '2026-07-05T00:00:00Z', { recordId: 'r1' }),
    ])
    const commitNodes = graph.nodes.filter((n) => n.type === 'commit')
    expect(commitNodes).toHaveLength(1)
    expect(commitNodes[0].events).toHaveLength(3)
  })

  it('does not aggregate events of different types on the same branch', () => {
    const graph = buildTimelineGraph([
      ev('e1', 'RECORD_CREATED', '2026-07-02T00:00:00Z', { recordId: 'r1' }),
      ev('e2', 'ATTACHMENT_UPLOADED', '2026-07-03T00:00:00Z', { recordId: 'r1' }),
      ev('e3', 'RECORD_SUBMITTED', '2026-07-04T00:00:00Z', { recordId: 'r1' }),
    ])
    expect(graph.nodes.filter((n) => n.type === 'commit')).toHaveLength(2)
  })

  it('assigns lane colors distinct from trunk', () => {
    const graph = buildTimelineGraph([
      ev('e1', 'RECORD_CREATED', '2026-07-02T00:00:00Z', { recordId: 'r1' }),
      ev('e2', 'RECORD_SUBMITTED', '2026-07-03T00:00:00Z', { recordId: 'r1' }),
    ])
    const trunkNode = graph.nodes.find((n) => n.type === 'fork')
    const branchNode = graph.nodes.find((n) => n.type === 'commit')
    expect(trunkNode.color.name).toBe('blue')
    expect(branchNode.color.name).not.toBe('blue')
    expect(branchNode.color.name).toBe('emerald')
  })

  it('stops an open branch lane at its last commit (no phantom extension)', () => {
    const graph = buildTimelineGraph([
      ev('e1', 'RECORD_CREATED', '2026-07-02T00:00:00Z', { recordId: 'r1' }),
      ev('e2', 'RECORD_SUBMITTED', '2026-07-03T00:00:00Z', { recordId: 'r1' }),
    ])
    const lane1 = graph.lanes.find((l) => l.index === 1)
    expect(lane1).toBeTruthy()
    expect(lane1.isOpen).toBe(true)
    const lastCommitRow = graph.nodes.find((n) => n.id === 'e2').row
    expect(lane1.segments[lane1.segments.length - 1].toRow).toBe(lastCommitRow)
  })

  it('stops an older open branch at its last commit when a new branch is forked', () => {
    const graph = buildTimelineGraph([
      ev('e1', 'RECORD_CREATED', '2026-07-02T00:00:00Z', { recordId: 'r1' }),
      ev('e2', 'RECORD_SUBMITTED', '2026-07-03T00:00:00Z', { recordId: 'r1' }),
      ev('e3', 'RECORD_CREATED', '2026-07-04T00:00:00Z', { recordId: 'r2' }),
      ev('e4', 'RECORD_SUBMITTED', '2026-07-05T00:00:00Z', { recordId: 'r2' }),
    ])
    const lane1 = graph.lanes.find((l) => l.index === 1)
    const r1LastCommitRow = graph.nodes.find((n) => n.id === 'e2').row
    // older branch r1 must NOT extend past its last commit, even though r2 is forked above
    expect(lane1.segments[lane1.segments.length - 1].toRow).toBe(r1LastCommitRow)
    expect(lane1.segments[lane1.segments.length - 1].toRow).toBeLessThan(graph.totalRows - 1)
  })

  it('trunk lane always extends its full span', () => {
    const graph = buildTimelineGraph([
      ev('e1', 'PROJECT_CREATED', '2026-07-01T00:00:00Z'),
      ev('e2', 'RECORD_CREATED', '2026-07-02T00:00:00Z', { recordId: 'r1' }),
      ev('e3', 'REVIEW_APPROVED', '2026-07-04T00:00:00Z', { recordId: 'r1' }),
    ])
    const trunk = graph.lanes.find((l) => l.index === 0)
    expect(trunk.segments[0].fromRow).toBe(0)
  })
})

describe('graphNodeLabel', () => {
  const labels = { ATTACHMENT_UPLOADED: '上传了实验附件', RECORD_SUBMITTED: '提交了实验记录审核' }

  it('returns standard label for single event', () => {
    const node = { events: [{ eventType: 'RECORD_SUBMITTED', actorId: 'u1' }] }
    expect(graphNodeLabel(node, labels)).toBe('提交了实验记录审核')
  })

  it('returns aggregated label for multiple uploads', () => {
    const node = { events: [
      { eventType: 'ATTACHMENT_UPLOADED', actorId: 'u1' },
      { eventType: 'ATTACHMENT_UPLOADED', actorId: 'u1' },
      { eventType: 'ATTACHMENT_UPLOADED', actorId: 'u1' },
    ]}
    expect(graphNodeLabel(node, labels)).toBe('上传了 3 个附件')
  })

  it('returns aggregated label for export previews', () => {
    const node = { events: [
      { eventType: 'RECORD_EXPORT_PREVIEW', actorId: 'u1' },
      { eventType: 'RECORD_EXPORT_PREVIEW', actorId: 'u1' },
    ]}
    expect(graphNodeLabel(node, {})).toBe('预览了 2 次报告')
  })
})

describe('graphNodeContext', () => {
  it('returns title for trunk node with record context', () => {
    const node = { type: 'trunk', recordId: 'r1', events: [{ metadata: { title: 'PCR 实验' } }] }
    expect(graphNodeContext(node)).toBe('PCR 实验')
  })

  it('returns filename for trunk attachment event', () => {
    const node = { type: 'trunk', recordId: 'r1', events: [{ metadata: { filename: '结果.png' } }] }
    expect(graphNodeContext(node)).toBe('结果.png')
  })

  it('returns empty string for branch commit nodes', () => {
    const node = { type: 'commit', recordId: 'r1', events: [{ metadata: { title: 'PCR 实验' } }] }
    expect(graphNodeContext(node)).toBe('')
  })
})
