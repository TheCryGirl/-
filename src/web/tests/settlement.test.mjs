import test from 'node:test'
import assert from 'node:assert/strict'
import {
  currentMonthPeriod,
  formatSettlementPeriod,
  isSettlementPaid,
  isValidPeriod,
  settlementStatusTag,
  settlementStatusText,
} from '../src/utils/settlement.js'

test('周期格式化为 yyyy-MM，非法或缺失显示 —', () => {
  assert.equal(formatSettlementPeriod('2026-11-01T00:00:00'), '2026-11')
  assert.equal(formatSettlementPeriod('2026-11-01 00:00:00'), '2026-11')
  assert.equal(formatSettlementPeriod(null), '—')
  assert.equal(formatSettlementPeriod(''), '—')
  assert.equal(formatSettlementPeriod('不是时间'), '—')
})

test('周期输入校验只认 yyyy-MM，月份范围 01-12', () => {
  assert.equal(isValidPeriod('2026-11'), true)
  assert.equal(isValidPeriod(' 2026-11 '), true)
  assert.equal(isValidPeriod('2026-13'), false)
  assert.equal(isValidPeriod('2026-1'), false)
  assert.equal(isValidPeriod('abc'), false)
  assert.equal(isValidPeriod(''), false)
  assert.equal(isValidPeriod(null), false)
})

test('状态文案与标签映射完整，未知值兜底', () => {
  assert.equal(settlementStatusText('PENDING'), '待结算')
  assert.equal(settlementStatusText('CONFIRMED'), '已确认')
  assert.equal(settlementStatusText('PAID'), '已打款')
  assert.equal(settlementStatusTag('PENDING'), 'warning')
  assert.equal(settlementStatusTag('CONFIRMED'), 'primary')
  assert.equal(settlementStatusTag('PAID'), 'success')
  assert.equal(settlementStatusText('UNKNOWN'), 'UNKNOWN')
  assert.equal(settlementStatusText(null), '—')
  assert.equal(settlementStatusTag('UNKNOWN'), 'info')
})

test('已打款判定只认 PAID', () => {
  assert.equal(isSettlementPaid('PAID'), true)
  assert.equal(isSettlementPaid('PENDING'), false)
  assert.equal(isSettlementPaid('CONFIRMED'), false)
  assert.equal(isSettlementPaid(null), false)
})

test('当前月周期格式为 yyyy-MM', () => {
  const period = currentMonthPeriod()
  assert.equal(isValidPeriod(period), true)
  const now = new Date()
  assert.equal(period, `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
})
