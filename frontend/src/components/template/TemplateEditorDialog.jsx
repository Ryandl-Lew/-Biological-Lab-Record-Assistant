import { useEffect, useState } from 'react'
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui'
import { TEMPLATE_FIELD_TYPE_LABELS } from '@/domain'

const blankField = (index) => ({ fieldKey: `field_${Date.now()}_${index}`, label: '', fieldType: 'SINGLE_LINE_TEXT', required: false, placeholder: '', defaultValue: null, options: [] })

export default function TemplateEditorDialog({ template, onClose, onSave }) {
  const [form, setForm] = useState({ name: '', experimentType: '', category: '', description: '', fields: [] })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (template) setForm({ name: template.name, experimentType: template.experimentType || '', category: template.category || '', description: template.description || '', fields: template.fields.map((field) => ({ ...field, options: field.options || [] })), version: template.version })
    else setForm({ name: '', experimentType: '', category: '', description: '', fields: [blankField(0)] })
  }, [template])

  const update = (index, key, value) => setForm((current) => ({ ...current, fields: current.fields.map((field, i) => i === index ? { ...field, [key]: value } : field) }))
  const move = (index, direction) => setForm((current) => {
    const fields = [...current.fields]
    const target = index + direction
    if (target < 0 || target >= fields.length) return current
    ;[fields[index], fields[target]] = [fields[target], fields[index]]
    return { ...current, fields }
  })
  const submit = async (event) => {
    event.preventDefault()
    setError('')
    const labels = form.fields.map((field) => field.label.trim().toLowerCase())
    if (new Set(labels).size !== labels.length) { setError('字段名称不能重复'); return }
    if (form.fields.some((field) => !field.label.trim())) { setError('字段名称不能为空'); return }
    if (form.fields.some((field) => field.fieldType === 'SELECT' && !field.options.filter(Boolean).length)) { setError('下拉字段至少需要一个选项'); return }
    setSaving(true)
    try {
      await onSave({ ...form, fields: form.fields.map((field, index) => ({ ...field, fieldKey: field.fieldKey || `field_${index + 1}`, options: field.fieldType === 'SELECT' ? field.options.filter(Boolean) : [], defaultValue: field.fieldType === 'FILE' ? null : field.defaultValue })) })
    } catch (requestError) { setError(requestError.message) } finally { setSaving(false) }
  }

  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"><form onSubmit={submit} className="max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-2xl bg-white p-6 shadow-pop">
    <h2 className="text-lg font-semibold">{template ? '编辑个人模板' : '新建个人模板'}</h2>
    <div className="mt-5 grid gap-4 sm:grid-cols-2"><div><label htmlFor="template-name" className="field-label">模板名称</label><input id="template-name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required className="input" /></div><div><label htmlFor="template-experiment-type" className="field-label">实验类型</label><input id="template-experiment-type" value={form.experimentType} onChange={(e) => setForm({ ...form, experimentType: e.target.value })} className="input" /></div><div><label htmlFor="template-category" className="field-label">分类</label><input id="template-category" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} className="input" /></div><div><label htmlFor="template-description" className="field-label">说明</label><input id="template-description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} className="input" /></div></div>
    <div className="mt-6 flex items-center justify-between"><h3 className="font-semibold">动态字段</h3><Button size="sm" variant="secondary" icon={Plus} onClick={() => setForm((current) => ({ ...current, fields: [...current.fields, blankField(current.fields.length)] }))}>新增字段</Button></div>
    <div className="mt-3 space-y-3">{form.fields.map((field, index) => <div key={field.fieldKey} className="rounded-xl border border-slate-200 p-4"><div className="grid gap-3 md:grid-cols-[1.2fr,1fr,1.2fr,auto]"><input aria-label={`字段 ${index + 1} 名称`} value={field.label} onChange={(e) => update(index, 'label', e.target.value)} placeholder="字段名称" className="input" /><select aria-label={`字段 ${index + 1} 类型`} value={field.fieldType} onChange={(e) => update(index, 'fieldType', e.target.value)} className="input">{Object.entries(TEMPLATE_FIELD_TYPE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><input value={field.placeholder || ''} onChange={(e) => update(index, 'placeholder', e.target.value)} placeholder="提示文字" className="input" /><div className="flex gap-1"><button type="button" aria-label="上移" onClick={() => move(index, -1)} className="rounded p-2 hover:bg-slate-100"><ArrowUp size={15} /></button><button type="button" aria-label="下移" onClick={() => move(index, 1)} className="rounded p-2 hover:bg-slate-100"><ArrowDown size={15} /></button><button type="button" aria-label="删除字段" onClick={() => setForm((current) => ({ ...current, fields: current.fields.filter((_, i) => i !== index) }))} className="rounded p-2 text-red-500 hover:bg-red-50"><Trash2 size={15} /></button></div></div><label className="mt-3 inline-flex items-center gap-2 text-sm"><input type="checkbox" checked={field.required} onChange={(e) => update(index, 'required', e.target.checked)} />提交时必填</label>{field.fieldType === 'SELECT' && <input aria-label={`字段 ${index + 1} 选项`} value={(field.options || []).join('，')} onChange={(e) => update(index, 'options', e.target.value.split(/[，,]/).map((value) => value.trim()))} placeholder="选项用逗号分隔" className="input mt-3" />}</div>)}</div>
    {error && <p role="alert" className="mt-4 text-sm text-red-600">{error}</p>}<div className="mt-6 flex justify-end gap-2"><Button variant="secondary" onClick={onClose}>取消</Button><Button type="submit" loading={saving}>保存模板</Button></div>
  </form></div>
}
