import { useNavigate } from 'react-router-dom'
import { ArrowRight, FolderKanban } from 'lucide-react'
import { Button, StatusBadge, Badge } from '@/components/ui'
import { PROJECT_ROLE_LABELS, PROJECT_ROLE_TONES } from '@/domain'

export default function ProjectCard({ project }) {
  const navigate = useNavigate()
  return (
    <article className="flex flex-col rounded-xl border border-slate-200 bg-white p-5 shadow-card hover:border-brand-200">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3"><span className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-600"><FolderKanban size={18}/></span><div className="min-w-0"><h2 className="truncate text-[15px] font-semibold">{project.name}</h2><div className="mt-1"><Badge tone={PROJECT_ROLE_TONES[project.currentUserRole]}>{PROJECT_ROLE_LABELS[project.currentUserRole]}</Badge></div></div></div>
        <StatusBadge kind="project" status={project.status}/>
      </div>
      <p className="mt-4 line-clamp-3 min-h-[3.9rem] text-sm leading-relaxed text-slate-500">{project.description || '暂无项目简介'}</p>
      <dl className="mt-4 grid grid-cols-3 divide-x divide-slate-200 rounded-xl border border-slate-100 bg-slate-50/80 px-2 py-3 text-center text-xs">
        <div><dt className="text-slate-400">成员</dt><dd className="mt-1 font-medium">{project.memberCount} 人</dd></div>
        <div><dt className="text-slate-400">记录</dt><dd className="mt-1 font-medium">{project.recordCount} 条</dd></div>
        <div><dt className="text-slate-400">更新</dt><dd className="mt-1 font-medium">{new Date(project.updatedAt).toLocaleDateString()}</dd></div>
      </dl>
      <div className="mt-5 border-t border-slate-100 pt-4"><Button className="w-full" icon={ArrowRight} onClick={()=>navigate(`/projects/${project.id}`)}>进入项目</Button></div>
    </article>
  )
}
