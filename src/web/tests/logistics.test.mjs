import test from 'node:test'
import assert from 'node:assert/strict'
import {
  autoConfirmText,
  latestFirst,
  logisticsState,
  validateShipForm,
} from '../src/utils/logistics.js'
import { EXPRESS_COMPANIES, LOGISTICS_STATUS_MAP } from '../src/constants/status.js'

const CODES = EXPRESS_COMPANIES.map((item) => item.code)

test('track timeline is rendered newest first without mutating the source array', () => {
  const source = [
    { trackId: 'T1', occurredAt: '2026-11-13 10:00:00' },
    { trackId: 'T2', occurredAt: '2026-11-14 08:30:00' },
    { trackId: 'T3', occurredAt: '2026-11-15 09:00:00' },
  ]
  const view = latestFirst(source)

  assert.deepEqual(view.map((t) => t.trackId), ['T3', 'T2', 'T1'])
  // 原数组必须保持后端返回的正序，避免同一份数据在别处渲染时顺序反转
  assert.deepEqual(source.map((t) => t.trackId), ['T1', 'T2', 'T3'])
})

test('unshipped and shipped-without-track states stay distinct', () => {
  // 未发货：提示「尚未发货」，不能显示成「物流更新中」
  assert.equal(logisticsState({ shipped: false, tracks: [] }), 'pending')
  // 已发货但没轨迹：提示「物流更新中」，不能显示成「暂无数据」或错误
  assert.equal(logisticsState({ shipped: true, tracks: [] }), 'empty')
  // 有轨迹
  assert.equal(logisticsState({ shipped: true, tracks: [{ trackId: 'T1' }] }), 'ready')
})

test('loading and failure take precedence over empty data', () => {
  assert.equal(logisticsState({ loading: true, shipped: true, tracks: [] }), 'loading')
  assert.equal(logisticsState({ error: '网络异常', shipped: true, tracks: [] }), 'error')
  // 失败时即使有旧数据也不能当成 ready，否则会掩盖错误
  assert.equal(logisticsState({ error: '网络异常', shipped: true, tracks: [{}] }), 'error')
})

test('defensive input never throws and never fabricates tracks', () => {
  assert.deepEqual(latestFirst(undefined), [])
  assert.equal(logisticsState(), 'pending')
})

test('countdown degrades from days to hours to minutes', () => {
  assert.equal(autoConfirmText(14400), '10 天')
  assert.equal(autoConfirmText(1440), '1 天')
  assert.equal(autoConfirmText(180), '3 小时')
  assert.equal(autoConfirmText(60), '1 小时')
  assert.equal(autoConfirmText(1), '1 分钟')
  // 到期时不能显示 0，要说清楚「不到 1 分钟」
  assert.equal(autoConfirmText(0), '不到 1 分钟')
  // 不该显示倒计时的情况返回空串，调用方用 v-if 隐藏
  assert.equal(autoConfirmText(null), '')
  assert.equal(autoConfirmText(undefined), '')
  assert.equal(autoConfirmText('abc'), '')
})

test('countdown keeps the sub-unit so 14340 minutes is not shown as 9 days', () => {
  // 14340 = 9 天 23 小时；只取整到天会少算近一天，刚发货就跳成「9 天」
  assert.equal(autoConfirmText(14340), '9 天 23 小时')
  assert.equal(autoConfirmText(1441), '1 天')
  assert.equal(autoConfirmText(90), '1 小时 30 分钟')
  // 次级单位为 0 时省略，不写「10 天 0 小时」
  assert.equal(autoConfirmText(2880), '2 天')
})

test('ship form requires both an enumerated company and a tracking number', () => {
  assert.match(validateShipForm({ expressCompany: '', trackingNo: 'SF1' }, CODES), /快递公司/)
  assert.match(validateShipForm({ expressCompany: 'SF', trackingNo: '  ' }, CODES), /物流单号/)
  // 自由文本（例如手打「顺丰」）必须被拦住，否则后端只能返回 10001
  assert.match(validateShipForm({ expressCompany: '顺丰', trackingNo: 'SF1' }, CODES), /范围/)
  assert.match(validateShipForm({ expressCompany: 'SF', trackingNo: 'x'.repeat(65) }, CODES), /64/)
  assert.equal(validateShipForm({ expressCompany: 'SF', trackingNo: ' SF123 ' }, CODES), '')
})

test('every express company code has a non-empty label and is unique', () => {
  assert.ok(EXPRESS_COMPANIES.length >= 8)
  const codes = new Set()
  for (const item of EXPRESS_COMPANIES) {
    assert.ok(item.code && item.code === item.code.toUpperCase())
    assert.ok(item.label && item.label.length > 0)
    assert.ok(!codes.has(item.code), `重复的快递公司编码 ${item.code}`)
    codes.add(item.code)
  }
})

test('track status map covers exactly the five statuses from the issue', () => {
  assert.deepEqual(
    Object.keys(LOGISTICS_STATUS_MAP).sort(),
    ['COLLECTED', 'EXCEPTION', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'SIGNED'],
  )
})
