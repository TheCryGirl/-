<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { http, isOk, orderApi, paymentApi, unwrapResult } from '../api'
import {
  ORDER_STATUS_MAP,
  ORDER_STATUS_TAG,
  statusTag,
  statusText,
} from '../constants/status'
import { confirmAction } from '../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess, showWarning } from '../components/ui/message'
import { formatPrice, formatTime, formatTotal } from '../utils/format'
import PageHero from '../components/store/PageHero.vue'
import { createIdempotencyKey } from '../utils/idempotency'
import {
  afterSaleReasonLabel,
  afterSaleReasonOptions,
  afterSaleStatusClass,
  afterSaleStatusLabel,
  afterSaleStatusOptions,
  afterSaleStepIndex,
  afterSaleSteps,
  afterSaleTypeLabel,
  afterSaleTypeOptions,
  uploadAfterSaleEvidence,
} from '../utils/afterSale'

const EVIDENCE_LIMIT = 3
const EVIDENCE_MAX_SIZE = 5 * 1024 * 1024
const EVIDENCE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'gif']

const route = useRoute()
const orderId = ref(String(route.query.orderId || ''))
const order = ref(null)
const payment = ref(null)
const tickets = ref([])
const ticketsTotal = ref(0)
const loading = ref(true)
const ticketsLoading = ref(false)
const submitting = ref(false)
const processingId = ref('')
const orderLoadError = ref('')
const ticketsError = ref('')
const filterStatus = ref('')
const returnTracking = reactive({})
const form = reactive({
  type: 'REFUND_ONLY',
  amount: '',
  reason: '',
  reasonCode: '',
  evidenceFiles: [],
})

/* #143 仲裁中补充材料：ticketId -> { remark, files: [{file, url}] } */
const supplements = reactive({})
const supplementSubmitting = ref('')

/* #143 时间线与凭证：点击展开后按需拉详情 */
const expandedTicket = ref('')
const detailLoading = ref('')
const details = reactive({})

const remaining = computed(() => {
  if (!payment.value) return 0
  return Math.max(0, (payment.value.amount || 0) - (payment.value.refundedAmount || 0))
})

const canApply = computed(() => {
  if (!order.value || !payment.value) return false
  if (payment.value.status !== 'SUCCESS') return false
  if (!['PAID', 'SHIPPED', 'COMPLETED'].includes(order.value.status)) return false
  return form.type === 'EXCHANGE' || remaining.value > 0
})

const selectedTypeHint = computed(
  () => afterSaleTypeOptions.find((item) => item.value === form.type)?.hint || '',
)

function makeKey() {
  return createIdempotencyKey()
}

async function loadOrder() {
  order.value = null
  payment.value = null
  orderLoadError.value = ''
  form.amount = ''
  if (!orderId.value) return
  try {
    const { data } = await orderApi.detail(orderId.value)
    if (!isOk(data)) {
      orderLoadError.value = data?.message || '订单不存在或无权查看'
      return
    }
    const detail = unwrapResult(data)
    if (!detail) {
      orderLoadError.value = data?.message || '订单不存在或无权查看'
      return
    }
    order.value = detail
  } catch (err) {
    orderLoadError.value = resolveErrorMessage(err, '订单加载失败')
    return
  }

  try {
    const { data } = await paymentApi.byOrder(orderId.value)
    if (!isOk(data)) {
      payment.value = null
      return
    }
    payment.value = unwrapResult(data)
    if (payment.value?.status === 'SUCCESS') {
      const rest = Math.max(0, (payment.value.amount || 0) - (payment.value.refundedAmount || 0))
      if (rest > 0 && !form.amount) {
        form.amount = (rest / 100).toFixed(2)
      }
    }
  } catch {
    payment.value = null
  }
}

async function loadTickets() {
  ticketsLoading.value = true
  ticketsError.value = ''
  try {
    // 原实现即一次取前 50 条，这里不改接口参数，只如实标注可见范围
    const params = { page: 1, size: 50 }
    if (filterStatus.value) params.status = filterStatus.value
    if (orderId.value) params.orderId = orderId.value
    const { data } = await paymentApi.afterSales(params)
    if (!isOk(data)) {
      ticketsError.value = data?.message || '售后记录加载失败'
      tickets.value = []
      ticketsTotal.value = 0
      return
    }
    const page = unwrapResult(data)
    tickets.value = page?.list || []
    ticketsTotal.value = page?.total ?? tickets.value.length
    tickets.value.forEach((ticket) => {
      if (!supplements[ticket.ticketId]) {
        supplements[ticket.ticketId] = { remark: '', files: [] }
      }
    })
  } catch (err) {
    ticketsError.value = resolveErrorMessage(err, '售后记录加载失败')
    tickets.value = []
    ticketsTotal.value = 0
  } finally {
    ticketsLoading.value = false
  }
}

async function loadAll() {
  loading.value = true
  await Promise.all([loadOrder(), loadTickets()])
  loading.value = false
}

function onEvidenceChange(event) {
  const picked = Array.from(event.target.files || [])
  event.target.value = ''
  const invalidSize = picked.find((file) => file.size > EVIDENCE_MAX_SIZE)
  if (invalidSize) {
    showError(`${invalidSize.name} 超过 5MB，不能上传`)
    return
  }
  const invalidExt = picked.find((file) => {
    const name = file.name || ''
    const ext = name.includes('.') ? name.split('.').pop().toLowerCase() : ''
    return !EVIDENCE_EXTENSIONS.includes(ext)
  })
  if (invalidExt) {
    showError(`${invalidExt.name} 不是支持格式，只支持 jpg/jpeg/png/webp/gif`)
    return
  }
  const merged = [...form.evidenceFiles, ...picked.map((file) => ({ file, url: URL.createObjectURL(file) }))]
  if (merged.length > EVIDENCE_LIMIT) {
    showWarning(`单次最多上传 ${EVIDENCE_LIMIT} 张凭证`)
    merged.slice(form.evidenceFiles.length).forEach((item) => URL.revokeObjectURL(item.url))
    return
  }
  form.evidenceFiles = merged
}

function removeEvidence(index) {
  const [removed] = form.evidenceFiles.splice(index, 1)
  if (removed?.url) URL.revokeObjectURL(removed.url)
}

function onSupplementChange(ticket, event) {
  const picked = Array.from(event.target.files || [])
  event.target.value = ''
  const state = supplements[ticket.ticketId] || (supplements[ticket.ticketId] = { remark: '', files: [] })
  const invalidSize = picked.find((file) => file.size > EVIDENCE_MAX_SIZE)
  if (invalidSize) {
    showError(`${invalidSize.name} 超过 5MB，不能上传`)
    return
  }
  const merged = [...state.files, ...picked.map((file) => ({ file, url: URL.createObjectURL(file) }))]
  if (merged.length > EVIDENCE_LIMIT) {
    showWarning(`单次最多上传 ${EVIDENCE_LIMIT} 张凭证`)
    merged.slice(state.files.length).forEach((item) => URL.revokeObjectURL(item.url))
    return
  }
  state.files = merged
}

function removeSupplementEvidence(ticket, index) {
  const state = supplements[ticket.ticketId]
  if (!state) return
  const [removed] = state.files.splice(index, 1)
  if (removed?.url) URL.revokeObjectURL(removed.url)
}

async function uploadEvidence(files) {
  if (!files.length) return []
  const { data } = await uploadAfterSaleEvidence(files.map((item) => item.file))
  if (!isOk(data)) {
    throw new Error(data?.message || '凭证上传失败')
  }
  const urls = unwrapResult(data)
  if (!Array.isArray(urls) || urls.length !== files.length) {
    throw new Error('凭证上传失败，返回结果异常')
  }
  return urls
}

async function submit() {
  if (submitting.value) return
  if (!orderId.value) {
    showWarning('请从订单详情进入申请售后')
    return
  }
  if (!canApply.value) {
    showWarning('当前订单或支付状态不可申请售后')
    return
  }
  if (!form.reasonCode) {
    showWarning('请选择售后原因')
    return
  }
  if (form.reasonCode === 'OTHER' && !form.reason.trim()) {
    showWarning('选择其他原因时必须填写说明')
    return
  }

  let amount = 0
  if (form.type !== 'EXCHANGE') {
    const cents = Math.round(Number(form.amount) * 100)
    if (!Number.isFinite(cents) || cents <= 0) {
      showWarning('退款金额需大于 0')
      return
    }
    if (cents > remaining.value) {
      showWarning('退款金额不能超过剩余可退金额')
      return
    }
    amount = cents
  }

  submitting.value = true
  try {
    let attachmentUrls = []
    if (form.evidenceFiles.length) {
      attachmentUrls = await uploadEvidence(form.evidenceFiles)
    }
    const { data } = await paymentApi.createAfterSale(
      {
        orderId: orderId.value,
        type: form.type,
        amount: form.type === 'EXCHANGE' ? null : amount,
        reason: form.reason,
        reasonCode: form.reasonCode,
        attachmentUrls,
      },
      makeKey(),
    )
    if (!isOk(data)) {
      showError(data?.message || '售后申请失败')
      return
    }
    const ticket = unwrapResult(data)
    showSuccess(`售后申请已提交${ticket?.ticketId ? `，售后单号 ${ticket.ticketId}` : ''}`)
    form.reason = ''
    form.reasonCode = ''
    form.evidenceFiles.forEach((item) => item.url && URL.revokeObjectURL(item.url))
    form.evidenceFiles = []
    await loadOrder()
    await loadTickets()
  } catch (err) {
    showError(resolveErrorMessage(err, '售后申请失败'))
  } finally {
    submitting.value = false
  }
}

async function shipReturn(ticket) {
  if (processingId.value) return
  const trackingNo = (returnTracking[ticket.ticketId] || '').trim()
  if (!trackingNo) {
    showWarning('请填写退货物流单号')
    return
  }

  processingId.value = ticket.ticketId
  try {
    const { data } = await paymentApi.shipAfterSaleReturn(ticket.ticketId, { trackingNo })
    if (!isOk(data)) {
      showError(data?.message || '提交退货物流失败')
      return
    }
    showSuccess('退货物流已提交，等待卖家确认收货')
    returnTracking[ticket.ticketId] = ''
    await loadTickets()
  } catch (err) {
    showError(resolveErrorMessage(err, '提交退货物流失败'))
  } finally {
    processingId.value = ''
  }
}

/* #143 买家撤销：仅 PENDING */
async function cancelTicket(ticket) {
  if (processingId.value) return
  const ok = await confirmAction({
    message: `确定撤销售后单「${ticket.ticketId}」？撤销后卖家不能再处理该单。`,
    title: '撤销售后',
    danger: true,
  })
  if (!ok) return

  processingId.value = ticket.ticketId
  try {
    const { data } = await http.post(`/payments/after-sales/${ticket.ticketId}/cancel`, {
      remark: '买家主动撤销',
    })
    if (!isOk(data)) {
      showError(data?.message || '撤销失败')
      return
    }
    showSuccess('售后单已撤销')
    await loadTickets()
  } catch (err) {
    showError(resolveErrorMessage(err, '撤销失败'))
  } finally {
    processingId.value = ''
  }
}

/* #143 卖家驳回后申请平台仲裁 */
async function requestArbitration(ticket) {
  if (processingId.value) return
  const ok = await confirmAction({
    message: `确定对售后单「${ticket.ticketId}」申请平台仲裁？仲裁期间卖家不能处理该单。`,
    title: '申请平台仲裁',
    danger: true,
  })
  if (!ok) return

  processingId.value = ticket.ticketId
  try {
    const { data } = await http.post(`/payments/after-sales/${ticket.ticketId}/arbitration-request`, {
      remark: '买家申请平台仲裁',
    })
    if (!isOk(data)) {
      showError(data?.message || '申请仲裁失败')
      return
    }
    showSuccess('已申请平台仲裁，请等待平台裁定')
    await loadTickets()
  } catch (err) {
    showError(resolveErrorMessage(err, '申请仲裁失败'))
  } finally {
    processingId.value = ''
  }
}

/* #143 仲裁期间补充材料 */
async function submitSupplement(ticket) {
  if (supplementSubmitting.value) return
  const state = supplements[ticket.ticketId] || { remark: '', files: [] }
  const remark = (state.remark || '').trim()
  if (!remark && !state.files.length) {
    showWarning('请填写补充说明或上传凭证')
    return
  }

  supplementSubmitting.value = ticket.ticketId
  try {
    let attachmentUrls = []
    if (state.files.length) {
      attachmentUrls = await uploadEvidence(state.files)
    }
    const { data } = await http.post(`/payments/after-sales/${ticket.ticketId}/supplement`, {
      remark: remark || null,
      attachmentUrls,
    })
    if (!isOk(data)) {
      showError(data?.message || '补充材料失败')
      return
    }
    showSuccess('补充材料已提交，双方均可查看')
    state.remark = ''
    state.files.forEach((item) => item.url && URL.revokeObjectURL(item.url))
    state.files = []
    details[ticket.ticketId] = null
    await loadTickets()
    if (expandedTicket.value === ticket.ticketId) {
      await loadDetail(ticket.ticketId)
    }
  } catch (err) {
    showError(resolveErrorMessage(err, '补充材料失败'))
  } finally {
    supplementSubmitting.value = ''
  }
}

/* #143 进度时间线与凭证 */
async function loadDetail(ticketId) {
  detailLoading.value = ticketId
  try {
    const { data } = await paymentApi.afterSaleDetail(ticketId)
    if (!isOk(data)) {
      showError(data?.message || '进度详情加载失败')
      return null
    }
    const detail = unwrapResult(data)
    details[ticketId] = detail
    return detail
  } catch (err) {
    showError(resolveErrorMessage(err, '进度详情加载失败'))
    return null
  } finally {
    detailLoading.value = ''
  }
}

async function toggleTimeline(ticket) {
  if (expandedTicket.value === ticket.ticketId) {
    expandedTicket.value = ''
    return
  }
  expandedTicket.value = ticket.ticketId
  if (!details[ticket.ticketId]) {
    await loadDetail(ticket.ticketId)
  }
}

function operatorLabel(ticket, operatorId) {
  if (!operatorId) return '—'
  if (operatorId === 'SYSTEM') return '系统'
  if (operatorId === ticket.buyerId) return '买家'
  if (operatorId === ticket.sellerId) return '卖家'
  // 售后单只有买家/卖家/平台三方可操作，其余视为平台
  return '平台'
}

function changeFilter() {
  loadTickets()
}

function clearFilter() {
  filterStatus.value = ''
  loadTickets()
}

watch(
  () => route.query.orderId,
  (value) => {
    orderId.value = String(value || '')
    form.reason = ''
    loadAll()
  },
)

onMounted(loadAll)
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-4">
    <PageHero
      title="我的售后"
      subtitle="仅退款、退货退款与换货均在此申请和跟踪"
      icon="refresh"
      tone="amber"
    >
      <template #actions>
        <RouterLink :to="{ name: 'orders' }" class="jd-link-btn">
          返回订单
        </RouterLink>
      </template>
    </PageHero>

    <div
      v-if="loading"
      v-loading="true"
      class="min-h-60 flex items-center justify-center text-sm text-gray-400"
    >
      正在加载售后信息…
    </div>

    <template v-else>
      <!-- 申请售后 -->
      <el-card v-if="orderId" shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">申请售后</span>
            <span v-if="order" class="flex flex-wrap items-center gap-2 text-xs text-gray-500">
              <span class="break-all">订单 {{ order.orderNo }}</span>
              <el-tag :type="statusTag(ORDER_STATUS_TAG, order.status)" size="small">
                {{ statusText(ORDER_STATUS_MAP, order.status) }}
              </el-tag>
              <span v-if="payment">剩余可退 ¥{{ formatPrice(remaining) }}</span>
            </span>
          </div>
        </template>

        <el-result
          v-if="orderLoadError"
          status="error"
          title="加载订单失败"
          :sub-title="orderLoadError"
        >
          <template #extra>
            <el-button type="primary" data-testid="after-sales-order-retry" @click="loadOrder">
              重试
            </el-button>
          </template>
        </el-result>

        <template v-else>
          <el-alert
            v-if="!canApply"
            type="warning"
            :closable="false"
            show-icon
            title="当前订单没有可用的成功支付单，或剩余可退金额为 0，暂不能申请售后。"
            class="mb-3"
          />

          <el-form label-position="top">
            <div class="grid gap-3 sm:grid-cols-3">
              <el-form-item label="售后类型">
                <el-select
                  v-model="form.type"
                  class="w-full"
                  data-testid="after-sales-type"
                >
                  <el-option
                    v-for="item in afterSaleTypeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>

              <el-form-item v-if="form.type !== 'EXCHANGE'" label="退款金额（元）">
                <el-input
                  v-model="form.amount"
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder="例如 12.50"
                  data-testid="after-sales-amount"
                />
              </el-form-item>

              <el-form-item label="售后原因" :class="form.type === 'EXCHANGE' ? 'sm:col-span-2' : ''">
                <el-select
                  v-model="form.reasonCode"
                  class="w-full"
                  placeholder="请选择原因"
                  data-testid="after-sales-reason-code"
                >
                  <el-option
                    v-for="item in afterSaleReasonOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>

              <el-form-item
                v-if="form.reasonCode === 'OTHER'"
                label="补充说明"
                class="sm:col-span-2"
              >
                <el-input
                  v-model="form.reason"
                  maxlength="255"
                  placeholder="选择其他原因时必须填写说明"
                  data-testid="after-sales-reason"
                />
              </el-form-item>

              <el-form-item label="上传凭证（选填，最多 3 张，每张不超过 5MB）" class="sm:col-span-3">
                <div class="w-full space-y-2">
                  <div v-if="form.evidenceFiles.length" class="flex flex-wrap gap-2">
                    <div
                      v-for="(item, index) in form.evidenceFiles"
                      :key="item.url"
                      class="relative w-20 h-20 rounded-lg overflow-hidden border border-gray-200"
                    >
                      <img :src="item.url" :alt="item.file.name" class="w-full h-full object-cover" />
                      <button
                        type="button"
                        class="absolute top-0 right-0 w-5 h-5 text-xs leading-5 text-center text-white bg-black/60 hover:bg-black/80"
                        :data-testid="`after-sales-evidence-remove-${index}`"
                        @click="removeEvidence(index)"
                      >
                        ×
                      </button>
                    </div>
                  </div>
                  <label
                    v-if="form.evidenceFiles.length < 3"
                    class="inline-flex items-center justify-center w-20 h-20 rounded-lg border border-dashed border-gray-300 text-gray-400 cursor-pointer hover:border-blue-400 hover:text-blue-400"
                  >
                    <span class="text-2xl">+</span>
                    <input
                      type="file"
                      accept="image/jpeg,image/png,image/webp,image/gif"
                      multiple
                      class="sr-only"
                      data-testid="after-sales-evidence"
                      @change="onEvidenceChange"
                    />
                  </label>
                  <p class="text-xs text-gray-400">
                    支持 jpg/jpeg/png/webp/gif，凭证将随申请一并提交给卖家与平台
                  </p>
                </div>
              </el-form-item>
            </div>
          </el-form>

          <div class="flex flex-wrap items-center justify-between gap-2">
            <p class="text-xs text-gray-400">{{ selectedTypeHint }}</p>
            <el-button
              type="danger"
              :loading="submitting"
              :disabled="!canApply"
              data-testid="after-sales-submit"
              @click="submit"
            >
              {{ submitting ? '提交中…' : '提交售后申请' }}
            </el-button>
          </div>
        </template>
      </el-card>

      <!-- 售后记录 -->
      <el-card shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">
              {{ orderId ? '本订单售后记录' : '我的售后记录' }}
            </span>
            <div class="flex flex-wrap items-center gap-2">
              <span class="text-xs text-gray-400">
                {{ formatTotal(ticketsTotal, '单', tickets.length) }}
              </span>
              <el-select
                v-model="filterStatus"
                placeholder="全部状态"
                style="width: 150px"
                data-testid="after-sales-filter"
                @change="changeFilter"
              >
                <el-option label="全部状态" value="" />
                <el-option
                  v-for="item in afterSaleStatusOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </div>
          </div>
        </template>

        <el-result v-if="ticketsError" status="error" title="加载售后记录失败" :sub-title="ticketsError">
          <template #extra>
            <el-button type="primary" data-testid="after-sales-list-retry" @click="loadTickets">
              重试
            </el-button>
          </template>
        </el-result>

        <div
          v-else-if="ticketsLoading"
          v-loading="true"
          class="min-h-40 flex items-center justify-center text-sm text-gray-400"
        >
          正在加载售后记录…
        </div>

        <el-empty
          v-else-if="!tickets.length"
          :description="filterStatus ? '当前筛选下暂无售后记录' : '暂无售后记录'"
        >
          <el-button v-if="filterStatus" type="primary" plain data-testid="after-sales-clear-filter" @click="clearFilter">
            查看全部记录
          </el-button>
        </el-empty>

        <div v-else class="space-y-3">
          <div
            v-for="ticket in tickets"
            :key="ticket.ticketId"
            class="rounded-lg border border-gray-200 p-4 space-y-3"
            :data-testid="`after-sales-ticket-${ticket.ticketId}`"
          >
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="flex flex-wrap items-center gap-2">
                <span class="text-sm font-medium text-gray-900">{{ afterSaleTypeLabel(ticket.type) }}</span>
                <span
                  class="text-xs px-2 py-0.5 rounded"
                  :class="afterSaleStatusClass(ticket.status)"
                >
                  {{ afterSaleStatusLabel(ticket.status) }}
                </span>
              </div>
              <span class="text-xs text-gray-400">{{ formatTime(ticket.createdAt) }}</span>
            </div>

            <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-gray-600">
              <RouterLink
                :to="{ name: 'order-detail', params: { id: ticket.orderId } }"
                class="text-blue-600 hover:text-blue-700 break-all"
              >
                订单 {{ ticket.orderId }}
              </RouterLink>
              <span v-if="ticket.amount > 0">退款金额 ¥{{ formatPrice(ticket.amount) }}</span>
              <span v-if="ticket.reasonCode" class="break-words">原因：{{ afterSaleReasonLabel(ticket.reasonCode) }}</span>
              <span v-else-if="ticket.reason" class="break-words">原因：{{ ticket.reason }}</span>
              <span v-if="ticket.reasonCode && ticket.reason" class="break-words">说明：{{ ticket.reason }}</span>
            </div>

            <el-alert
              v-if="ticket.status === 'REJECTED'"
              type="error"
              :closable="false"
              show-icon
              :title="ticket.auditRemark ? `卖家已驳回：${ticket.auditRemark}` : '卖家已驳回'"
            />

            <el-alert
              v-else-if="ticket.status === 'ARBITRATING'"
              type="warning"
              :closable="false"
              show-icon
              title="平台仲裁中，卖家不能处理该售后单，请补充材料后等待平台裁定"
            />

            <el-alert
              v-else-if="ticket.status === 'CANCELLED'"
              type="info"
              :closable="false"
              show-icon
              :title="ticket.auditRemark ? `已撤销：${ticket.auditRemark}` : '已撤销'"
            />

            <div
              v-else
              class="pt-1"
            >
              <el-steps
                :active="afterSaleStepIndex(ticket.type, ticket.status)"
                finish-status="success"
                :process-status="['REFUNDED', 'COMPLETED'].includes(ticket.status) ? 'success' : 'process'"
                direction="vertical"
                class="max-w-sm"
              >
                <el-step v-for="step in afterSaleSteps(ticket.type)" :key="step" :title="step" />
              </el-steps>
            </div>

            <div class="text-xs text-gray-500 space-y-1">
              <p v-if="ticket.refundId" class="break-all">退款单号：{{ ticket.refundId }}</p>
              <p v-if="ticket.returnTrackingNo" class="break-all">退货物流：{{ ticket.returnTrackingNo }}</p>
              <p v-if="ticket.exchangeTrackingNo" class="break-all">换货物流：{{ ticket.exchangeTrackingNo }}</p>
              <p v-if="ticket.auditRemark && !['REJECTED', 'CANCELLED'].includes(ticket.status)" class="break-words">
                处理备注：{{ ticket.auditRemark }}
              </p>
            </div>

            <div
              v-if="ticket.status === 'APPROVED' && ticket.type !== 'REFUND_ONLY'"
              class="flex flex-wrap items-center gap-2 pt-1"
            >
              <el-input
                v-model="returnTracking[ticket.ticketId]"
                maxlength="64"
                placeholder="填写退货物流单号"
                style="width: 260px"
                data-testid="after-sales-tracking"
              />
              <el-button
                type="primary"
                :loading="processingId === ticket.ticketId"
                data-testid="after-sales-ship-return"
                @click="shipReturn(ticket)"
              >
                {{ processingId === ticket.ticketId ? '提交中…' : '提交退货物流' }}
              </el-button>
            </div>

            <!-- 待处理：撤销 -->
            <div v-if="ticket.status === 'PENDING'" class="flex justify-end pt-1">
              <el-button
                :loading="processingId === ticket.ticketId"
                :data-testid="`after-sales-cancel-${ticket.ticketId}`"
                @click="cancelTicket(ticket)"
              >
                撤销售后
              </el-button>
            </div>

            <!-- 已驳回：申请平台仲裁 -->
            <div v-if="ticket.status === 'REJECTED'" class="flex justify-end pt-1">
              <el-button
                type="warning"
                :loading="processingId === ticket.ticketId"
                :data-testid="`after-sales-arbitrate-${ticket.ticketId}`"
                @click="requestArbitration(ticket)"
              >
                申请平台仲裁
              </el-button>
            </div>

            <!-- 仲裁中：补充材料 -->
            <div v-if="ticket.status === 'ARBITRATING'" class="space-y-2 pt-1">
              <div class="flex flex-wrap items-center gap-2">
                <el-input
                  v-model="supplements[ticket.ticketId].remark"
                  maxlength="255"
                  placeholder="补充说明（选填）"
                  style="max-width: 420px"
                  :data-testid="`after-sales-supplement-remark-${ticket.ticketId}`"
                />
                <label
                  class="inline-flex items-center px-3 h-8 rounded border border-dashed border-gray-300 text-xs text-gray-500 cursor-pointer hover:border-blue-400 hover:text-blue-400"
                >
                  上传凭证
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp,image/gif"
                    multiple
                    class="sr-only"
                    :data-testid="`after-sales-supplement-file-${ticket.ticketId}`"
                    @change="onSupplementChange(ticket, $event)"
                  />
                </label>
                <el-button
                  type="primary"
                  :loading="supplementSubmitting === ticket.ticketId"
                  :data-testid="`after-sales-supplement-submit-${ticket.ticketId}`"
                  @click="submitSupplement(ticket)"
                >
                  {{ supplementSubmitting === ticket.ticketId ? '提交中…' : '提交补充材料' }}
                </el-button>
              </div>
              <div v-if="supplements[ticket.ticketId]?.files?.length" class="flex flex-wrap gap-2">
                <div
                  v-for="(item, index) in supplements[ticket.ticketId].files"
                  :key="item.url"
                  class="relative w-16 h-16 rounded-lg overflow-hidden border border-gray-200"
                >
                  <img :src="item.url" :alt="item.file.name" class="w-full h-full object-cover" />
                  <button
                    type="button"
                    class="absolute top-0 right-0 w-4 h-4 text-xs leading-4 text-center text-white bg-black/60 hover:bg-black/80"
                    @click="removeSupplementEvidence(ticket, index)"
                  >
                    ×
                  </button>
                </div>
              </div>
            </div>

            <!-- 进度时间线与凭证 -->
            <div class="pt-1 border-t border-gray-100">
              <el-button
                text
                type="primary"
                size="small"
                :loading="detailLoading === ticket.ticketId"
                :data-testid="`after-sales-timeline-${ticket.ticketId}`"
                @click="toggleTimeline(ticket)"
              >
                {{ expandedTicket === ticket.ticketId ? '收起进度与凭证' : '查看进度与凭证' }}
              </el-button>

              <div v-if="expandedTicket === ticket.ticketId && details[ticket.ticketId]" class="space-y-3 pt-2">
                <div class="flex flex-wrap gap-2">
                  <img
                    v-for="att in details[ticket.ticketId].attachments || []"
                    :key="att.id || att.url"
                    :src="att.url"
                    :alt="att.url"
                    class="w-16 h-16 rounded-lg object-cover border border-gray-200"
                  />
                  <span
                    v-if="!(details[ticket.ticketId].attachments || []).length"
                    class="text-xs text-gray-400"
                  >
                    暂无凭证
                  </span>
                </div>
                <el-timeline class="pt-1">
                  <el-timeline-item
                    v-for="eventItem in details[ticket.ticketId].events || []"
                    :key="eventItem.id || `${eventItem.createdAt}-${eventItem.toStatus}`"
                    :timestamp="formatTime(eventItem.createdAt)"
                    placement="top"
                  >
                    <div class="text-xs text-gray-600 space-y-0.5">
                      <p>
                        状态：{{ afterSaleStatusLabel(eventItem.toStatus) }}
                        <span v-if="eventItem.fromStatus" class="text-gray-400">
                          （原 {{ afterSaleStatusLabel(eventItem.fromStatus) }}）
                        </span>
                      </p>
                      <p>操作人：{{ operatorLabel(ticket, eventItem.operatorId) }}</p>
                      <p v-if="eventItem.remark" class="break-words">备注：{{ eventItem.remark }}</p>
                    </div>
                  </el-timeline-item>
                </el-timeline>
                <p v-if="!(details[ticket.ticketId].events || []).length" class="text-xs text-gray-400">
                  暂无进度记录
                </p>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
/* Element Plus 按钮默认不显示焦点环，键盘操作时需要可见反馈 */
.el-button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
