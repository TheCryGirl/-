/**
 * 搜索历史：存在 localStorage，最多保留 8 条，最近搜索排在最前。
 *
 * 只保存关键词本身，不涉及账号信息；推荐模块和搜索页都用它作为来源。
 */
const KEY = 'mall.searchHistory'
const MAX = 8

export function readSearchHistory() {
  try {
    const raw = JSON.parse(localStorage.getItem(KEY) || '[]')
    if (!Array.isArray(raw)) return []
    return raw.filter((item) => typeof item === 'string' && item.trim()).slice(0, MAX)
  } catch {
    return []
  }
}

export function pushSearchHistory(keyword) {
  const value = String(keyword || '').trim()
  if (!value) return readSearchHistory()
  const list = readSearchHistory().filter((item) => item !== value)
  list.unshift(value)
  const next = list.slice(0, MAX)
  try {
    localStorage.setItem(KEY, JSON.stringify(next))
  } catch {
    // 存储被禁用时忽略，不影响搜索本身
  }
  return next
}

export function clearSearchHistory() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // 忽略
  }
  return []
}