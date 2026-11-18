/**
 * 结算单展示口径（#144 第二块），卖家页与管理页共用。
 *
 * 金额由后端按分返回，这里只负责周期、状态的纯文本/标签映射，
 * 便于 node 测试直接覆盖。
 */
export const SETTLEMENT_STATUS_TEXT = {
  PENDING: '待结算',
  CONFIRMED: '已确认',
  PAID: '已打款',
}

export const SETTLEMENT_STATUS_TAG = {
  PENDING: 'warning',
  CONFIRMED: 'primary',
  PAID: 'success',
}

export function settlementStatusText(status) {
  return SETTLEMENT_STATUS_TEXT[status] || status || '—'
}

export function settlementStatusTag(status) {
  return SETTLEMENT_STATUS_TAG[status] || 'info'
}

/** "2026-11-01T00:00:00" / "2026-11-01 00:00:00" → "2026-11" */
export function formatSettlementPeriod(periodStart) {
  if (!periodStart) return '—'
  const head = String(periodStart).replace('T', ' ').slice(0, 7)
  return /^\d{4}-\d{2}$/.test(head) ? head : '—'
}

/** 当前自然月周期，形如 "2026-11" */
export function currentMonthPeriod() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

export function isValidPeriod(value) {
  return /^\d{4}-(0[1-9]|1[0-2])$/.test(String(value || '').trim())
}

export function isSettlementPaid(status) {
  return status === 'PAID'
}
