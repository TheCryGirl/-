/**
 * 跨店合并结算的订单列表聚合（#135）。
 *
 * 买家视角：同一 mainOrderId 的子订单聚合成一个主订单行，可展开看子订单；
 * 历史单店订单（mainOrderId 为 null）保持逐单展示。卖家视角不聚合（按店发货）。
 *
 * 金额只做展示层求和（各子订单 payAmount 之和），与主订单 payAmount 一致；
 * 应付金额一律以服务端为准，本模块不参与下单计价。
 */

/**
 * @param {Array} orders 后端返回的子订单/单店订单列表
 * @param {string} role buyer | seller
 * @returns {Array} 展示行：{ kind:'main', mainOrderId, mainOrderNo, createdAt, subs, payAmount, itemCount, status }
 *                  或 { kind:'order', order }
 */
export function groupOrderRows(orders, role = 'buyer') {
  const list = Array.isArray(orders) ? orders : []
  if (role !== 'buyer') {
    return list.map((o) => ({ kind: 'order', order: o }))
  }
  const rows = []
  const mainIndex = new Map()
  for (const o of list) {
    if (!o || !o.mainOrderId) {
      rows.push({ kind: 'order', order: o })
      continue
    }
    if (!mainIndex.has(o.mainOrderId)) {
      const group = {
        kind: 'main',
        mainOrderId: o.mainOrderId,
        mainOrderNo: o.mainOrderNo || o.mainOrderId,
        createdAt: o.createdAt,
        subs: [],
      }
      mainIndex.set(o.mainOrderId, group)
      rows.push(group)
    }
    mainIndex.get(o.mainOrderId).subs.push(o)
  }
  for (const row of rows) {
    if (row.kind !== 'main') continue
    row.payAmount = row.subs.reduce((sum, s) => sum + (Number(s.payAmount) || 0), 0)
    row.itemCount = row.subs.reduce((sum, s) => sum + (s.items?.length || 0), 0)
    const statuses = row.subs.map((s) => s.status)
    row.status = statuses.includes('PENDING_PAYMENT')
      ? 'PENDING_PAYMENT'
      : statuses.every((s) => s === 'CANCELLED')
        ? 'CANCELLED'
        : statuses.find((s) => s !== 'CANCELLED') || statuses[0]
  }
  return rows
}