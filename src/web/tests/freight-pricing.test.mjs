import test from 'node:test'
import assert from 'node:assert/strict'
import { buildSummary, summaryRows, toCents, toYuan } from '../src/utils/pricing.js'

// Issue #136：运费与优惠计价。前端只做元/分换算与四行展示，
// 应付金额一律取后端预览结果，不在前端重算运费与优惠。

test('yuan input converts to integer cents without floating point drift', () => {
  assert.equal(toCents('19.99'), 1999)
  assert.equal(toCents(0.1 + 0.2), 30)
  assert.equal(toCents(''), 0)
  assert.equal(toCents(-5), 0)
  assert.equal(toYuan(1999), '19.99')
})

test('summary keeps the backend chain: pay = goods - discount + freight', () => {
  const summary = buildSummary({ totalAmount: 10000, freightAmount: 600, discountAmount: 500, payAmount: 10100 })
  assert.deepEqual(summary, { goods: 10000, freight: 600, discount: 500, pay: 10100 })
  assert.equal(summary.goods - summary.discount + summary.freight, summary.pay)
})

test('missing preview falls back to goods amount with zero freight and discount', () => {
  const summary = buildSummary(null, 8800)
  assert.deepEqual(summary, { goods: 8800, freight: 0, discount: 0, pay: 8800 })
})

test('summary rows are fixed four lines in contract order', () => {
  const rows = summaryRows({ goods: 10000, freight: 600, discount: 500, pay: 10100 })
  assert.deepEqual(rows.map((r) => r.key), ['goods', 'freight', 'discount', 'pay'])
  assert.deepEqual(rows.map((r) => r.label), ['商品金额', '运费', '优惠', '应付合计'])
  assert.equal(rows.at(-1).amount, 10100)
})

test('free shipping above threshold is reflected as zero freight', () => {
  const summary = buildSummary({ totalAmount: 12000, freightAmount: 0, discountAmount: 0, payAmount: 12000 })
  assert.equal(summary.freight, 0)
  assert.equal(summary.pay, 12000)
})
