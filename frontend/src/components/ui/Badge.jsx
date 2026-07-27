/**
 * Badge 状态徽章（新设计）
 * 统一渲染各类状态 / 分类标签，色系由 tone 决定。
 */

const TONES = {
  gray: 'bg-slate-100 text-slate-600 ring-slate-200',
  blue: 'bg-brand-50 text-brand-700 ring-brand-200',
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  amber: 'bg-amber-50 text-amber-700 ring-amber-200',
  red: 'bg-red-50 text-red-700 ring-red-200',
  violet: 'bg-violet-50 text-violet-700 ring-violet-200',
}

const DOTS = {
  gray: 'bg-slate-400',
  blue: 'bg-brand-500',
  green: 'bg-emerald-500',
  amber: 'bg-amber-500',
  red: 'bg-red-500',
  violet: 'bg-violet-500',
}

/**
 * @param {Object} props
 * @param {import('@/domain/common').BadgeTone} [props.tone] 色系，默认 gray
 * @param {boolean} [props.dot] 是否显示状态圆点
 * @param {import('react').ReactNode} props.children
 */
export default function Badge({ tone = 'gray', dot = false, children }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${TONES[tone] ?? TONES.gray}`}
    >
      {dot && <span className={`h-1.5 w-1.5 rounded-full ${DOTS[tone] ?? DOTS.gray}`} />}
      {children}
    </span>
  )
}
