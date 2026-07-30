/**
 * DashboardMvpPage —— 工作台（VSCode 风欢迎页）
 * ----------------------------------------------------------------------------
 * 左：开始 + 最近访问；右：我参与的项目。
 */
import WelcomeHeader from '@/components/dashboard/WelcomeHeader'
import QuickStartList from '@/components/dashboard/QuickStartList'
import RecentList from '@/components/dashboard/RecentList'
import TasksPanel from '@/components/dashboard/TasksPanel'

export default function DashboardMvpPage() {
  return (
    <section className="space-y-8">
      <WelcomeHeader />
      <div className="grid gap-8 lg:grid-cols-[320px,minmax(0,1fr)]">
        <aside className="lg:sticky lg:top-24 lg:self-start">
          <QuickStartList />
          <RecentList />
        </aside>
        <TasksPanel />
      </div>
    </section>
  )
}
