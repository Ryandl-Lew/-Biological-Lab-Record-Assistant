/**
 * EmptyState 空状态
 * 列表 / 面板无数据时的统一占位。
 */

/**
 * @param {Object} props
 * @param {import('lucide-react').LucideIcon} props.icon 图标组件
 * @param {string} props.title 标题
 * @param {string} [props.description] 描述
 * @param {import('react').ReactNode} [props.action] 引导操作按钮
 */
export default function EmptyState({ icon: Icon, title, description, action }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-slate-50/60 px-6 py-14 text-center">
      {Icon && (
        <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-white text-slate-400 shadow-card">
          <Icon size={22} strokeWidth={1.8} />
        </div>
      )}
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
      {description && <p className="mt-1 max-w-sm text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}
