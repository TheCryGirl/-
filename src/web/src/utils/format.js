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

/** 金额以分为单位，页面展示为元。 */
export function formatPrice(cents) {
  return ((cents || 0) / 100).toFixed(2)
}

/**
 * 列表顶部的总量文案。
 *
 * 背景（Issue #95）：部分列表只取前 50 条且没有翻页入口，但顶部只写「共 N 件」，
 * 用户会以为全部可见。这里统一改成如实标注可见范围，避免误导。
 *
 * 量词规范：商品用「件」、订单与售后用「单」、退款用「笔」、通用记录用「条」。
 *
 * @param {number} total 后端返回的总量
 * @param {string} unit 量词，如「件」「单」「笔」「条」
 * @param {number} shown 当前实际展示的条数
 * @returns {string} 例如「共 128 件，当前显示前 50 件」或「共 32 件」
 */
export function formatTotal(total, unit = '条', shown) {
  const n = Number(total) || 0
  // shown 未传时不作判断，只显示总量（调用方应传 list.length）
  if (shown === undefined || shown === null) return `共 ${n} ${unit}`
  const s = Number(shown) || 0
  // 全部可见时不必画蛇添足
  if (s >= n) return `共 ${n} ${unit}`
  return `共 ${n} ${unit}，当前显示前 ${s} ${unit}`
}
