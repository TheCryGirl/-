/**
 * 列表里的时间展示。
 *
 * 后端返回形如 2026-10-28T20:00:00 的字符串，页面统一展示到分钟，
 * 避免表格和卡片里出现长长的 ISO 串。
 */
export function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}
