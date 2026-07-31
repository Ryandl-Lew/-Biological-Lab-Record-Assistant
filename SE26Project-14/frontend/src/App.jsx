/**
 * 应用根组件：挂载路由。
 */
import { useEffect } from 'react'
import { RouterProvider } from 'react-router-dom'
import { router } from '@/router'
import { useAuthStore } from '@/store/authStore'

export default function App() {
  const restoreSession = useAuthStore((s) => s.restoreSession)
  const clearSession = useAuthStore((s) => s.clearSession)

  useEffect(() => {
    restoreSession()
  }, [restoreSession])

  useEffect(() => {
    window.addEventListener('bionote:unauthorized', clearSession)
    return () => window.removeEventListener('bionote:unauthorized', clearSession)
  }, [clearSession])

  return <RouterProvider router={router} />
}
