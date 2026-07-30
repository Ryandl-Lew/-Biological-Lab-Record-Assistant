import { useState } from 'react'
import { Maximize2, X } from 'lucide-react'

function extent(values, fallback = [0, 1]) {
  const nums = values.filter((value) => Number.isFinite(value))
  if (!nums.length) return fallback
  let min = Math.min(...nums)
  let max = Math.max(...nums)
  if (min === max) {
    const pad = Math.abs(min) * 0.1 || 1
    min -= pad
    max += pad
  }
  return [min, max]
}

function niceTicks(min, max, count = 4) {
  if (!Number.isFinite(min) || !Number.isFinite(max) || min === max) return [min]
  const span = max - min
  const step = span / Math.max(1, count - 1)
  return Array.from({ length: count }, (_, index) => min + step * index)
}

function formatTick(value) {
  if (!Number.isFinite(value)) return ''
  if (Math.abs(value) >= 1000 || (Math.abs(value) > 0 && Math.abs(value) < 0.01)) {
    return value.toExponential(2)
  }
  return Number(value.toPrecision(4)).toString()
}

/**
 * @param {{
 *   type?: 'bar'|'line'|'scatter',
 *   title?: string,
 *   xLabel?: string,
 *   yLabel?: string,
 *   series?: Array<{ name?: string, points?: Array<{ x:number, y:number, label?: string }> }>,
 *   height?: number,
 * }} props
 */
export default function DataChart({
  type = 'line',
  title,
  xLabel = 'x',
  yLabel = 'y',
  series = [],
  height = 220,
}) {
  const [expanded, setExpanded] = useState(false)
  const width = 480
  const pad = { top: 16, right: 16, bottom: 44, left: 48 }
  const plotW = width - pad.left - pad.right
  const plotH = height - pad.top - pad.bottom

  const allPoints = series.flatMap((item) => item.points || [])
  if (!allPoints.length) return null

  const chartType = type === 'bar' || type === 'scatter' ? type : 'line'
  const [yMin, yMax] = extent(allPoints.map((point) => Number(point.y)))
  const categories =
    chartType === 'bar'
      ? allPoints.map(
          (point, index) => point.label || formatTick(Number(point.x)) || String(index + 1),
        )
      : null
  const [xMin, xMax] =
    chartType === 'bar'
      ? [0, Math.max(1, allPoints.length)]
      : extent(allPoints.map((point) => Number(point.x)))

  const sx = (x) => pad.left + ((x - xMin) / (xMax - xMin || 1)) * plotW
  const sy = (y) => pad.top + plotH - ((y - yMin) / (yMax - yMin || 1)) * plotH
  const yTicks = niceTicks(yMin, yMax)
  const xTicks =
    chartType === 'bar' ? allPoints.map((_, index) => index + 0.5) : niceTicks(xMin, xMax)

  const palette = ['#0f766e', '#2563eb', '#c2410c', '#7c3aed']

  return (
    <div
      className="mt-2 overflow-x-auto rounded-lg border border-slate-200 bg-white p-2"
      role="img"
      aria-label={title || '数据图'}
    >
      <div className="flex items-center justify-between mb-1 px-1">
        {title ? <p className="text-xs font-medium text-slate-600">{title}</p> : <span />}
        <button
          className="rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          onClick={() => setExpanded(true)}
          aria-label="放大图表"
        >
          <Maximize2 size={14} />
        </button>
      </div>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="h-auto w-full min-w-[20rem]"
        preserveAspectRatio="xMidYMid meet"
      >
        <rect x={pad.left} y={pad.top} width={plotW} height={plotH} fill="#f8fafc" />
        {yTicks.map((tick) => (
          <g key={`y-${tick}`}>
            <line
              x1={pad.left}
              x2={pad.left + plotW}
              y1={sy(tick)}
              y2={sy(tick)}
              stroke="#e2e8f0"
              strokeWidth="1"
            />
            <text
              x={pad.left - 6}
              y={sy(tick) + 3}
              textAnchor="end"
              className="fill-slate-400"
              fontSize="10"
            >
              {formatTick(tick)}
            </text>
          </g>
        ))}
        {xTicks.map((tick, index) => (
          <text
            key={`x-${tick}-${index}`}
            x={sx(tick)}
            y={pad.top + plotH + 16}
            textAnchor="middle"
            className="fill-slate-400"
            fontSize="10"
          >
            {chartType === 'bar' ? (categories[index] || '').slice(0, 8) : formatTick(tick)}
          </text>
        ))}
        <text
          x={pad.left + plotW / 2}
          y={height - 6}
          textAnchor="middle"
          className="fill-slate-500"
          fontSize="11"
        >
          {xLabel}
        </text>
        <text
          x={14}
          y={pad.top + plotH / 2}
          textAnchor="middle"
          className="fill-slate-500"
          fontSize="11"
          transform={`rotate(-90 14 ${pad.top + plotH / 2})`}
        >
          {yLabel}
        </text>

        {series.map((item, seriesIndex) => {
          const points = item.points || []
          const color = palette[seriesIndex % palette.length]
          if (chartType === 'bar') {
            const barW = Math.max(4, (plotW / Math.max(1, points.length)) * 0.55)
            return (
              <g key={item.name || seriesIndex}>
                {points.map((point, index) => {
                  const cx = sx(index + 0.5)
                  const top = sy(Number(point.y))
                  const base = sy(Math.max(0, yMin) === yMin && yMin < 0 ? 0 : yMin)
                  const y = Math.min(top, base)
                  const h = Math.max(1, Math.abs(base - top))
                  return (
                    <rect
                      key={`${item.name}-${index}`}
                      x={cx - barW / 2}
                      y={y}
                      width={barW}
                      height={h}
                      fill={color}
                      opacity="0.85"
                    />
                  )
                })}
              </g>
            )
          }

          const path = points
            .map(
              (point, index) =>
                `${index === 0 ? 'M' : 'L'}${sx(Number(point.x))},${sy(Number(point.y))}`,
            )
            .join(' ')
          const isCurve = (item.name || '').includes('拟合')
          return (
            <g key={item.name || seriesIndex}>
              {(chartType === 'line' || isCurve) && points.length > 1 ? (
                <path d={path} fill="none" stroke={color} strokeWidth={isCurve ? 2 : 1.75} />
              ) : null}
              {(chartType === 'scatter' || !isCurve) &&
                points.map((point, index) => (
                  <circle
                    key={`${item.name}-${index}`}
                    cx={sx(Number(point.x))}
                    cy={sy(Number(point.y))}
                    r={chartType === 'scatter' || isCurve ? 3.5 : 2.5}
                    fill={isCurve ? 'none' : color}
                    stroke={color}
                    strokeWidth={isCurve ? 0 : 1}
                  />
                ))}
              {isCurve && points.length > 1 ? null : null}
            </g>
          )
        })}
      </svg>
      {series.length > 1 ? (
        <div className="mt-1 flex flex-wrap gap-3 px-1 text-[11px] text-slate-500">
          {series.map((item, index) => (
            <span key={item.name || index} className="inline-flex items-center gap-1">
              <span
                className="inline-block h-2 w-2 rounded-full"
                style={{ background: palette[index % palette.length] }}
              />
              {item.name || `系列 ${index + 1}`}
            </span>
          ))}
        </div>
      ) : null}
      {expanded && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4"
          onClick={() => setExpanded(false)}
        >
          <div
            className="flex max-h-[90vh] w-full max-w-5xl flex-col rounded-xl bg-white p-4 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-2 flex items-center justify-between">
              <p className="text-sm font-semibold text-slate-700">{title || '数据图'}</p>
              <button
                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100"
                onClick={() => setExpanded(false)}
                aria-label="关闭"
              >
                <X size={18} />
              </button>
            </div>
            <div className="min-h-0 flex-1 overflow-auto">
              <svg
                viewBox={`0 0 ${width} ${height}`}
                className="h-auto w-full"
                preserveAspectRatio="xMidYMid meet"
              >
                <rect x={pad.left} y={pad.top} width={plotW} height={plotH} fill="#f8fafc" />
                {yTicks.map((tick) => (
                  <g key={`ey-${tick}`}>
                    <line
                      x1={pad.left}
                      x2={pad.left + plotW}
                      y1={sy(tick)}
                      y2={sy(tick)}
                      stroke="#e2e8f0"
                      strokeWidth="1"
                    />
                    <text
                      x={pad.left - 6}
                      y={sy(tick) + 3}
                      textAnchor="end"
                      className="fill-slate-400"
                      fontSize="12"
                    >
                      {formatTick(tick)}
                    </text>
                  </g>
                ))}
                {xTicks.map((tick, index) => (
                  <text
                    key={`ex-${tick}-${index}`}
                    x={sx(tick)}
                    y={pad.top + plotH + 18}
                    textAnchor="middle"
                    className="fill-slate-400"
                    fontSize="11"
                  >
                    {chartType === 'bar'
                      ? (categories[index] || '').slice(0, 12)
                      : formatTick(tick)}
                  </text>
                ))}
                <text
                  x={pad.left + plotW / 2}
                  y={height - 6}
                  textAnchor="middle"
                  className="fill-slate-500"
                  fontSize="13"
                >
                  {xLabel}
                </text>
                <text
                  x={16}
                  y={pad.top + plotH / 2}
                  textAnchor="middle"
                  className="fill-slate-500"
                  fontSize="13"
                  transform={`rotate(-90 16 ${pad.top + plotH / 2})`}
                >
                  {yLabel}
                </text>
                {series.map((item, seriesIndex) => {
                  const points = item.points || []
                  const color = palette[seriesIndex % palette.length]
                  if (chartType === 'bar') {
                    const barW = Math.max(6, (plotW / Math.max(1, points.length)) * 0.55)
                    return (
                      <g key={item.name || seriesIndex}>
                        {points.map((point, index) => {
                          const cx = sx(index + 0.5),
                            top = sy(Number(point.y)),
                            base = sy(Math.max(0, yMin))
                          return (
                            <rect
                              key={`${item.name}-${index}`}
                              x={cx - barW / 2}
                              y={Math.min(top, base)}
                              width={barW}
                              height={Math.max(1, Math.abs(base - top))}
                              fill={color}
                              opacity="0.85"
                            />
                          )
                        })}
                      </g>
                    )
                  }
                  const path = points
                    .map(
                      (point, index) =>
                        `${index === 0 ? 'M' : 'L'}${sx(Number(point.x))},${sy(Number(point.y))}`,
                    )
                    .join(' ')
                  const isCurve = (item.name || '').includes('拟合')
                  return (
                    <g key={item.name || seriesIndex}>
                      {(chartType === 'line' || isCurve) && points.length > 1 ? (
                        <path d={path} fill="none" stroke={color} strokeWidth={2} />
                      ) : null}
                      {points.map((point, index) => (
                        <circle
                          key={`${item.name}-${index}`}
                          cx={sx(Number(point.x))}
                          cy={sy(Number(point.y))}
                          r={isCurve ? 4 : 3}
                          fill={isCurve ? 'none' : color}
                          stroke={color}
                        />
                      ))}
                    </g>
                  )
                })}
              </svg>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export function chartFromFit(fit) {
  if (!fit) return null
  const series = []
  if (Array.isArray(fit.points) && fit.points.length) {
    series.push({
      name: '观测点',
      points: fit.points.map((point) => ({
        x: Number(point.x),
        y: Number(point.y),
        label: point.recordCode,
      })),
    })
  }
  if (Array.isArray(fit.curveSample) && fit.curveSample.length) {
    series.push({
      name: '拟合曲线',
      points: fit.curveSample.map((point) => ({ x: Number(point.x), y: Number(point.y) })),
    })
  }
  if (!series.length) return null
  return {
    type: 'scatter',
    title: `拟合图：${fit.equation || ''}`.trim(),
    xLabel: 'x',
    yLabel: 'y',
    series,
  }
}
