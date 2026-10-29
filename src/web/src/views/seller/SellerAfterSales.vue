<script setup>
import { onMounted, reactive, ref } from 'vue'
import { isOk, paymentApi, unwrapResult } from '../../api'
import { formatPrice, formatTime } from '../../utils/format'
import {
  afterSaleStatusClass,
  afterSaleStatusLabel,
  afterSaleStatusOptions,
  afterSaleStepIndex,
  afterSaleSteps,
  afterSaleTypeLabel,
} from '../../utils/afterSale'

const tickets = ref([])
const loading = ref(true)
const error = ref('')
const message = ref('')
const statusFilter = ref('')
const processingId = ref('')
const remarks = reactive({})
const exchangeTracking = reactive({})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: 1, size: 50 }
    if (statusFilter.value) params.status = statusFilter.value
    const { data } = await paymentApi.afterSales(params)
    const page = unwrapResult(data)
    tickets.value = page?.list || []
  } catch (err) {
    error.value = err.response?.data?.message || '售后单加载失败'
  } finally {
    loading.value = false
  }
}

function actionLabel(ticket, approved) {
  if (ticket.type === 'REFUND_ONLY') return approved ? '同意退款' : '驳回'
  return approved ? '同意售后' : '驳回'
}

async function audit(ticket, approved) {
  processingId.value = ticket.ticketId
  error.value = ''
  message.value = ''
  try {
    const { data } = await paymentApi.auditAfterSale(ticket.ticketId, {
      approved,
      remark: remarks[ticket.ticketId] || '',
    })
    if (!isOk(data)) {
      error.value = data?.message || '处理失败'
      return
    }
    const result = unwrapResult(data)
    message.value = `售后单 ${result?.ticketId || ticket.ticketId} 已${approved ? '同意' : '驳回'}`
    remarks[ticket.ticketId] = ''
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '处理失败'
  } finally {
    processingId.value = ''
  }
}

async function receive(ticket) {
  processingId.value = ticket.ticketId
  error.value = ''
  message.value = ''
  try {
    const { data } = await paymentApi.receiveAfterSaleReturn(ticket.ticketId, {
      remark: remarks[ticket.ticketId] || '确认收货并退款',
    })
    if (!isOk(data)) {
      error.value = data?.message || '确认收货失败'
      return
    }
    message.value = `售后单 ${ticket.ticketId} 已确认收货并完成退款`
    remarks[ticket.ticketId] = ''
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '确认收货失败'
  } finally {
    processingId.value = ''
  }
}

async function reship(ticket) {
  const trackingNo = (exchangeTracking[ticket.ticketId] || '').trim()
  if (!trackingNo) {
    error.value = '请填写换货物流单号'
    return
  }
  processingId.value = ticket.ticketId
  error.value = ''
  message.value = ''
  try {
    const { data } = await paymentApi.reshipAfterSale(ticket.ticketId, {
      trackingNo,
      remark: remarks[ticket.ticketId] || '确认收货并重新发货',
    })
    if (!isOk(data)) {
      error.value = data?.message || '换货发货失败'
      return
    }
    message.value = `售后单 ${ticket.ticketId} 已完成换货发货`
    exchangeTracking[ticket.ticketId] = ''
    remarks[ticket.ticketId] = ''
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '换货发货失败'
  } finally {
    processingId.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h1 class="text-lg font-semibold text-gray-900">售后处理</h1>
        <p class="text-xs text-gray-400 mt-1">仅展示本店售后单；管理员进入时可见全量</p>
      </div>
      <select
        v-model="statusFilter"
        class="px-3 py-1.5 border border-gray-300 rounded-md text-sm"
        @change="load"
      >
        <option value="">全部状态</option>
        <option v-for="item in afterSaleStatusOptions" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p v-if="message" class="text-sm text-green-600">{{ message }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
    <div v-else-if="!tickets.length" class="py-16 text-center text-gray-400 text-sm">
      暂无售后单
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
        <span>售后单：{{ ticket.ticketId }}</span>
        <span>订单：{{ ticket.orderId }}</span>
        <span>买家：{{ ticket.buyerId }}</span>
        <span v-if="ticket.amount > 0">退款金额 ¥{{ formatPrice(ticket.amount) }}</span>
      </div>
      <p class="text-sm text-gray-500">原因：{{ ticket.reason || '—' }}</p>

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

      <div class="text-xs text-gray-500 space-y-1">
        <p v-if="ticket.refundId">退款单号：{{ ticket.refundId }}</p>
        <p v-if="ticket.returnTrackingNo">退货物流：{{ ticket.returnTrackingNo }}</p>
        <p v-if="ticket.exchangeTrackingNo">换货物流：{{ ticket.exchangeTrackingNo }}</p>
        <p v-if="ticket.auditRemark">处理备注：{{ ticket.auditRemark }}</p>
      </div>

      <div class="border-t border-gray-100 pt-3 space-y-2">
        <input
          v-if="['PENDING', 'BUYER_SHIPPED'].includes(ticket.status)"
          v-model="remarks[ticket.ticketId]"
          maxlength="255"
          class="w-full sm:w-80 px-3 py-2 border border-gray-300 rounded-md text-sm"
          placeholder="处理备注（选填）"
        />

        <div v-if="ticket.status === 'PENDING'" class="flex justify-end gap-2">
          <button
            class="px-3 py-1.5 rounded border border-gray-300 text-gray-600 text-sm hover:bg-gray-50 disabled:opacity-50"
            :disabled="processingId === ticket.ticketId"
            @click="audit(ticket, false)"
          >
            {{ actionLabel(ticket, false) }}
          </button>
          <button
            class="px-3 py-1.5 rounded bg-green-600 text-white text-sm hover:bg-green-700 disabled:bg-gray-300"
            :disabled="processingId === ticket.ticketId"
            @click="audit(ticket, true)"
          >
            {{ actionLabel(ticket, true) }}
          </button>
        </div>

        <div
          v-else-if="ticket.status === 'BUYER_SHIPPED' && ticket.type === 'RETURN_REFUND'"
          class="flex justify-end"
        >
          <button
            class="px-4 py-2 rounded bg-green-600 text-white text-sm hover:bg-green-700 disabled:bg-gray-300"
            :disabled="processingId === ticket.ticketId"
            @click="receive(ticket)"
          >
            确认收货并退款
          </button>
        </div>

        <div
          v-else-if="ticket.status === 'BUYER_SHIPPED' && ticket.type === 'EXCHANGE'"
          class="flex flex-wrap items-center justify-end gap-2"
        >
          <input
            v-model="exchangeTracking[ticket.ticketId]"
            maxlength="64"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm w-64"
            placeholder="换货物流单号"
          />
          <button
            class="px-4 py-2 rounded bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
            :disabled="processingId === ticket.ticketId"
            @click="reship(ticket)"
          >
            确认收货并重新发货
          </button>
        </div>

        <p
          v-else-if="ticket.status === 'APPROVED'"
          class="text-xs text-gray-400 text-right"
        >
          已同意，等待买家回填退货物流
        </p>
      </div>
    </article>
  </div>
</template>
