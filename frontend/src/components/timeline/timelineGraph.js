const PALETTE = [
  { bg: 'bg-emerald-500', stroke: '#10b981', light: 'bg-emerald-50', name: 'emerald' },
  { bg: 'bg-orange-500', stroke: '#f97316', light: 'bg-orange-50', name: 'orange' },
  { bg: 'bg-violet-500', stroke: '#8b5cf6', light: 'bg-violet-50', name: 'violet' },
  { bg: 'bg-rose-500', stroke: '#f43f5e', light: 'bg-rose-50', name: 'rose' },
  { bg: 'bg-cyan-500', stroke: '#06b6d4', light: 'bg-cyan-50', name: 'cyan' },
  { bg: 'bg-fuchsia-500', stroke: '#d946ef', light: 'bg-fuchsia-50', name: 'fuchsia' },
]

const TRUNK = { bg: 'bg-blue-500', stroke: '#3b82f6', light: 'bg-blue-50', name: 'blue' }

const BRANCH_COMMIT_TYPES = new Set([
  'ATTACHMENT_UPLOADED', 'ATTACHMENT_DELETED',
  'RECORD_SUBMITTED', 'REVIEW_CHANGES_REQUESTED', 'REVIEWER_REASSIGNED',
  'RECORD_REVISION_RESTORED',
  'RECORD_EXPORT_PREVIEW', 'RECORD_EXPORT_MARKDOWN', 'RECORD_EXPORT_PDF',
])

function sortEvents(events) {
  return [...events].sort((a, b) => {
    const diff = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
    return diff !== 0 ? diff : a.id.localeCompare(b.id)
  })
}

export function buildTimelineGraph(rawEvents) {
  const events = sortEvents(rawEvents)
  const nodes = []
  const forks = []
  const merges = []
  const branchStates = new Map()   // recordId -> { lane, closed }
  const laneNodes = new Map()
  laneNodes.set(0, [])
  const freeLanes = []        // sorted ascending – smallest freed index first
  const closedLanes = new Set()
  const recordColors = new Map()   // recordId → palette entry
  let nextLane = 1
  let nextColorIndex = 0

  function allocLane() {
    const lane = freeLanes.length ? freeLanes.shift() : nextLane++
    if (!laneNodes.has(lane)) laneNodes.set(lane, [])
    return lane
  }

  function freeLane(lane) {
    // insert in sorted order
    const idx = freeLanes.findIndex((v) => v > lane)
    if (idx === -1) freeLanes.push(lane)
    else freeLanes.splice(idx, 0, lane)
  }

  function nodeColor(lane, recordId) {
    if (lane === 0) return TRUNK
    return recordColors.get(recordId) || TRUNK
  }

  function pushNode(lane, type, event, recordId) {
    const node = {
      id: event.id,
      lane,
      type,
      events: [event],
      recordId: recordId || null,
      color: nodeColor(lane, recordId),
      row: 0,
    }
    nodes.push(node)
    laneNodes.get(lane).push(node)
    return node
  }

  // Main loop: process events oldest → newest
  for (const event of events) {
    const { eventType, recordId, actorId } = event

    if (eventType === 'RECORD_CREATED' && recordId) {
      const lane = allocLane()
      recordColors.set(recordId, PALETTE[nextColorIndex % PALETTE.length])
      nextColorIndex++
      branchStates.set(recordId, { lane, closed: false })
      pushNode(0, 'fork', event, recordId)
      continue
    }

    if (eventType === 'REVIEW_APPROVED' && recordId) {
      const state = branchStates.get(recordId)
      if (state && !state.closed) {
        const trunkNode = pushNode(0, 'merge', event, recordId)
        const commitNodes = laneNodes.get(state.lane).filter((n) => n.type === 'commit')
        if (commitNodes.length) {
          merges.push({ recordId, to: trunkNode })
        }
        state.closed = true
        closedLanes.add(state.lane)
        freeLane(state.lane)
      } else {
        pushNode(0, 'trunk', event, recordId)
      }
      continue
    }

    if (eventType === 'RECORD_DELETED' && recordId) {
      const state = branchStates.get(recordId)
      if (state && !state.closed) {
        const trunkNode = pushNode(0, 'terminus', event, recordId)
        const commitNodes = laneNodes.get(state.lane).filter((n) => n.type === 'commit')
        if (commitNodes.length) {
          merges.push({ recordId, to: trunkNode })
        }
        state.closed = true
        closedLanes.add(state.lane)
        freeLane(state.lane)
      } else {
        pushNode(0, 'trunk', event, recordId)
      }
      continue
    }

    // Branch commit events (with open record lane)
    const isBranchCommit = (BRANCH_COMMIT_TYPES.has(eventType) ||
      (eventType === 'AGENT_RUN_SUCCEEDED' && recordId))
    if (isBranchCommit && recordId) {
      const state = branchStates.get(recordId)
      if (state && !state.closed) {
        // Aggregation: consecutive same (eventType, actorId) on same lane
        const prevNodes = laneNodes.get(state.lane)
        const prev = prevNodes[prevNodes.length - 1]
        if (prev && prev.type === 'commit' &&
            prev.events[0].eventType === eventType &&
            prev.events[0].actorId === actorId) {
          prev.events.push(event)
          continue
        }
        pushNode(state.lane, 'commit', event, recordId)
      } else {
        // Post-merge / post-delete: trunk event with record context
        pushNode(0, 'trunk', event, recordId)
      }
      continue
    }

    // Default: trunk project / member / invitation / agent(project-level) events
    pushNode(0, 'trunk', event, recordId || null)
  }

  // Assign row indices (oldest = 0)
  const totalRows = nodes.length
  nodes.forEach((node, index) => { node.row = index })

  // Build fork edges: from each trunk 'fork' node to the first commit on its lane
  const forkNodes = nodes.filter((n) => n.type === 'fork')
  for (const forkNode of forkNodes) {
    const state = branchStates.get(forkNode.recordId)
    if (state) {
      const laneCommits = laneNodes.get(state.lane).filter((n) => n.type === 'commit' && n.recordId === forkNode.recordId)
      if (laneCommits.length) {
        forks.push({ from: forkNode, to: laneCommits[0] })
      }
    }
  }

  // Resolve merge/terminus edges to the last commit on the branch
  for (const merge of merges) {
    const state = branchStates.get(merge.recordId)
    if (state) {
      const laneCommits = laneNodes.get(state.lane).filter((n) => n.type === 'commit' && n.recordId === merge.recordId)
      if (laneCommits.length) {
        const lastCommit = laneCommits[laneCommits.length - 1]
        if (lastCommit.row < merge.to.row) {
          merge.from = lastCommit
        }
      }
    }
  }

  // Build lanes metadata
  const lanes = []
  for (let index = 0; index < nextLane; index++) {
    const lnodes = laneNodes.get(index)
    if (!lnodes || !lnodes.length) continue
    const lastNode = lnodes[lnodes.length - 1]
    const isOpen = index > 0 && lastNode.type === 'commit' &&
      [...branchStates.values()].some((s) => s.lane === index && !s.closed)

    if (index === 0) {
      // trunk is always one continuous segment
      lanes.push({
        index,
        color: TRUNK,
        segments: [{ fromRow: lnodes[0].row, toRow: lnodes[lnodes.length - 1].row, color: TRUNK }],
        isOpen: false,
      })
      continue
    }

    // Branch lane: break into per‑record segments so reused lanes don't connect
    const segments = []
    let seg = null
    for (const node of lnodes) {
      if (!seg || seg.recordId !== node.recordId) {
        seg = { fromRow: node.row, toRow: node.row, recordId: node.recordId, color: recordColors.get(node.recordId) || PALETTE[0] }
        segments.push(seg)
      } else {
        seg.toRow = node.row
      }
    }
    // An open branch should stop at its last commit node and only extend
    // forward when a NEW operation occurs on THAT branch. Forking a different
    // branch is not a new operation here, so we never stretch the lane to the
    // top of the graph — otherwise the branch "keeps extending with no nodes".
    lanes.push({
      index,
      color: recordColors.get(segments[0]?.recordId) || PALETTE[0],
      segments,
      isOpen,
    })
  }

  return { nodes, lanes, forks, merges, totalRows }
}

export function graphNodeLabel(node, labels) {
  const events = node.events
  if (!events || !events.length) return ''
  const count = events.length
  const first = events[0]

  if (count > 1) {
    const type = first.eventType
    if (type === 'ATTACHMENT_UPLOADED') return `上传了 ${count} 个附件`
    if (type === 'ATTACHMENT_DELETED') return `删除了 ${count} 个附件`
    if (type === 'RECORD_EXPORT_PREVIEW') return `预览了 ${count} 次报告`
    if (type === 'RECORD_EXPORT_MARKDOWN') return `导出了 ${count} 次 Markdown`
    if (type === 'RECORD_EXPORT_PDF') return `导出了 ${count} 次 PDF`
  }

  return (labels || {})[first.eventType] || first.eventType
}

export function graphNodeContext(node) {
  const events = node.events
  if (!events || !events.length) return ''
  const first = events[0]
  const meta = first.metadata || {}
  if (node.type === 'trunk' || node.type === 'fork' || node.type === 'merge' || node.type === 'terminus') {
    if (node.recordId) return meta.title || meta.code || meta.name || meta.filename || ''
  }
  return ''
}
