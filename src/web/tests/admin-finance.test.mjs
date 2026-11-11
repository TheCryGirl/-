import test from 'node:test'
import assert from 'node:assert/strict'
import { combineFinanceSummaries, pageCoverage } from '../src/utils/adminFinance.js'

test('uses server-wide totals rather than administrator personal order arrays', () => {
  const result = combineFinanceSummaries(
    { gmv: 250000, orderCount: 151, paidCount: 120, paidAmount: 200000, cancelledCount: 5,
      byStatus: [{ status: 'PAID', count: 120 }], daily: [{ day: '2026-11-09', count: 120, amount: 200000 }] },
    { refundedCount: 105, refundedAmount: 50000, pendingRefundAmount: 300,
      daily: [{ day: '2026-11-10', refund: 50000 }] },
  )
  assert.equal(result.stats.orderCount, 151)
  assert.equal(result.stats.netAmount, 150000)
  assert.deepEqual(result.daily, [
    { day: '2026-11-10', count: 0, amount: 0, refund: 50000, net: -50000 },
    { day: '2026-11-09', count: 120, amount: 200000, refund: 0, net: 200000 },
  ])
  assert.equal(result.byStatus.PAID, 120)
})

test('merges receipts and refunds on the same business day without replacing either', () => {
  const out = combineFinanceSummaries(
    { gmv: 1000, orderCount: 1, paidCount: 1, paidAmount: 1000, cancelledCount: 0,
      byStatus: [{ status: 'COMPLETED', count: 1 }], daily: [{ day: '2026-11-09', count: 1, amount: 1000 }] },
    { refundedCount: 1, refundedAmount: 200, pendingRefundAmount: 0, daily: [{ day: '2026-11-09', refund: 200 }] },
  )
  assert.equal(out.daily.length, 1)
  assert.equal(out.daily[0].net, 800)
})

test('missing or malformed summary fails explicitly rather than showing a zero platform balance', () => {
  assert.throws(() => combineFinanceSummaries(null, {}), /汇总/)
  assert.throws(() => combineFinanceSummaries({ gmv: 'not-a-number' }, {}), /汇总/)
})

test('risk coverage names sampled data instead of implying a full scan', () => {
  assert.deepEqual(pageCoverage({ total: 300, list: [{}, {}] }), { loaded: 2, total: 300, complete: false })
  assert.deepEqual(pageCoverage({ total: 2, list: [{}, {}] }), { loaded: 2, total: 2, complete: true })
  assert.throws(() => pageCoverage({ list: [] }), /覆盖/)
})
