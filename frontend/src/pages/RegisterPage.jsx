import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { CircleAlert, Loader2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import AuthLayout from '@/components/auth/AuthLayout'

export default function RegisterPage() {
  const [form, setForm] = useState({ displayName: '', email: '', password: '', confirmPassword: '' })
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const register = useAuthStore((s) => s.register)
  const currentUser = useAuthStore((s) => s.currentUser)
  const loading = useAuthStore((s) => s.loading)
  const navigate = useNavigate()
  if (!loading && currentUser) return <Navigate to="/" replace />
  const change = (field) => (event) => { setForm((value) => ({ ...value, [field]: event.target.value })); setFieldErrors((value) => ({ ...value, [field]: undefined })) }
  const submit = async (event) => {
    event.preventDefault(); setError(''); setFieldErrors({})
    if (form.password !== form.confirmPassword) { setFieldErrors({ confirmPassword: '两次输入的密码不一致' }); return }
    setSubmitting(true)
    try { await register(form); navigate('/', { replace: true }) }
    catch (requestError) { setError(requestError.message || '注册失败'); setFieldErrors(requestError.fieldErrors || {}) }
    finally { setSubmitting(false) }
  }
  const field = (name) => fieldErrors[name] && <span className="mt-1 block text-xs text-red-600">{fieldErrors[name]}</span>
  return (
    <AuthLayout title="创建账号" subtitle="加入 BioNote，开始规范记录实验" footer={<span>已有账号？ <Link to="/login" className="font-semibold text-brand-600">返回登录</Link></span>}>
      <form className="space-y-4" onSubmit={submit}>
        <div><label htmlFor="displayName" className="mb-1.5 block text-sm font-medium">用户名</label><input id="displayName" value={form.displayName} onChange={change('displayName')} maxLength={50} required autoFocus className="input h-11"/>{field('displayName')}</div>
        <div><label htmlFor="register-email" className="mb-1.5 block text-sm font-medium">邮箱</label><input id="register-email" type="email" value={form.email} onChange={change('email')} required className="input h-11"/>{field('email')}</div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div><label htmlFor="register-password" className="mb-1.5 block text-sm font-medium">密码</label><input id="register-password" type="password" minLength={8} maxLength={72} value={form.password} onChange={change('password')} required className="input h-11"/>{field('password')}</div>
          <div><label htmlFor="confirm-password" className="mb-1.5 block text-sm font-medium">确认密码</label><input id="confirm-password" type="password" value={form.confirmPassword} onChange={change('confirmPassword')} required className="input h-11"/>{field('confirmPassword')}</div>
        </div>
        {error && <div role="alert" className="flex gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700"><CircleAlert size={16}/>{error}</div>}
        <button disabled={submitting} className="flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-brand-600 font-semibold text-white disabled:opacity-60">{submitting && <Loader2 size={16} className="animate-spin"/>}{submitting ? '注册中…' : '注册并登录'}</button>
      </form>
    </AuthLayout>
  )
}
