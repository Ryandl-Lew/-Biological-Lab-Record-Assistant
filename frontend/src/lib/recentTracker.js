/**
 * recentTracker —— 最近访问记录与 walkthrough 手动完成态
 * ----------------------------------------------------------------------------
 * 用 localStorage 持久化两类轻量数据，供欢迎页 (Dashboard) 使用：
 *   1) visits:     记录/项目的最近访问历史（去重 + 上限 12 条 + 7 天清理）
 *   2) manualDone: 用户手动标记完成的 walkthrough key
 *
 * 不依赖后端接口，纯客户端行为；不影响其他模块。
 */
import { fetchProject, fetchRecord } from '@/api'

const STORAGE_KEY = 'bionote:recent-visits'
const MAX_VISITS = 12
const TTL_MS = 7 * 24 * 60 * 60 * 1000 // 7 天

function read() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { visits: [] }
    const parsed = JSON.parse(raw)
    return { visits: Array.isArray(parsed.visits) ? parsed.visits : [] }
  } catch {
    return { visits: [] }
  }
}

function write(state) {
  try {
    const trimmed = prune(state)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed))
  } catch {
    // 隐私模式或额度超限，静默失败
  }
}

function prune(state) {
  const cutoff = Date.now() - TTL_MS
  const visits = (state.visits || [])
    .filter((v) => v && v.visitedAt && new Date(v.visitedAt).getTime() >= cutoff)
    .slice(0, MAX_VISITS)
  return { visits }
}

/**
 * 记录一次访问。同 id 项会被提到顶部；超过上限自动裁剪。
 * @param {{type:'record'|'project', id:string, title:string, subTitle?:string}} entry
 */
export function recordVisit(entry) {
  if (!entry || !entry.id || !entry.type || !entry.title) return
  const state = read()
  const visitedAt = new Date().toISOString()
  const rest = state.visits.filter((v) => v.id !== entry.id || v.type !== entry.type)
  state.visits = [{ ...entry, visitedAt }, ...rest]
  write(state)
}

/**
 * 读取最近 N 条访问记录（默认 8）。
 * @returns {Array<{type:string,id:string,title:string,subTitle?:string,visitedAt:string}>}
 */
export function getRecent(n = 8) {
  return read().visits.slice(0, n)
}

export function clearRecent() {
  write({ visits: [] })
}

/**
 * 供测试使用的重置工具（不对外暴露语义）
 */
export function __resetRecentTracker() {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* noop */
  }
}

/**
 * 根据路径自动判断并记录一次访问。
 * 在 AppLayout 中监听 useLocation 变化时调用。
 * @param {string} pathname
 */
export async function trackVisitByPath(pathname) {
  if (!pathname) return

  // /records/:id 或 /records/:id/edit
  let m = pathname.match(/^\/records\/([^/]+?)(?:\/edit)?$/)
  if (m && m[1] !== 'new') {
    try {
      const record = await fetchRecord(m[1])
      recordVisit({
        type: 'record',
        id: record.id,
        title: record.title,
        subTitle: record.projectName,
      })
    } catch {
      /* 记录可能被删除，静默 */
    }
    return
  }

  // /projects/:id 可带 query (?tab=...)
  m = pathname.match(/^\/projects\/([^/]+)$/)
  if (m) {
    try {
      const project = await fetchProject(m[1])
      recordVisit({
        type: 'project',
        id: project.id,
        title: project.name,
        subTitle: project.status,
      })
    } catch {
      /* 项目可能被归档，静默 */
    }
  }
}
