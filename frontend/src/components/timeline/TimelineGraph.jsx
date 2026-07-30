import { useMemo, useState } from 'react'
import { buildTimelineGraph, graphNodeLabel, graphNodeContext } from './timelineGraph'

const ROW_HEIGHT = 78
const LANE_WIDTH = 36
const NODE_RADIUS = 6

function laneX(lane) { return lane * LANE_WIDTH + LANE_WIDTH / 2 }
function rowY(row, totalRows) { return (totalRows - 1 - row) * ROW_HEIGHT + ROW_HEIGHT / 2 }

function filterEvents(allEvents, filters) {
  const { eventType: filteredType, actorId, from, to } = filters
  const toDate = to ? new Date(to) : null
  if (toDate) toDate.setDate(toDate.getDate() + 1)

  return allEvents.filter((event) => {
    if (filteredType && event.eventType !== filteredType) return false
    if (actorId && event.actorId !== actorId) return false
    if (from && new Date(event.createdAt) < new Date(from)) return false
    if (toDate && new Date(event.createdAt) >= toDate) return false
    return true
  })
}

export default function TimelineGraph({
  events, labels, icons, members, onViewDetail, emptyMessage = '暂无协作事件',
}) {
  const [filters, setFilters] = useState({ eventType: '', actorId: '', from: '', to: '' })
  const setFilter = (key, value) => setFilters((prev) => ({ ...prev, [key]: value }))
  const clearFilters = () => setFilters({ eventType: '', actorId: '', from: '', to: '' })
  const activeFilters = Object.values(filters).some(Boolean)

  const graph = useMemo(() => buildTimelineGraph(events), [events])

  const { nodes, lanes, forks, merges, totalRows } = graph
  const laneCount = lanes.length + (lanes.length ? 0 : 1)
  const gutterWidth = laneCount * LANE_WIDTH + 24
  const svgHeight = Math.max(totalRows * ROW_HEIGHT, 1)

  const filteredEvents = activeFilters ? filterEvents(events, filters) : events
  const filteredIds = new Set(filteredEvents.map((e) => e.id))
  const dimmedIds = new Set(
    activeFilters
      ? nodes
          .filter((n) => !n.events.some((e) => filteredIds.has(e.id)))
          .map((n) => n.id)
      : [],
  )

  const displayNodes = [...nodes].reverse()
  const matchingCount = activeFilters ? displayNodes.filter((n) => !dimmedIds.has(n.id)).length : null
  const IconComponent = (eventType) => (icons && icons[eventType]) || null

  return (
    <div className="space-y-4">
      <div className="grid gap-3 md:grid-cols-5">
        <div>
          <label htmlFor="tl-type" className="field-label">事件类型</label>
          <select id="tl-type" aria-label="事件类型" value={filters.eventType} onChange={(e) => setFilter('eventType', e.target.value)} className="input h-10">
            <option value="">全部事件</option>
            {Object.entries(labels).filter(([, v]) => v).map(([key, val]) => <option key={key} value={key}>{val}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="tl-actor" className="field-label">操作者</label>
          <select id="tl-actor" value={filters.actorId} onChange={(e) => setFilter('actorId', e.target.value)} className="input h-10">
            <option value="">全部成员</option>
            {members.map((member) => <option key={member.userId} value={member.userId}>{member.displayName}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="tl-from" className="field-label">开始日期</label>
          <div className="relative">
            <input id="tl-from" type="date" value={filters.from} onChange={(e) => setFilter('from', e.target.value)} className="input h-10 pr-3" />
            {!filters.from && (
              <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 bg-white pr-4 text-sm text-slate-400" aria-hidden="true">年 / 月 / 日</span>
            )}
          </div>
        </div>
        <div>
          <label htmlFor="tl-to" className="field-label">结束日期</label>
          <div className="relative">
            <input id="tl-to" type="date" value={filters.to} onChange={(e) => setFilter('to', e.target.value)} className="input h-10 pr-3" />
            {!filters.to && (
              <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 bg-white pr-4 text-sm text-slate-400" aria-hidden="true">年 / 月 / 日</span>
            )}
          </div>
        </div>
        <div className="flex items-end">
          <button type="button" onClick={clearFilters} className="inline-flex items-center text-sm text-brand-600 hover:text-brand-700">清除筛选</button>
        </div>
      </div>

      {activeFilters && matchingCount != null && (
        <p className="rounded-lg bg-brand-50 px-4 py-2 text-sm text-brand-700">
          筛选结果：<span className="font-semibold">{matchingCount}</span> 个匹配节点（淡化项为不匹配）
        </p>
      )}

      {!nodes.length ? (
        <div className="flex h-32 items-center justify-center rounded-xl border text-sm text-slate-400">{emptyMessage}</div>
      ) : (
        <div className="overflow-y-auto rounded-xl border border-slate-200" style={{ maxHeight: '36rem' }}>
          <div className="relative">
            {/* SVG graph overlay — absolute so it shares y‑origin with cards */}
            <svg
              className="pointer-events-none absolute left-0 top-0"
              width={gutterWidth}
              height={svgHeight}
              viewBox={`0 0 ${gutterWidth} ${svgHeight}`}
              aria-hidden="true"
            >
                {/* Lane vertical segments — per‑record breaks avoid cross‑record connections */}
                {lanes.map((lane) =>
                  lane.segments.map((seg, i) => (
                    <line
                      key={`lane-${lane.index}-${i}`}
                      x1={laneX(lane.index)}
                      y1={rowY(seg.fromRow, totalRows)}
                      x2={laneX(lane.index)}
                      y2={rowY(seg.toRow, totalRows)}
                      stroke={seg.color.stroke}
                      strokeWidth={1.5}
                      opacity={0.45}
                    />
                  ))
                )}
              {/* Fork curves */}
              {forks.map((fork, i) => {
                const x1 = laneX(fork.from.lane)
                const y1 = rowY(fork.from.row, totalRows)
                const x2 = laneX(fork.to.lane)
                const y2 = rowY(fork.to.row, totalRows)
                const dx = (x2 - x1) * 0.6
                const d = `M${x1},${y1} C${x1 + dx},${y1} ${x2 - dx * 0.3},${y2} ${x2},${y2}`
                return <path key={`fork-${i}`} d={d} fill="none" stroke={fork.to.color.stroke} strokeWidth={1.5} opacity={0.55} />
              })}
              {/* Merge / terminus curves */}
              {merges.map((merge, i) => {
                if (!merge.from) return null
                const x1 = laneX(merge.from.lane)
                const y1 = rowY(merge.from.row, totalRows)
                const x2 = laneX(merge.to.lane)
                const y2 = rowY(merge.to.row, totalRows)
                const dx = (x2 - x1) * 0.5
                const d = `M${x1},${y1} C${x1 + dx * 0.6},${y1} ${x2 - dx * 0.8},${y2} ${x2},${y2}`
                return <path key={`merge-${i}`} d={d} fill="none" stroke={merge.from.color.stroke} strokeWidth={1.5} opacity={0.55} />
              })}
              {/* Node dots */}
              {nodes.map((node) => {
                const cx = laneX(node.lane)
                const cy = rowY(node.row, totalRows)
                const dimmed = dimmedIds.has(node.id)
                const isTrunk = node.lane === 0
                const ring = isTrunk ? <circle cx={cx} cy={cy} r={NODE_RADIUS + 4} fill="none" stroke={node.color.stroke} strokeWidth={1.5} opacity={dimmed ? 0.2 : 0.45} /> : null
                if (node.type === 'terminus') {
                  return <g key={node.id}>
                    {ring}
                    <circle cx={cx} cy={cy} r={NODE_RADIUS} fill={node.color.stroke} opacity={dimmed ? 0.3 : 1} />
                    <path d={`M${cx - 3},${cy - 3} L${cx + 3},${cy + 3} M${cx + 3},${cy - 3} L${cx - 3},${cy + 3}`} stroke="#fff" strokeWidth={1.5} opacity={dimmed ? 0.3 : 1} />
                  </g>
                }
                return <g key={node.id}>
                  {ring}
                  <circle cx={cx} cy={cy} r={NODE_RADIUS} fill={node.color.stroke} opacity={dimmed ? 0.35 : 1} />
                </g>
              })}
            </svg>

            {/* Content cards — pushed to the right by gutter margin */}
            <ol className="m-0 list-none p-0" style={{ marginLeft: gutterWidth }}>
              {displayNodes.map((node) => {
                const dimmed = dimmedIds.has(node.id)
                const firstEvent = node.events[0]
                const Icon = IconComponent(firstEvent.eventType)
                const label = graphNodeLabel(node, labels)
                const context = graphNodeContext(node)
                const time = new Date(firstEvent.createdAt).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
                const count = node.events.length > 1
                  ? <span className="ml-1 rounded-full bg-slate-200 px-1.5 text-xs text-slate-600">×{node.events.length}</span>
                  : null

                return (
                  <li
                    key={node.id}
                    className={`flex items-center overflow-hidden px-4 py-2 ${dimmed ? 'opacity-15' : ''}`}
                    style={{ height: ROW_HEIGHT }}
                  >
                    <div className={`flex w-full max-w-4xl gap-3 rounded-xl border border-slate-200/60 bg-white/85 py-2.5 px-4 backdrop-blur-sm transition hover:border-brand-200 hover:bg-slate-50`}>
                      {Icon
                        ? <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs ${node.color.light}`} style={{ color: node.color.stroke }}><Icon size={16} /></span>
                        : <span className="h-8 w-8 shrink-0" />}
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm leading-6">
                          <span className="font-semibold text-slate-900">{firstEvent.actorName}</span>
                          <span className="text-slate-600"> {label}</span>
                          {count}
                        </p>
                        {context && <p className="mt-0.5 truncate text-xs text-slate-400">{context}</p>}
                      </div>
                      <div className="flex shrink-0 flex-col items-end gap-1.5 pt-0.5">
                        <span className="whitespace-nowrap text-xs text-slate-400">{time}</span>
                        {node.recordId && (
                          <button
                            type="button"
                            className="rounded-lg px-2.5 py-1 text-xs text-brand-600 transition hover:bg-brand-50"
                            onClick={() => onViewDetail(node)}
                          >
                            查看
                          </button>
                        )}
                      </div>
                    </div>
                  </li>
                )
              })}
            </ol>
          </div>
        </div>
      )}
    </div>
  )
}
