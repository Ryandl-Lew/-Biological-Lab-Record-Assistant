import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, CheckCheck, Menu } from 'lucide-react'
import {
  acceptInvitation,
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  rejectInvitation,
} from '@/api'

const POLL_INTERVAL_MS = 20_000

export default function TopbarMvp({ onMenuClick }) {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState([])
  const [unread, setUnread] = useState(0)
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)

  const refreshUnread = useCallback(async () => {
    try {
      const result = await fetchUnreadCount()
      setUnread(result.count)
    } catch (requestError) {
      setError(requestError.message)
    }
  }, [])

  const loadNotifications = useCallback(async (nextPage = 0, append = false) => {
    setLoading(true)
    setError('')
    try {
      const result = await fetchNotifications({ page: nextPage, size: 20 })
      setItems((current) => (append ? [...current, ...result.items] : result.items))
      setPage(nextPage)
      setHasMore(nextPage + 1 < result.meta.totalPages)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refreshUnread()
    const intervalId = window.setInterval(refreshUnread, POLL_INTERVAL_MS)
    const handleFocus = () => refreshUnread()
    const handleNotificationsChanged = () => {
      refreshUnread()
      if (open) loadNotifications(0, false)
    }
    window.addEventListener('focus', handleFocus)
    window.addEventListener('bionote:notifications-changed', handleNotificationsChanged)
    return () => {
      window.clearInterval(intervalId)
      window.removeEventListener('focus', handleFocus)
      window.removeEventListener('bionote:notifications-changed', handleNotificationsChanged)
    }
  }, [loadNotifications, open, refreshUnread])

  const toggle = () => {
    const next = !open
    setOpen(next)
    if (next) loadNotifications(0, false)
  }

  const markReadLocally = (id) => {
    setItems((current) => current.map((item) => (
      item.id === id && !item.readAt ? { ...item, readAt: new Date().toISOString() } : item
    )))
    setUnread((current) => Math.max(0, current - 1))
  }

  const openNotification = async (item) => {
    setError('')
    try {
      if (!item.readAt) {
        await markNotificationRead(item.id)
        markReadLocally(item.id)
      }
      if (item.stale || item.target?.type === 'NONE') {
        setError('该任务已处理或不可访问')
        return
      }
      if (item.target?.type === 'PROJECT') navigate(`/projects/${item.target.id}`)
      if (item.target?.type === 'RECORD') navigate(`/records/${item.target.id}`)
      setOpen(false)
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  const handleInvitation = async (item, action) => {
    if (item.stale || !item.actions?.includes(action)) {
      setError('该邀请已处理或不可访问')
      return
    }
    setBusyId(item.id)
    setError('')
    try {
      if (action === 'ACCEPT') await acceptInvitation(item.target.id)
      else await rejectInvitation(item.target.id)
      if (!item.readAt) await markNotificationRead(item.id)
      await Promise.all([loadNotifications(0, false), refreshUnread()])
      window.dispatchEvent(new Event('bionote:projects-changed'))
    } catch (requestError) {
      setError(requestError.message)
      await loadNotifications(0, false)
    } finally {
      setBusyId(null)
    }
  }

  const markAllRead = async () => {
    setLoading(true)
    setError('')
    try {
      await markAllNotificationsRead()
      setUnread(0)
      setItems((current) => current.map((item) => ({
        ...item,
        readAt: item.readAt || new Date().toISOString(),
      })))
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center border-b border-slate-200 bg-white/80 px-4 backdrop-blur sm:px-6 lg:px-8">
      <button onClick={onMenuClick} aria-label="打开导航菜单" className="rounded-lg p-2 text-slate-500 lg:hidden"><Menu size={20} /></button>
      <div className="relative ml-auto">
        <button aria-label={`通知，${unread} 条未读`} aria-expanded={open} onClick={toggle} className="relative rounded-lg p-2.5 text-slate-500 hover:bg-slate-100">
          <Bell size={18} />
          {unread > 0 && <span className="absolute -right-0.5 -top-0.5 rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">{unread}</span>}
        </button>
        {open && (
          <div className="absolute right-0 top-12 z-50 w-[380px] max-w-[calc(100vw-2rem)] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-pop">
            <div className="flex items-center justify-between border-b p-4">
              <div><h2 className="text-sm font-semibold">通知</h2><p className="text-xs text-slate-400">{unread} 条未读</p></div>
              <button disabled={loading || unread === 0} onClick={markAllRead} className="inline-flex items-center gap-1 text-xs text-brand-600 disabled:text-slate-300"><CheckCheck size={14} />全部已读</button>
            </div>
            {error && <p role="alert" className="border-b bg-red-50 p-3 text-sm text-red-700">{error}</p>}
            <div className="max-h-[420px] divide-y overflow-y-auto">
              {loading && items.length === 0 && <p className="p-8 text-center text-sm text-slate-400">加载通知中…</p>}
              {!loading && items.length === 0 && !error && <p className="p-8 text-center text-sm text-slate-400">暂无通知</p>}
              {items.map((item) => (
                <div key={item.id} className={`p-4 ${item.readAt ? '' : 'bg-brand-50/40'}`}>
                  <button onClick={() => openNotification(item)} className="w-full text-left">
                    <span className="text-sm font-medium">{item.title}</span>
                    <span className="mt-1 block text-xs text-slate-500">{item.body}</span>
                    <span className="mt-1 block text-[11px] text-slate-400">{new Date(item.createdAt).toLocaleString()}</span>
                  </button>
                  {item.type === 'PROJECT_INVITATION' && (item.stale || item.actions?.length === 0 ? (
                    <p className="mt-2 text-xs text-slate-400">已处理或不可访问</p>
                  ) : (
                    <div className="mt-3 flex gap-2">
                      <button disabled={busyId === item.id} onClick={() => handleInvitation(item, 'ACCEPT')} className="rounded-md bg-brand-600 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50">接受</button>
                      <button disabled={busyId === item.id} onClick={() => handleInvitation(item, 'REJECT')} className="rounded-md border px-3 py-1.5 text-xs disabled:opacity-50">拒绝</button>
                    </div>
                  ))}
                </div>
              ))}
              {hasMore && <button disabled={loading} onClick={() => loadNotifications(page + 1, true)} className="w-full p-3 text-center text-xs font-medium text-brand-600 disabled:text-slate-300">{loading ? '加载中…' : '查看更多'}</button>}
            </div>
          </div>
        )}
      </div>
    </header>
  )
}
