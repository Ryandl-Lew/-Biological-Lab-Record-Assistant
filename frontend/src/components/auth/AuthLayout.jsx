/**
 * AuthLayout 认证页布局（登录/注册共用）
 * 左侧品牌视觉面板（桌面端显示）+ 右侧表单区。
 */
import { FlaskConical, NotebookPen, Search, ShieldCheck, Users } from 'lucide-react'

const FEATURES = [
  {
    icon: NotebookPen,
    title: '结构化实验记录',
    desc: '模板化记录实验全过程，数据规范可追溯',
  },
  {
    icon: Search,
    title: '全文快速检索',
    desc: '项目、记录、附件，一秒定位所需信息',
  },
  {
    icon: Users,
    title: '团队实时协作',
    desc: '成员共享工作区，进度同步一目了然',
  },
  {
    icon: ShieldCheck,
    title: '审计与版本追踪',
    desc: '完整修订历史，满足科研合规要求',
  },
]

export default function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <div className="grid min-h-screen bg-white lg:grid-cols-[1.05fr,1fr]">
      {/* 左侧品牌面板 */}
      <div className="relative hidden overflow-hidden bg-gradient-to-br from-brand-800 via-brand-600 to-indigo-600 lg:flex lg:flex-col">
        {/* 装饰光斑 */}
        <div className="pointer-events-none absolute -left-32 -top-32 h-96 w-96 rounded-full bg-white/10 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-40 -right-24 h-[28rem] w-[28rem] rounded-full bg-indigo-400/20 blur-3xl" />
        {/* 装饰网格 */}
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.15]"
          style={{
            backgroundImage:
              'linear-gradient(rgba(255,255,255,.35) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.35) 1px, transparent 1px)',
            backgroundSize: '56px 56px',
          }}
        />

        <div className="relative flex h-full flex-col p-12 xl:p-16">
          {/* 品牌 */}
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/[0.15] text-white backdrop-blur">
              <FlaskConical size={21} strokeWidth={2.2} />
            </div>
            <span className="text-lg font-bold tracking-tight text-white">
              BioNote
            </span>
          </div>

          {/* 主视觉文案 */}
          <div className="mt-16 max-w-md xl:mt-24">
            <h1 className="text-4xl font-bold leading-snug tracking-tight text-white xl:text-[2.75rem]">
              让每一次实验
              <br />
              都有迹可循
            </h1>
            <p className="mt-4 text-[15px] leading-relaxed text-brand-100">
              BioNote 是专为生物科研团队打造的电子实验记录本，
              帮助你规范记录、高效检索、安心协作。
            </p>
          </div>

          {/* 特性列表 */}
          <ul className="mt-auto space-y-5 pt-16">
            {FEATURES.map((f) => (
              <li key={f.title} className="flex items-start gap-4">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white/[0.12] text-white backdrop-blur">
                  <f.icon size={18} />
                </div>
                <div>
                  <div className="text-sm font-semibold text-white">{f.title}</div>
                  <div className="mt-0.5 text-[13px] text-brand-100/90">{f.desc}</div>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </div>

      {/* 右侧表单区 */}
      <div className="flex flex-col px-6 py-10 sm:px-12 lg:py-0">
        <div className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center">
          {/* 移动端品牌 */}
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-brand-600 to-indigo-600 text-white">
              <FlaskConical size={20} strokeWidth={2.2} />
            </div>
            <div>
              <div className="text-lg font-bold text-slate-900">BioNote</div>
              <div className="text-xs text-slate-400">生物实验记录助手</div>
            </div>
          </div>

          <div className="animate-fade-in-up">
            <h2 className="text-2xl font-bold tracking-tight text-slate-900">
              {title}
            </h2>
            <p className="mt-1.5 text-sm text-slate-500">{subtitle}</p>

            <div className="mt-8">{children}</div>
          </div>

          {footer && (
            <div className="mt-8 text-center text-sm text-slate-500">{footer}</div>
          )}
        </div>

        <p className="py-6 text-center text-xs text-slate-400">
          © 2026 BioNote · 生物实验记录助手
        </p>
      </div>
    </div>
  )
}
