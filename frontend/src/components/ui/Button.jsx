/**
 * Button 统一按钮组件
 * 全站按钮的唯一入口，保证每个页面的按钮样式与交互一致。
 *
 * variant:
 *   primary       主操作（每页建议只有 1 个）
 *   secondary     次操作（带边框白底）
 *   ghost         弱操作（无边框，hover 出底色）
 *   danger        危险操作（删除、退回等）
 *   white         深色/渐变背景上的主操作
 *   outlineWhite  深色/渐变背景上的次操作
 * size: sm / md
 * 支持 icon（lucide 组件）、loading、to（渲染为路由 Link）。
 */
import { Link } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { forwardRef } from 'react'

const VARIANTS = {
  primary:
    'bg-brand-600 text-white shadow-sm hover:bg-brand-700 active:bg-brand-800 disabled:opacity-60',
  secondary:
    'border border-slate-300 bg-white text-slate-700 shadow-sm hover:border-slate-400 hover:bg-slate-50 disabled:opacity-60',
  ghost:
    'text-slate-600 hover:bg-slate-100 hover:text-slate-900 disabled:opacity-60',
  danger:
    'border border-red-200 bg-white text-red-600 shadow-sm hover:bg-red-50 hover:border-red-300 disabled:opacity-60',
  white:
    'bg-white text-brand-700 shadow-sm hover:bg-brand-50 disabled:opacity-60',
  outlineWhite:
    'border border-white/50 text-white hover:bg-white/10 disabled:opacity-60',
}

const SIZES = {
  sm: 'h-8 gap-1.5 rounded-lg px-3 text-xs',
  md: 'h-10 gap-2 rounded-lg px-4 text-sm',
}

const ICON_SIZES = { sm: 14, md: 16 }

const Button = forwardRef(function Button({
  variant = 'primary',
  size = 'md',
  icon: Icon,
  loading = false,
  to,
  type = 'button',
  disabled,
  className = '',
  children,
  ...rest
}, ref) {
  const cls = [
    'inline-flex select-none items-center justify-center whitespace-nowrap font-medium transition-colors',
    VARIANTS[variant] ?? VARIANTS.primary,
    SIZES[size] ?? SIZES.md,
    className,
  ].join(' ')

  const iconSize = ICON_SIZES[size] ?? 16
  const content = (
    <>
      {loading ? (
        <Loader2 size={iconSize} className="animate-spin" />
      ) : Icon ? (
        <Icon size={iconSize} strokeWidth={2.2} />
      ) : null}
      {children}
    </>
  )

  if (to) {
    return (
      <Link ref={ref} to={to} className={cls} {...rest}>
        {content}
      </Link>
    )
  }

  return (
    <button ref={ref} type={type} disabled={disabled || loading} className={cls} {...rest}>
      {content}
    </button>
  )
})

export default Button
