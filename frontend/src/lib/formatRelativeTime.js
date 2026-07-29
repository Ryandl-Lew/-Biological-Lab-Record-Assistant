/**
 * formatRelativeTime —— 将 ISO 时间字符串格式化为"3 小时前 / 1 天前"等相对时间。
 * @param {string|number|Date} input
 * @returns {string}
 */
export default function formatRelativeTime(input) {
  if (!input) return ''
  const date = input instanceof Date ? input : new Date(input)
  const diff = Date.now() - date.getTime()
  if (Number.isNaN(diff)) return ''
  const sec = Math.round(diff / 1000)
  if (sec < 60) return '刚刚'
  const min = Math.round(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const hr = Math.round(min / 60)
  if (hr < 24) return `${hr} 小时前`
  const day = Math.round(hr / 24)
  if (day < 30) return `${day} 天前`
  const month = Math.round(day / 30)
  if (month < 12) return `${month} 个月前`
  const year = Math.round(month / 12)
  return `${year} 年前`
}