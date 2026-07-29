/**
 * WelcomeHeader —— 欢迎页顶部问候条。
 * 仿 VSCode 欢迎界面：eyebrow 问候语 + 主标题。
 */
import { useAuthStore } from '@/store/authStore'

export default function WelcomeHeader() {
  const currentUser = useAuthStore((s) => s.currentUser)
  const name = currentUser?.displayName || '研究者'
  return (
    <header className="border-b border-slate-200 pb-5">
      <p className="mb-1 text-xs font-semibold text-brand-600">工作台</p>
      <h1 className="text-2xl font-bold tracking-tight text-slate-900">你好，{name}</h1>
      <p className="mt-1 text-sm text-slate-500">欢迎回来</p>
    </header>
  )
}
