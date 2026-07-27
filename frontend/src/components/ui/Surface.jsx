/**
 * Surface 卡片容器（新设计）
 * 白色圆角卡片；可选带标题栏（title + 右侧操作区）。
 */

/**
 * @param {Object} props
 * @param {import('react').ReactNode} [props.title] 卡片标题
 * @param {import('react').ReactNode} [props.extra] 标题右侧操作区
 * @param {string} [props.className]
 * @param {import('react').CSSProperties} [props.style]
 * @param {import('react').ReactNode} props.children
 */
export default function Surface({ title, extra, className = '', style, children }) {
  return (
    <section
      className={`rounded-xl border border-slate-200 bg-white p-5 shadow-card ${className}`.trim()}
      style={style}
    >
      {(title || extra) && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          {title ? (
            <h2 className="text-base font-semibold text-slate-900">{title}</h2>
          ) : (
            <span />
          )}
          {extra}
        </div>
      )}
      {children}
    </section>
  )
}
