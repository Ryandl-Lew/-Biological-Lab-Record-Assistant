/**
 * 404 页面（重设计）
 */
import { Home } from 'lucide-react'
import { Button } from '@/components/ui'

export default function NotFoundPage() {
  return (
    <section className="flex min-h-[60vh] flex-col items-center justify-center text-center">
      <p className="bg-gradient-to-br from-brand-600 to-indigo-500 bg-clip-text text-7xl font-bold tracking-tight text-transparent">
        404
      </p>
      <h1 className="mt-4 text-xl font-bold text-slate-900">页面不存在</h1>
      <p className="mt-2 text-sm text-slate-500">你访问的页面可能已被移动或删除。</p>
      <Button to="/" icon={Home} className="mt-7">
        返回工作台
      </Button>
    </section>
  )
}
