import { useState } from 'react'
import { CalendarDays, Check, Mail, Pencil, Upload, UserRound, X } from 'lucide-react'
import { Button, PageHeader, Surface } from '@/components/ui'
import { useAuthStore } from '@/store/authStore'

export default function ProfilePage() {
  const currentUser = useAuthStore((s) => s.currentUser)
  const updateProfile = useAuthStore((s) => s.updateProfile)
  const uploadAvatar = useAuthStore((s) => s.uploadAvatar)
  const [displayName, setDisplayName] = useState(currentUser?.displayName || '')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState(false)
  const save = async (event) => {
    event.preventDefault(); setSaving(true); setError(''); setMessage('')
    try { await updateProfile(displayName.trim()); setMessage('资料已更新'); setEditing(false) }
    catch (requestError) { setError(requestError.message) }
    finally { setSaving(false) }
  }
  const avatar = async (event) => {
    const file = event.target.files?.[0]; if (!file) return
    setSaving(true); setError(''); setMessage('')
    try { await uploadAvatar(file); setMessage('头像已更新') }
    catch (requestError) { setError(requestError.message) }
    finally { setSaving(false); event.target.value = '' }
  }
  const cancelEdit = () => { setDisplayName(currentUser?.displayName || ''); setEditing(false); setError('') }
  return (
    <section>
      <PageHeader eyebrow="个人中心" title="用户信息" />
      <div className="mx-auto grid max-w-5xl items-start gap-6 lg:grid-cols-[320px,minmax(0,1fr)]">
        <Surface className="text-center lg:sticky lg:top-20">
          <div className="mx-auto flex h-28 w-28 items-center justify-center overflow-hidden rounded-full bg-gradient-to-br from-brand-500 to-indigo-600 text-3xl font-bold text-white ring-4 ring-brand-50">
            {currentUser?.avatarUrl ? <img src={currentUser.avatarUrl} alt="用户头像" className="h-full w-full object-cover"/> : currentUser?.displayName?.slice(0,1) || <UserRound size={34}/>} 
          </div>
          <h2 className="mt-4 text-lg font-semibold">{currentUser?.displayName}</h2>
          <p className="mt-1 text-sm text-slate-500">{currentUser?.email}</p>
          <label className="mt-5 inline-flex cursor-pointer items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium transition hover:bg-slate-50"><Upload size={15}/>更换头像<input type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={avatar} disabled={saving}/></label>
        </Surface>
        <Surface title="账户资料" extra={!editing && <Button size="sm" variant="secondary" icon={Pencil} onClick={() => { setEditing(true); setMessage('') }}>编辑资料</Button>}>
          <form onSubmit={save}>
            <dl className="divide-y divide-slate-100">
              <div className="grid gap-2 py-5 sm:grid-cols-[150px,minmax(0,1fr)] sm:items-center"><dt className="flex items-center gap-2 text-sm text-slate-500"><UserRound size={16}/>用户名</dt><dd>{editing ? <input id="profile-name" aria-label="用户名" value={displayName} onChange={(event) => setDisplayName(event.target.value)} maxLength={50} required autoFocus className="input h-11"/> : <span className="font-medium">{currentUser?.displayName}</span>}</dd></div>
              <div className="grid gap-2 py-5 sm:grid-cols-[150px,minmax(0,1fr)] sm:items-center"><dt className="flex items-center gap-2 text-sm text-slate-500"><Mail size={16}/>注册邮箱</dt><dd className="font-medium">{currentUser?.email}</dd></div>
              <div className="grid gap-2 py-5 sm:grid-cols-[150px,minmax(0,1fr)] sm:items-center"><dt className="flex items-center gap-2 text-sm text-slate-500"><CalendarDays size={16}/>注册时间</dt><dd className="font-medium">{currentUser?.createdAt ? new Date(currentUser.createdAt).toLocaleString() : '-'}</dd></div>
            </dl>
            {(message || error) && <p role="status" className={`mt-4 rounded-lg p-3 text-sm ${error ? 'bg-red-50 text-red-700' : 'bg-emerald-50 text-emerald-700'}`}>{error || message}</p>}
            {editing && <div className="mt-6 flex justify-end gap-2 border-t border-slate-100 pt-5"><Button type="button" variant="secondary" icon={X} onClick={cancelEdit} disabled={saving}>取消</Button><Button type="submit" icon={Check} loading={saving} disabled={!displayName.trim()}>保存资料</Button></div>}
          </form>
        </Surface>
      </div>
    </section>
  )
}
