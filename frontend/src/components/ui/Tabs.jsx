/**
 * Tabs 分段选项卡
 * 用于模板分类、搜索结果分类等场景。
 */

/**
 * @param {Object} props
 * @param {{ key: string, label: import('react').ReactNode }[]} props.items
 * @param {string} props.activeKey
 * @param {(key: string) => void} props.onChange
 */
export default function Tabs({ items, activeKey, onChange }) {
  return (
    <div className="flex w-fit max-w-full flex-wrap gap-1 rounded-xl bg-slate-100 p-1">
      {items.map((item) => {
        const active = item.key === activeKey
        return (
          <button
            key={item.key}
            type="button"
            onClick={() => onChange(item.key)}
            className={`rounded-lg px-3.5 py-1.5 text-sm font-medium transition-all ${
              active
                ? 'bg-white text-slate-900 shadow-card'
                : 'text-slate-500 hover:text-slate-900'
            }`}
          >
            {item.label}
          </button>
        )
      })}
    </div>
  )
}
