/**
 * RecentList —— 左侧"最近"访问列表（localStorage 持久化）。
 */
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { NotebookPen, FolderKanban, Eraser } from 'lucide-react'
import { clearRecent, getRecent } from '@/lib/recentTracker'
import formatRelativeTime from '@/lib/formatRelativeTime'

const TYPE_ICON = {
  record: NotebookPen,
  project: FolderKanban,
}

export default function RecentList() {
  const navigate = useNavigate()
  const [items, setItems] = useState([])
  const [tick, setTick] = useState(0) // 强制刷新计数

  // 挂载时与每次 tick 变化重新读取
  useEffect(() => {
    setItems(getRecent(8))
  }, [tick])

  const handleClear = () => {
    clearRecent()
    setTick((n) => n + 1)
  }

  const go = (item) => {
    if (item.type === 'record') navigate(`/records/${item.id}`)
    else if (item.type === 'project') navigate(`/projects/${item.id}`)
  }

  return (
    <section className="mt-8" aria-label="最近访问">
      <div className="mb-2 flex items-center justify-between px-1">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400">最近</h2>
        {items.length > 0 && (
          <button type="button" onClick={handleClear} className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-slate-700">
            <Eraser size={13} />
            <span>清除</span>
          </button>
        )}
      </div>
      {items.length === 0 ? (
        <p className="px-3 py-6 text-sm text-slate-400">暂无最近访问，试着打开一条记录或项目吧。</p>
      ) : (
        <ul className="space-y-0.5">
          {items.map((item) => {
            const Icon = TYPE_ICON[item.type] || NotebookPen
            return (
              <li key={`${item.type}-${item.id}`}>
                <button
                  type="button"
                  onClick={() => go(item)}
                  className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left transition-colors hover:bg-slate-100"
                >
                  <Icon size={16} strokeWidth={1.8} className="shrink-0 text-slate-400" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm text-slate-700">{item.title}</span>
                    <span className="block truncate text-xs text-slate-400">
                      {item.subTitle ? `${item.subTitle} · ` : ''}{formatRelativeTime(item.visitedAt)}
                    </span>
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}