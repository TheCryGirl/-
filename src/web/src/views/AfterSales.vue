<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { isOk, orderApi, paymentApi, unwrapResult } from '../api'
import { formatPrice, formatTime } from '../utils/format'
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
const loading = ref(true)
const submitting = ref(false)
const processingId = ref('')
const error = ref('')
const message = ref('')
const filterStatus = ref('')
const returnTracking = reactive({})
const form = reactive({ type: 'REFUND_ONLY', amount: '', reason: '' })

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

const orderStatusLabels = {
  PENDING_PAYMENT: '待支付',
  PAID: '待发货',
  SHIPPED: '待收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDED: '已退款',
}

function makeKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function loadOrder() {
  order.value = null
  payment.value = null
  if (!orderId.value) return
  try {
    const { data } = await orderApi.detail(orderId.value)
    const detail = unwrapResult(data)
    if (!detail) {
      error.value = data?.message || '订单不存在或无权查看'
      return
    }
    order.value = detail
  } catch (err) {
    error.value = err.response?.data?.message || '订单加载失败'
    return
  }
  try {
    const { data } = await paymentApi.byOrder(orderId.value)
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
  try {
    const params = { page: 1, size: 50 }
    if (filterStatus.value) params.status = filterStatus.value
    if (orderId.value) params.orderId = orderId.value
    const { data } = await paymentApi.afterSales(params)
    const page = unwrapResult(data)
    tickets.value = page?.list || []
  } catch (err) {
    error.value = err.response?.data?.message || '售后记录加载失败'
  }
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    await loadOrder()
    await loadTickets()
  } finally {
    loading.value = false
  }
}

async function submit() {
  error.value = ''
  message.value = ''
  if (!orderId.value) {
    error.value = '请从订单详情进入申请售后'
    return
  }
  if (!canApply.value) {
    error.value = '当前订单或支付状态不可申请售后'
    return
  }
  let amount = 0
  if (form.type !== 'EXCHANGE') {
    const cents = Math.round(Number(form.amount) * 100)
    if (!Number.isFinite(cents) || cents <= 0) {
      error.value = '退款金额需大于 0'
      return
    }
    if (cents > remaining.value) {
      error.value = '退款金额不能超过剩余可退金额'
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
      error.value = data?.message || '售后申请失败'
      return
    }
    const ticket = unwrapResult(data)
    message.value = `售后申请已提交，售后单号 ${ticket?.ticketId || ''}`
    form.reason = ''
    await loadOrder()
    await loadTickets()
  } catch (err) {
    error.value = err.response?.data?.message || '售后申请失败'
  } finally {
    submitting.value = false
  }
}

async function shipReturn(ticket) {
  const trackingNo = (returnTracking[ticket.ticketId] || '').trim()
  if (!trackingNo) {
    error.value = '请填写退货物流单号'
    return
  }
  processingId.value = ticket.ticketId
  error.value = ''
  message.value = ''
  try {
    const { data } = await paymentApi.shipAfterSaleReturn(ticket.ticketId, { trackingNo })
    if (!isOk(data)) {
      error.value = data?.message || '提交退货物流失败'
      return
    }
    message.value = '退货物流已提交，等待卖家确认收货'
    returnTracking[ticket.ticketId] = ''
    await loadTickets()
  } catch (err) {
    error.value = err.response?.data?.message || '提交退货物流失败'
  } finally {
    processingId.value = ''
  }
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
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-lg font-semibold text-gray-900">我的售后</h1>
        <p class="text-xs text-gray-400 mt-1">仅退款、退货退款与换货均在此申请和跟踪</p>
      </div>
      <RouterLink
        :to="{ name: 'orders' }"
        class="text-sm text-blue-600 hover:text-blue-700"
      >
        返回订单
      </RouterLink>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p v-if="message" class="text-sm text-green-600">{{ message }}</p>

    <section v-if="orderId" class="bg-white rounded-lg border border-gray-200 p-4 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <h2 class="text-sm font-medium text-gray-700">申请售后</h2>
        <span v-if="order" class="text-xs text-gray-500">
          订单 {{ order.orderNo }} · {{ orderStatusLabels[order.status] || order.status }}
          <template v-if="payment">
            · 剩余可退 ¥{{ formatPrice(remaining) }}
          </template>
        </span>
      </div>

      <p
        v-if="!canApply"
        class="text-sm text-amber-600 bg-amber-50 border border-amber-100 rounded-md px-3 py-2"
      >
        当前订单没有可用的成功支付单，或剩余可退金额为 0，暂不能申请售后。
      </p>

      <div class="grid gap-3 sm:grid-cols-3">
        <label class="text-sm text-gray-600">
          售后类型
          <select
            v-model="form.type"
            class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
          >
            <option v-for="item in afterSaleTypeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
        </label>
        <label v-if="form.type !== 'EXCHANGE'" class="text-sm text-gray-600">
          退款金额（元）
          <input
            v-model="form.amount"
            type="number"
            min="0.01"
            step="0.01"
            class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
          />
        </label>
        <label class="text-sm text-gray-600" :class="form.type === 'EXCHANGE' ? 'sm:col-span-2' : ''">
          售后原因
          <input
            v-model="form.reason"
            maxlength="255"
            class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="选填，例如商品有瑕疵"
          />
        </label>
      </div>
      <div class="flex flex-wrap items-center justify-between gap-2">
        <p class="text-xs text-gray-400">{{ selectedTypeHint }}</p>
        <button
          class="px-5 py-2 rounded-md bg-red-600 text-white text-sm hover:bg-red-700 disabled:bg-gray-300"
          :disabled="submitting || !canApply"
          @click="submit"
        >
          {{ submitting ? '提交中…' : '提交售后申请' }}
        </button>
      </div>
    </section>

    <section class="space-y-3">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <h2 class="text-sm font-medium text-gray-700">
          {{ orderId ? '本订单售后记录' : '我的售后记录' }}
        </h2>
        <select
          v-model="filterStatus"
          class="px-3 py-1.5 border border-gray-300 rounded-md text-sm"
          @change="loadTickets"
        >
          <option value="">全部状态</option>
          <option v-for="item in afterSaleStatusOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </div>

      <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
      <div v-else-if="!tickets.length" class="py-16 text-center text-gray-400 text-sm">
        暂无售后记录
      </div>

      <article
        v-for="ticket in tickets"
        :key="ticket.ticketId"
        class="bg-white rounded-lg border border-gray-200 p-4 space-y-3"
      >
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium text-gray-900">
              {{ afterSaleTypeLabel(ticket.type) }}
            </span>
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
            class="text-blue-600 hover:text-blue-700"
          >
            订单 {{ ticket.orderId }}
          </RouterLink>
          <span v-if="ticket.amount > 0">退款金额 ¥{{ formatPrice(ticket.amount) }}</span>
          <span v-if="ticket.reason">原因：{{ ticket.reason }}</span>
        </div>

        <div
          v-if="ticket.status !== 'REJECTED'"
          class="flex flex-wrap items-center gap-2 pt-1"
        >
          <template v-for="(step, index) in afterSaleSteps(ticket.type)" :key="step">
            <div class="flex items-center gap-1">
              <span
                class="w-5 h-5 rounded-full text-[10px] flex items-center justify-center"
                :class="
                  index <= afterSaleStepIndex(ticket.type, ticket.status)
                    ? 'bg-green-600 text-white'
                    : 'bg-gray-100 text-gray-400'
                "
              >
                {{ index + 1 }}
              </span>
              <span
                class="text-xs"
                :class="
                  index <= afterSaleStepIndex(ticket.type, ticket.status)
                    ? 'text-gray-700'
                    : 'text-gray-400'
                "
              >
                {{ step }}
              </span>
            </div>
            <span
              v-if="index < afterSaleSteps(ticket.type).length - 1"
              class="w-5 h-px bg-gray-200"
            ></span>
          </template>
        </div>
        <p v-else class="text-sm text-red-600">
          卖家已驳回<template v-if="ticket.auditRemark">：{{ ticket.auditRemark }}</template>
        </p>

        <div class="text-xs text-gray-500 space-y-1">
          <p v-if="ticket.refundId">退款单号：{{ ticket.refundId }}</p>
          <p v-if="ticket.returnTrackingNo">退货物流：{{ ticket.returnTrackingNo }}</p>
          <p v-if="ticket.exchangeTrackingNo">换货物流：{{ ticket.exchangeTrackingNo }}</p>
          <p v-if="ticket.auditRemark && ticket.status !== 'REJECTED'">
            处理备注：{{ ticket.auditRemark }}
          </p>
        </div>

        <div
          v-if="ticket.status === 'APPROVED' && ticket.type !== 'REFUND_ONLY'"
          class="flex flex-wrap items-center gap-2 pt-1"
        >
          <input
            v-model="returnTracking[ticket.ticketId]"
            maxlength="64"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm w-64"
            placeholder="填写退货物流单号"
          />
          <button
            class="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
            :disabled="processingId === ticket.ticketId"
            @click="shipReturn(ticket)"
          >
            {{ processingId === ticket.ticketId ? '提交中…' : '提交退货物流' }}
          </button>
        </div>
      </article>
    </section>
  </div>
</template>
