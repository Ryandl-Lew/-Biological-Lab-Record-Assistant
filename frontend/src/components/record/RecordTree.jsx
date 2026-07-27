import { useEffect, useState } from 'react'
import { ChevronDown, ChevronRight, FileText, Folder, FolderOpen } from 'lucide-react'
import StatusBadge from '@/components/ui/StatusBadge'

export default function RecordTree({ projects, records, selectedId, onSelect }) {
  const [expanded, setExpanded] = useState({})

  useEffect(() => {
    setExpanded((current) => {
      if (Object.keys(current).length) return current
      return Object.fromEntries(projects.map((project) => [project.id, true]))
    })
  }, [projects])

  return <div className="space-y-1">
    {projects.map((project) => {
      const projectRecords = records.filter((record) => record.projectId === project.id)
      const open = expanded[project.id] !== false
      return <div key={project.id}>
        <button type="button" onClick={() => setExpanded((current) => ({ ...current, [project.id]: !open }))} className="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left text-sm font-medium text-slate-700 hover:bg-slate-100">
          {open ? <ChevronDown size={14}/> : <ChevronRight size={14}/>}
          {open ? <FolderOpen size={17} className="text-amber-500"/> : <Folder size={17} className="text-amber-500"/>}
          <span className="min-w-0 flex-1 truncate">{project.name}</span>
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-500">{projectRecords.length}</span>
        </button>
        {open && <div className="relative ml-[19px] border-l border-slate-200 py-1 pl-3">
          {projectRecords.length ? projectRecords.map((record) => <button type="button" key={record.id} onClick={() => onSelect(record)} className={`mb-1 flex w-full items-start gap-2 rounded-lg px-3 py-2.5 text-left transition ${selectedId === record.id ? 'bg-brand-50 text-brand-800 ring-1 ring-brand-200' : 'text-slate-600 hover:bg-slate-50'}`}>
            <FileText size={15} className={`mt-0.5 shrink-0 ${selectedId === record.id ? 'text-brand-600' : 'text-slate-400'}`}/>
            <span className="min-w-0 flex-1"><span className="block truncate text-sm font-medium">{record.title}</span><span className="mt-1 flex items-center justify-between gap-2"><span className="truncate text-[11px] text-slate-400">{record.code}</span><StatusBadge kind="record" status={record.status}/></span></span>
          </button>) : <p className="px-3 py-2 text-xs text-slate-400">暂无匹配记录</p>}
        </div>}
      </div>
    })}
  </div>
}
