<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { isOk, orderApi, paymentApi, unwrapResult } from '../api'
import {
  ORDER_STATUS_MAP,
  ORDER_STATUS_TAG,
  statusTag,
  statusText,
} from '../constants/status'
import { resolveErrorMessage, showError, showSuccess, showWarning } from '../components/ui/message'
import { formatPrice, formatTime, formatTotal } from '../utils/format'
import {
  afterSaleStatusClass,
  afterSaleStatusLabel,
  afterSaleStatusOptions,
  afterSaleStepIndex,
  afterSaleSteps,
  afterSaleTypeLabel,
  afterSaleTypeOptions,
} from '../utils/afterSale'

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
const form = reactive({ type: 'REFUND_ONLY', amount: '', reason: '' })

const remaining = computed(() => {
  if (!payment.value) return 0
  return Math.max(0, (payment.value.amount || 0) - (payment.value.refundedAmount || 0))
})

const showRemaining = computed(() => payment.value?.status === 'SUCCESS')

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
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
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
    const { data } = await paymentApi.createAfterSale(
      {
        orderId: orderId.value,
        type: form.type,
        amount: form.type === 'EXCHANGE' ? null : amount,
        reason: form.reason,
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
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h1 class="text-lg font-semibold text-gray-900">我的售后</h1>
        <p class="text-xs text-gray-400 mt-1">仅退款、退货退款与换货均在此申请和跟踪</p>
      </div>
      <RouterLink :to="{ name: 'orders' }" class="text-sm text-blue-600 hover:text-blue-700">
        返回订单
      </RouterLink>
    </div>

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
              <span v-if="showRemaining">剩余可退 ¥{{ formatPrice(remaining) }}</span>
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

              <el-form-item
                label="售后原因"
                :class="form.type === 'EXCHANGE' ? 'sm:col-span-2' : ''"
              >
                <el-input
                  v-model="form.reason"
                  maxlength="255"
                  placeholder="选填，例如商品有瑕疵"
                  data-testid="after-sales-reason"
                />
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
              <span v-if="ticket.reason" class="break-words">原因：{{ ticket.reason }}</span>
            </div>

            <el-alert
              v-if="ticket.status === 'REJECTED'"
              type="error"
              :closable="false"
              show-icon
              :title="ticket.auditRemark ? `卖家已驳回：${ticket.auditRemark}` : '卖家已驳回'"
            />

            <div v-else class="pt-1">
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
              <p v-if="ticket.auditRemark && ticket.status !== 'REJECTED'" class="break-words">
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
