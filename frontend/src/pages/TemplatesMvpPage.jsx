import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Copy, Eye, Plus, Trash2 } from 'lucide-react'
import { Badge, Button, EmptyState, PageHeader, Surface, Tabs } from '@/components/ui'
import { copyTemplate, createTemplate, deleteTemplate, fetchTemplates, updateTemplate } from '@/api'
import { TEMPLATE_FIELD_TYPE_LABELS } from '@/domain'
import TemplateEditorDialog from '@/components/template/TemplateEditorDialog'

export default function TemplatesMvpPage() {
  const [searchParams] = useSearchParams()
  const [scope, setScope] = useState('')
  const [items, setItems] = useState([])
  const [editor, setEditor] = useState(undefined)
  const [preview, setPreview] = useState(null)
  const [error, setError] = useState('')
  const load = useCallback(async () => { try { const result = await fetchTemplates({ scope, page: 0, size: 100 }); setItems(result.items); setError('') } catch (requestError) { setError(requestError.message) } }, [scope])
  useEffect(() => { load() }, [load])
  useEffect(() => { const templateId = searchParams.get('templateId'); if (templateId && items.length) setPreview(items.find((item) => item.id === templateId) || null) }, [items, searchParams])
  const save = async (form) => { if (editor?.id) await updateTemplate(editor.id, form); else await createTemplate(form); setEditor(undefined); await load() }
  const remove = async (template) => { if (confirm(`删除“${template.name}”？已创建记录不会受影响。`)) { await deleteTemplate(template.id); await load() } }
  return <section><PageHeader eyebrow="模板中心" title="实验模板" description="系统模板只读；个人模板支持完整字段编辑，并在创建记录时固化结构快照。" actions={<Button icon={Plus} onClick={() => setEditor(null)}>新建模板</Button>} />
    <Tabs items={[{ key: '', label: '全部模板' }, { key: 'SYSTEM', label: '系统模板' }, { key: 'PERSONAL', label: '我的模板' }]} activeKey={scope} onChange={setScope} />
    {error && <p className="mt-4 text-red-600">{error}</p>}
    <div className="mt-6 grid gap-5 md:grid-cols-2 xl:grid-cols-3">{items.map((template) => <article key={template.id} className="rounded-xl border border-slate-200 bg-white p-5 shadow-card"><div className="flex justify-between gap-3"><h2 className="font-semibold">{template.name}</h2><Badge tone={template.scope === 'SYSTEM' ? 'blue' : 'green'}>{template.scope === 'SYSTEM' ? '系统' : '个人'}</Badge></div><p className="mt-2 line-clamp-2 min-h-10 text-sm text-slate-500">{template.description || '暂无说明'}</p><p className="mt-3 text-xs text-slate-400">{template.fields.length} 个字段 · {template.experimentType || '通用实验'}</p><div className="mt-4 flex flex-wrap gap-2"><Button size="sm" variant="secondary" icon={Eye} onClick={() => setPreview(template)}>查看</Button><Button size="sm" variant="secondary" icon={Copy} onClick={async () => { await copyTemplate(template.id); await load() }}>复制</Button>{template.canEdit && <Button size="sm" onClick={() => setEditor(template)}>编辑</Button>}{template.canDelete && <Button size="sm" variant="danger" icon={Trash2} onClick={() => remove(template)}>删除</Button>}</div></article>)}</div>
    {!items.length && !error && <EmptyState title="暂无模板" />}
    {editor !== undefined && <TemplateEditorDialog template={editor} onClose={() => setEditor(undefined)} onSave={save} />}
    {preview && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"><Surface className="max-h-[85vh] w-full max-w-2xl overflow-y-auto" title={preview.name} extra={<Button variant="ghost" onClick={() => setPreview(null)}>关闭</Button>}><div className="space-y-3">{preview.fields.map((field) => <div key={field.fieldKey} className="rounded-lg border p-3"><div className="flex justify-between"><span className="font-medium">{field.label}{field.required && <span className="text-red-500"> *</span>}</span><Badge>{TEMPLATE_FIELD_TYPE_LABELS[field.fieldType]}</Badge></div>{field.placeholder && <p className="mt-1 text-xs text-slate-400">{field.placeholder}</p>}{field.options?.length > 0 && <p className="mt-1 text-xs text-slate-500">选项：{field.options.join('、')}</p>}</div>)}</div></Surface></div>}
  </section>
}
