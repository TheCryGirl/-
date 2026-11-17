import test from 'node:test'
import assert from 'node:assert/strict'
import { groupOrderRows } from '../src/utils/orderGroup.js'

// Issue #135：买家订单列表按主订单聚合，历史单店订单保持逐单展示

const subA = {
  orderId: 'O-A', orderNo: 'NO-A', status: 'PENDING_PAYMENT', payAmount: 5000,
  createdAt: '2026-11-17 16:00:00', mainOrderId: 'M-1', mainOrderNo: 'MNO-1',
  items: [{ skuId: 'S1' }],
}
const subB = {
  orderId: 'O-B', orderNo: 'NO-B', status: 'PENDING_PAYMENT', payAmount: 14000,
  createdAt: '2026-11-17 16:00:00', mainOrderId: 'M-1', mainOrderNo: 'MNO-1',
  items: [{ skuId: 'S2' }, { skuId: 'S3' }],
}
const legacy = {
  orderId: 'O-LEGACY', orderNo: 'NO-LEGACY', status: 'PAID', payAmount: 9900,
  createdAt: '2026-10-01 10:00:00', mainOrderId: null, items: [{ skuId: 'S9' }],
}

test('buyer view groups sub-orders of the same main order into one row', () => {
  const rows = groupOrderRows([subA, subB, legacy], 'buyer')
  assert.equal(rows.length, 2)
  const main = rows.find((r) => r.kind === 'main')
  assert.equal(main.mainOrderId, 'M-1')
  assert.equal(main.mainOrderNo, 'MNO-1')
  assert.equal(main.subs.length, 2)
  assert.equal(main.payAmount, 19000)
  assert.equal(main.itemCount, 3)
  assert.equal(main.status, 'PENDING_PAYMENT')
  const single = rows.find((r) => r.kind === 'order')
  assert.equal(single.order.orderId, 'O-LEGACY')
})

test('legacy orders with null mainOrderId stay as individual rows', () => {
  const rows = groupOrderRows([legacy], 'buyer')
  assert.equal(rows.length, 1)
  assert.equal(rows[0].kind, 'order')
  assert.equal(rows[0].order.orderId, 'O-LEGACY')
})

test('seller view never groups because fulfilment is per shop', () => {
  const rows = groupOrderRows([subA, subB], 'seller')
  assert.equal(rows.length, 2)
  assert.ok(rows.every((r) => r.kind === 'order'))
})

test('main row stays PENDING_PAYMENT while any sub-order is unpaid', () => {
  const paidB = { ...subB, status: 'PAID' }
  const rows = groupOrderRows([subA, paidB], 'buyer')
  assert.equal(rows[0].status, 'PENDING_PAYMENT')
})

test('main row is CANCELLED only when every sub-order is cancelled', () => {
  const rows = groupOrderRows(
    [{ ...subA, status: 'CANCELLED' }, { ...subB, status: 'CANCELLED' }],
    'buyer',
  )
  assert.equal(rows[0].status, 'CANCELLED')
  const mixed = groupOrderRows(
    [{ ...subA, status: 'CANCELLED' }, { ...subB, status: 'PAID' }],
    'buyer',
  )
  assert.equal(mixed[0].status, 'PAID')
})

test('empty or invalid input yields no rows', () => {
  assert.deepEqual(groupOrderRows([], 'buyer'), [])
  assert.deepEqual(groupOrderRows(null, 'buyer'), [])
})