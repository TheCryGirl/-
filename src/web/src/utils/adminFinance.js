function numberField(value) {
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new Error('资金汇总响应字段不完整')
  return value
}

/** Merge two full SQL aggregates. No client-side page is used as a platform total. */
export function combineFinanceSummaries(order, refund) {
  if (!order || !refund || !Array.isArray(order.daily) || !Array.isArray(refund.daily)
      || !Array.isArray(order.byStatus)) throw new Error('资金汇总响应不完整')
  const stats = {}
  for (const key of ['gmv', 'orderCount', 'paidCount', 'paidAmount', 'cancelledCount']) {
    stats[key] = numberField(order[key])
  }
  for (const key of ['refundedCount', 'refundedAmount', 'pendingRefundAmount']) {
    stats[key] = numberField(refund[key])
  }
  stats.netAmount = stats.paidAmount - stats.refundedAmount
  const byStatus = Object.fromEntries(order.byStatus.map((row) => [row.status, numberField(row.count)]))
  const days = new Map()
  function dayRow(day) {
    if (typeof day !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(day)) throw new Error('资金汇总日期格式错误')
    if (!days.has(day)) days.set(day, { day, count: 0, amount: 0, refund: 0 })
    return days.get(day)
  }
  for (const row of order.daily) {
    const day = dayRow(row.day)
    day.count += numberField(row.count)
    day.amount += numberField(row.amount)
  }
  for (const row of refund.daily) dayRow(row.day).refund += numberField(row.refund)
  const daily = [...days.values()].map((row) => ({ ...row, net: row.amount - row.refund }))
    .sort((a, b) => b.day.localeCompare(a.day))
  return { stats, byStatus, daily }
}

/** A risk sample must report the server total, not just its returned length. */
export function pageCoverage(payload) {
  if (!payload || !Array.isArray(payload.list) || !Number.isSafeInteger(payload.total) || payload.total < 0) {
    throw new Error('风控覆盖范围响应不完整')
  }
  return { loaded: payload.list.length, total: payload.total, complete: payload.list.length === payload.total }
}
