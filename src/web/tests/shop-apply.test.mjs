import test from 'node:test'
import assert from 'node:assert/strict'
import {
  applyStatusTag,
  applyStatusText,
  canEditShopInfo,
  depositText,
  publishBlockReason,
  subjectTypeOptions,
  subjectTypeText,
} from '../src/utils/shopApply.js'

test('未申请与审核中都拦住发布入口，且不允许直接改资料', () => {
  const notApplied = { applyStatus: null, depositPaid: false, publishable: false }
  assert.equal(applyStatusText(notApplied.applyStatus), '未申请')
  assert.match(publishBlockReason(notApplied), /入驻申请/)
  assert.equal(canEditShopInfo(notApplied), false)

  const pending = { applyStatus: 'PENDING', depositPaid: false, publishable: false }
  assert.equal(applyStatusText(pending.applyStatus), '待审核')
  assert.equal(applyStatusTag(pending.applyStatus), 'warning')
  assert.match(publishBlockReason(pending), /审核中/)
  assert.equal(canEditShopInfo(pending), false)
})

test('驳回理由回传给卖家，重新提交前仍拦住发布', () => {
  const rejected = { applyStatus: 'REJECTED', depositPaid: false, publishable: false, rejectReason: '资质材料不清晰' }
  assert.equal(applyStatusTag(rejected.applyStatus), 'danger')
  assert.equal(publishBlockReason(rejected), '入驻审核已驳回：资质材料不清晰，请修改资质后重新提交')

  const noReason = { applyStatus: 'REJECTED', depositPaid: false, publishable: false }
  assert.match(publishBlockReason(noReason), /修改资质/)
})

test('审核通过即放行，保证金仅作记录不拦截发布', () => {
  const noDeposit = { applyStatus: 'APPROVED', depositPaid: false, depositAmount: 0, publishable: true }
  assert.equal(publishBlockReason(noDeposit), '')
  assert.equal(depositText(noDeposit.depositAmount), '保证金未缴纳')

  const legacy = { applyStatus: 'APPROVED', depositPaid: false, depositAmount: 0, publishable: true }
  assert.equal(publishBlockReason(legacy), '')
  assert.equal(depositText(legacy.depositAmount), '保证金未缴纳')
  assert.equal(depositText(null), '保证金未缴纳')
})

test('可发布的店铺放行，保证金按分转元展示一次', () => {
  const ok = { applyStatus: 'APPROVED', depositPaid: true, depositAmount: 50000, publishable: true }
  assert.equal(publishBlockReason(ok), '')
  assert.equal(canEditShopInfo(ok), true)
  assert.equal(applyStatusTag(ok.applyStatus), 'success')
  assert.equal(depositText(ok.depositAmount), '已缴保证金 ¥500.00')
  assert.equal(depositText(12345), '已缴保证金 ¥123.45')
})

test('主体类型只认后端允许的三项，未知值原样显示', () => {
  assert.deepEqual(subjectTypeOptions.map((opt) => opt.value), ['PERSONAL', 'INDIVIDUAL', 'ENTERPRISE'])
  assert.equal(subjectTypeText('ENTERPRISE'), '企业')
  assert.equal(subjectTypeText('INDIVIDUAL'), '个体工商户')
  assert.equal(subjectTypeText('UNKNOWN'), 'UNKNOWN')
  assert.equal(subjectTypeText(''), '—')
})

test('缺字段或空店铺不会误判成可发布', () => {
  assert.equal(publishBlockReason(undefined), '请先提交入驻申请，审核通过后才能发布商品')
  assert.equal(publishBlockReason({}), '请先提交入驻申请，审核通过后才能发布商品')
  assert.equal(canEditShopInfo(null), false)
})
