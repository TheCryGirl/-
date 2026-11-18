import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { formatTime, formatTotal } from '../src/utils/format.js'
import {
  applyStatusTag,
  applyStatusText,
  canEditShopInfo,
  depositText,
  publishBlockReason,
  subjectTypeOptions,
  subjectTypeText,
} from '../src/utils/shopApply.js'

/**
 * #144 页面级回归：直接执行 AdminShops / ShopProfile 的 <script setup>，
 * 展示与禁用规则用 src/utils/shopApply.js 的真实实现，只 stub 接口与组件库边界。
 */
function scriptOf(relative) {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^[ \t]*import[\s\S]*?from[ \t]*['"][^'"]+['"][ \t]*$/gm, '')
}

function harness(file, exports, { mine, confirm = true } = {}) {
  // vm 里创建的对象是另一个 realm，跨 realm 比较统一先 JSON 归一化
  const plain = (value) => JSON.parse(JSON.stringify(value))
  const notices = []
  const auditCalls = []
  const applyCalls = []
  const listParams = []
  const context = vm.createContext({
    ref: (v) => ({ value: v }),
    reactive: (o) => o,
    computed: (fn) => ({ get value() { return fn() } }),
    onMounted: () => {},
    isOk: (d) => d?.code === 0,
    unwrapResult: (r) => r.data,
    resolveErrorMessage: (e) => e.message,
    showError: (m) => notices.push(['error', m]),
    showSuccess: (m) => notices.push(['success', m]),
    confirmAction: async () => confirm,
    formatTime,
    formatTotal,
    applyStatusTag,
    applyStatusText,
    depositText,
    subjectTypeText,
    publishBlockReason,
    canEditShopInfo,
    subjectTypeOptions,
    usePagination: () => ({
      page: { value: 1 }, size: { value: 10 }, total: { value: 0 },
      onPageChange: () => {}, onSizeChange: () => {},
    }),
    shopApi: {
      list: async (params) => {
        listParams.push(plain(params))
        return { data: { code: 0, data: { list: [], total: 0 } } }
      },
      mine: async () => ({ data: { code: 0, data: mine } }),
      updateStatus: async () => ({ data: { code: 0, data: {} } }),
      audit: async (shopId, payload) => {
        auditCalls.push([shopId, plain(payload)])
        return { data: { code: 0, data: {} } }
      },
      apply: async (payload) => {
        applyCalls.push(plain(payload))
        return { data: { code: 0, data: { ...mine, ...payload, applyStatus: 'PENDING', publishable: false } } }
      },
    },
    productApi: { uploadImage: async () => ({ data: { code: 0, data: { url: '/images/up.png' } } }) },
  })
  vm.runInContext(scriptOf(file) + `\nthis.bundle = { ${exports} };`, context)
  return { bundle: context.bundle, notices, auditCalls, applyCalls, listParams }
}

const ADMIN_EXPORTS = [
  'openAudit', 'submitAudit', 'load', 'resetFilters',
  'auditVisible', 'auditForm', 'depositCents', 'applyFilter',
].join(', ')

const SELLER_EXPORTS = [
  'load', 'submitApply',
  'mine', 'applyForm', 'needApply', 'publishHint', 'depositHint', 'applyLabel',
].join(', ')

const ADMIN_PAGE = '../src/views/admin/AdminShops.vue'
const SELLER_PAGE = '../src/views/seller/ShopProfile.vue'

test('审核弹窗按元回显保证金，提交时换算成分且只四舍五入一次', async () => {
  const h = harness(ADMIN_PAGE, ADMIN_EXPORTS)
  h.bundle.openAudit({ shopId: 'S1', name: '待审店', sellerId: 'U1', depositAmount: 50000, applyStatus: 'PENDING' })
  assert.equal(h.bundle.auditVisible.value, true)
  assert.equal(h.bundle.auditForm.depositYuan, '500.00')

  h.bundle.auditForm.depositYuan = '12.345'
  assert.equal(h.bundle.depositCents.value, 1235)
  await h.bundle.submitAudit()

  assert.deepEqual(h.auditCalls, [['S1', { decision: 'APPROVED', depositAmount: 1235 }]])
  assert.equal(h.bundle.auditVisible.value, false)
  assert.deepEqual(h.notices, [['success', '已通过']])
  assert.equal(h.listParams.length, 1, '审核成功后应重新拉取列表')
})

test('历史店铺保证金留空，提交通过时按 0 分登记', async () => {
  const h = harness(ADMIN_PAGE, ADMIN_EXPORTS)
  h.bundle.openAudit({ shopId: 'S2', name: '老店', sellerId: 'U2', depositAmount: null, applyStatus: 'APPROVED' })
  assert.equal(h.bundle.auditForm.depositYuan, '')

  h.bundle.auditForm.depositYuan = '   '
  await h.bundle.submitAudit()
  assert.deepEqual(h.auditCalls, [['S2', { decision: 'APPROVED', depositAmount: 0 }]])
})

test('驳回不填理由不发请求，填了才提交并带上理由', async () => {
  const h = harness(ADMIN_PAGE, ADMIN_EXPORTS)
  h.bundle.openAudit({ shopId: 'S3', name: '资质不全店', sellerId: 'U3', depositAmount: 0, applyStatus: 'PENDING' })
  h.bundle.auditForm.decision = 'REJECTED'
  h.bundle.auditForm.reason = '  '

  await h.bundle.submitAudit()
  assert.equal(h.auditCalls.length, 0)
  assert.match(h.notices.map((n) => n[1]).join(' '), /理由/)

  h.bundle.auditForm.reason = ' 资质材料不清晰 '
  await h.bundle.submitAudit()
  assert.deepEqual(h.auditCalls, [['S3', { decision: 'REJECTED', reason: '资质材料不清晰' }]])
})

test('取消确认框时不提交审核', async () => {
  const h = harness(ADMIN_PAGE, ADMIN_EXPORTS, { confirm: false })
  h.bundle.openAudit({ shopId: 'S4', name: '未确认店', sellerId: 'U4', depositAmount: 10000, applyStatus: 'PENDING' })
  await h.bundle.submitAudit()
  assert.equal(h.auditCalls.length, 0)
  assert.equal(h.bundle.auditVisible.value, true)
})

test('待审核筛选会把 applyStatus 带进列表请求', async () => {
  const h = harness(ADMIN_PAGE, ADMIN_EXPORTS)
  h.bundle.applyFilter.value = 'PENDING'
  await h.bundle.load()
  assert.deepEqual(h.listParams[0], { page: 1, size: 10, applyStatus: 'PENDING' })

  h.bundle.resetFilters()
  assert.deepEqual(h.listParams[1], { page: 1, size: 10 })
  assert.equal(h.bundle.applyFilter.value, '')
})

test('未过审的卖家页展示申请表单并预填已填内容', async () => {
  const mine = {
    shopId: 'S5', sellerId: 'U5', name: ' 待审店 ', contactPhone: '13800138000',
    applyStatus: 'REJECTED', rejectReason: '资质材料不清晰', depositAmount: 0,
    depositPaid: false, publishable: false, subjectType: 'ENTERPRISE',
    licenseNo: '91110000MA01ABCD2X', materials: ['/images/a.png'], status: 'SUSPENDED',
  }
  const h = harness(SELLER_PAGE, SELLER_EXPORTS, { mine })
  await h.bundle.load()

  assert.equal(h.bundle.needApply.value, true)
  assert.equal(h.bundle.applyLabel.value, '已驳回')
  assert.equal(
    h.bundle.publishHint.value,
    '入驻审核已驳回：资质材料不清晰，请修改资质后重新提交'
  )
  assert.equal(h.bundle.applyForm.name, ' 待审店 ')
  assert.equal(h.bundle.applyForm.subjectType, 'ENTERPRISE')
  assert.deepEqual([...h.bundle.applyForm.materials], ['/images/a.png'])

  h.bundle.applyForm.name = '  资质补全店  '
  h.bundle.applyForm.materials.push('/images/b.png')
  await h.bundle.submitApply()

  const payload = h.applyCalls[0]
  assert.equal(payload.name, '资质补全店')
  assert.equal(payload.subjectType, 'ENTERPRISE')
  assert.deepEqual(payload.materials, ['/images/a.png', '/images/b.png'])
  assert.equal(h.bundle.mine.value.applyStatus, 'PENDING')
  assert.equal(h.bundle.needApply.value, true, '提交后仍是待审核，发布入口继续拦住')
})

test('审核通过的卖家页回到资料维护，不再要求提交申请', async () => {
  const mine = {
    shopId: 'S6', name: '已过审店', applyStatus: 'APPROVED',
    depositAmount: 50000, depositPaid: true, publishable: true, status: 'ACTIVE',
  }
  const h = harness(SELLER_PAGE, SELLER_EXPORTS, { mine })
  await h.bundle.load()
  assert.equal(h.bundle.needApply.value, false)
  assert.equal(h.bundle.publishHint.value, '')
  assert.equal(h.bundle.depositHint.value, '已缴保证金 ¥500.00')
})
