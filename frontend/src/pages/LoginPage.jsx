import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Mail, Lock, Eye, EyeOff, CircleAlert, Loader2 } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import AuthLayout from '@/components/auth/AuthLayout'

export default function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const login = useAuthStore((s) => s.login)
  const currentUser = useAuthStore((s) => s.currentUser)
  const loading = useAuthStore((s) => s.loading)
  const navigate = useNavigate()
  const location = useLocation()

  if (!loading && currentUser) return <Navigate to="/" replace />

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    if (!email.trim() || !password) { setError('请输入邮箱和密码'); return }
    setSubmitting(true)
    try {
      await login({ email, password })
      navigate(location.state?.from || '/', { replace: true })
    } catch (requestError) {
      setError(requestError.message || '登录失败，请重试')
    } finally { setSubmitting(false) }
  }

  return (
    <AuthLayout title="欢迎回来" subtitle="使用邮箱登录你的 BioNote 账号" footer={<span>还没有账号？ <Link to="/register" className="font-semibold text-brand-600">立即注册</Link></span>}>
      <form className="space-y-5" onSubmit={handleSubmit}>
        <div>
          <label htmlFor="email" className="mb-1.5 block text-sm font-medium text-slate-700">邮箱</label>
          <div className="relative"><Mail size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" autoFocus className="input h-11 pl-10" />
          </div>
        </div>
        <div>
          <label htmlFor="password" className="mb-1.5 block text-sm font-medium text-slate-700">密码</label>
          <div className="relative"><Lock size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input id="password" type={showPassword ? 'text' : 'password'} value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" className="input h-11 pl-10 pr-11" />
            <button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? '隐藏密码' : '显示密码'} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400">{showPassword ? <EyeOff size={16}/> : <Eye size={16}/>}</button>
          </div>
        </div>
        {error && <div role="alert" className="flex gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700"><CircleAlert size={16}/>{error}</div>}
        <button type="submit" disabled={submitting} className="flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-brand-600 font-semibold text-white disabled:opacity-60">{submitting && <Loader2 size={16} className="animate-spin"/>}{submitting ? '登录中…' : '登录'}</button>
      </form>
    </AuthLayout>
  )
}
