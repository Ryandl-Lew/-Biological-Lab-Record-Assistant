/**
 * Sidebar 侧边栏导航（新设计）
 * 品牌区 + 分组导航 + 底部用户卡片。
 * 使用 lucide-react 图标，Tailwind 工具类。
 */
import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard,
  FolderKanban,
  NotebookPen,
  LayoutTemplate,
  Search,
  UserRound,
  FlaskConical,
  LogOut,
} from 'lucide-react'
import { useAuthStore } from '@/store/authStore'

/** 导航分组配置 */
const NAV_SECTIONS = [
  {
    title: '项目工作区',
    items: [
      { to: '/', label: '工作台', icon: LayoutDashboard, end: true },
      { to: '/projects', label: '项目管理', icon: FolderKanban },
      { to: '/records', label: '实验记录', icon: NotebookPen },
      { to: '/templates', label: '模板中心', icon: LayoutTemplate },
    ],
  },
  {
    title: '发现与账户',
    items: [
      { to: '/search', label: '搜索中心', icon: Search },
      { to: '/profile', label: '个人中心', icon: UserRound },
    ],
  },
]

export default function Sidebar({ onNavigate }) {
  const navigate = useNavigate()
  const currentUser = useAuthStore((s) => s.currentUser)
  const logout = useAuthStore((s) => s.logout)

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex h-full flex-col border-r border-slate-200 bg-white">
      {/* 品牌区 */}
      <div className="flex h-16 items-center gap-3 border-b border-slate-100 px-5">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-brand-600 to-indigo-600 text-white shadow-sm">
          <FlaskConical size={19} strokeWidth={2.2} />
        </div>
        <div className="min-w-0">
          <div className="text-[16px] font-bold leading-tight tracking-tight text-slate-900">
            BioNote
          </div>
          <div className="truncate text-xs text-slate-400">生物实验记录助手</div>
        </div>
      </div>

      {/* 导航 */}
      <nav className="flex-1 overflow-y-auto px-3 py-4" aria-label="主导航">
        {NAV_SECTIONS.map((section) => (
          <div key={section.title} className="mb-5 last:mb-0">
            <div className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
              {section.title}
            </div>
            <div className="space-y-0.5">
              {section.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    [
                      'group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                      isActive
                        ? 'bg-brand-50 text-brand-700'
                        : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
                    ].join(' ')
                  }
                >
                  {({ isActive }) => (
                    <>
                      <item.icon
                        size={18}
                        strokeWidth={isActive ? 2.2 : 1.8}
                        className={
                          isActive
                            ? 'text-brand-600'
                            : 'text-slate-400 group-hover:text-slate-600'
                        }
                      />
                      <span>{item.label}</span>
                    </>
                  )}
                </NavLink>
              ))}
            </div>
          </div>
        ))}
      </nav>

      {/* 底部用户卡片 */}
      {currentUser && (
        <div className="border-t border-slate-100 p-3">
          <button
            type="button"
            onClick={() => {
              navigate('/profile')
              onNavigate?.()
            }}
            className="flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left transition-colors hover:bg-slate-50"
          >
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand-500 to-indigo-500 text-sm font-bold text-white">
              {currentUser.avatarUrl ? <img src={currentUser.avatarUrl} alt="" className="h-full w-full rounded-full object-cover" /> : currentUser.displayName?.slice(0, 1)}
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-semibold text-slate-900">
                {currentUser.displayName}
              </div>
              <div className="truncate text-xs text-slate-400">
                {currentUser.email}
              </div>
            </div>
            <span
              title="进入个人中心"
              className="rounded-lg p-2 text-slate-400"
            >
              <UserRound size={16} />
            </span>
          </button>
          <button
            type="button"
            onClick={handleLogout}
            className="mt-1 flex w-full items-center justify-center gap-2 rounded-lg py-2 text-xs font-medium text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600"
          >
            <LogOut size={14} />
            退出登录
          </button>
        </div>
      )}
    </div>
  )
}
