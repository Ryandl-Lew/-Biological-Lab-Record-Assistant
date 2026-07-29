/**
 * QuickStartList —— 左侧"开始"动作列表。
 * 仿 VSCode 欢迎页「Start」区，把分散的入口汇聚成 4 个常用动作。
 */
import { useNavigate } from 'react-router-dom'
import { Search, Plus, FolderPlus } from 'lucide-react'

const ITEMS = [
  { key: 'new-record', label: '新建实验记录', icon: Plus, to: '/records/new' },
  { key: 'new-project', label: '新建项目', icon: FolderPlus, to: '/projects?new=1' },
  { key: 'search', label: '全局搜索', icon: Search, to: '/search', kbd: '⌘K' },
]

export default function QuickStartList() {
  const navigate = useNavigate()
  return (
    <nav aria-label="快速开始" className="space-y-0.5">
      <h2 className="mb-2 px-1 text-xs font-semibold uppercase tracking-wider text-slate-400">开始</h2>
      {ITEMS.map((item) => (
        <button
          key={item.key}
          type="button"
          onClick={() => navigate(item.to)}
          className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm font-medium text-slate-700 transition-colors hover:bg-slate-100 hover:text-slate-900"
        >
          <item.icon size={17} strokeWidth={1.8} className="text-slate-500" />
          <span className="flex-1">{item.label}</span>
          {item.kbd && (
            <kbd className="rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-[11px] font-medium text-slate-400">
              {item.kbd}
            </kbd>
          )}
        </button>
      ))}
    </nav>
  )
}