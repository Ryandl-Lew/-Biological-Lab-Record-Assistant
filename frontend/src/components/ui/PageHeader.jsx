/**
 * PageHeader 页面标题区（新设计）
 * 统一渲染 eyebrow 小标题 + 主标题 + 描述 + 右侧操作区。
 */

/**
 * @param {Object} props
 * @param {string} [props.eyebrow] 小标题
 * @param {import('react').ReactNode} props.title 主标题
 * @param {string} [props.description] 描述文本
 * @param {import('react').ReactNode} [props.actions] 右侧操作区
 */
export default function PageHeader({ eyebrow, title, description, actions }) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div className="min-w-0">
        {eyebrow && (
          <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-brand-600">
            {eyebrow}
          </p>
        )}
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">{title}</h1>
        {description && <p className="mt-1.5 max-w-2xl text-sm text-slate-500">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2.5">{actions}</div>}
    </div>
  )
}
