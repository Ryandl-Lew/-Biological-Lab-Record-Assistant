/**
 * useRecentTracker —— 监听路由变化并把访问记录写入 recentTracker。
 * 在 AppLayout 顶层调用，仅产生副作用，不返回任何值。
 */
import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { trackVisitByPath } from '@/lib/recentTracker'

export default function useRecentTracker() {
  const location = useLocation()
  useEffect(() => {
    // 异步拉取 title 后写入；忽略被删除/归档导致的错误
    trackVisitByPath(location.pathname).catch(() => {})
  }, [location.pathname])
}