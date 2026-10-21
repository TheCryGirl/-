<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderApi, paymentApi } from '../api'

const route = useRoute()
const router = useRouter()
const orderId = route.params.id

const order = ref(null)
const loading = ref(true)
const error = ref('')
const processing = ref(false)
const payment = ref(null)
const refundVisible = ref(false)
const refundAmount = ref('')
const refundReason = ref('')
const refundMessage = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await orderApi.detail(orderId)
    order.value = data?.data ?? data
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function cancel() {
  if (!confirm('确定取消该订单吗？')) return
  processing.value = true
  try {
    await orderApi.cancel(orderId)
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '取消失败'
  } finally {
    processing.value = false
  }
}

async function confirmReceive() {
  if (!confirm('确认已收到商品吗？')) return
  processing.value = true
  try {
    await orderApi.confirm(orderId)
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '确认收货失败'
  } finally {
    processing.value = false
  }
}

function pay() {
  router.push({ name: 'order-pay', params: { id: orderId } })
}

/** 已支付订单可申请退款：先查支付单，金额默认剩余可退部分 */
async function openRefund() {
  error.value = ''
  refundMessage.value = ''
  try {
    const { data } = await paymentApi.byOrder(orderId)
    payment.value = data?.data ?? data
  } catch (err) {
    error.value = err.response?.data?.message || '查询支付单失败'
    return
  }
  if (payment.value?.status !== 'SUCCESS') {
    error.value = '当前支付单不可退款'
    return
  }
  const remaining = (payment.value.amount || 0) - (payment.value.refundedAmount || 0)
  if (remaining <= 0) {
    error.value = '该订单已无可退金额'
    return
  }
  refundAmount.value = (remaining / 100).toFixed(2)
  refundReason.value = ''
  refundVisible.value = true
}

async function submitRefund() {
  const cents = Math.round(Number(refundAmount.value) * 100)
  if (!Number.isFinite(cents) || cents <= 0) {
    error.value = '退款金额需大于 0'
    return
  }
  processing.value = true
  error.value = ''
  try {
    const { data } = await paymentApi.refund(payment.value.paymentId, {
      amount: cents,
      reason: refundReason.value,
    })
    const refund = data?.data ?? data
    refundVisible.value = false
    refundMessage.value = `退款申请已提交，退款单号 ${refund.refundId}，等待卖家或管理员审核`
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '申请退款失败'
  } finally {
    processing.value = false
  }
}

function statusText(s) {
  const map = {
    PENDING_PAYMENT: '待支付',
    PAID: '待发货',
    SHIPPED: '待收货',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    REFUNDED: '已退款',
  }
  return map[s] || s || '未知'
}

function statusClass(s) {
  if (s === 'COMPLETED') return 'bg-green-100 text-green-700'
  if (s === 'CANCELLED' || s === 'REFUNDED') return 'bg-gray-100 text-gray-500'
  return 'bg-blue-50 text-blue-600'
}

function formatPrice(cents) {
  return (cents / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">订单详情</h1>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p v-if="refundMessage" class="text-sm text-green-600">{{ refundMessage }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

    <div v-else-if="order" class="bg-white rounded-lg border border-gray-200 p-5 space-y-5">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-sm text-gray-500">订单号：{{ order.orderNo }}</p>
          <p class="text-xs text-gray-400 mt-1">下单时间：{{ order.createdAt || '—' }}</p>
        </div>
        <span class="text-sm px-3 py-1 rounded" :class="statusClass(order.status)">
          {{ statusText(order.status) }}
        </span>
      </div>

      <div class="border-t border-gray-100 pt-4">
        <h2 class="text-sm font-medium text-gray-700 mb-2">收货地址</h2>
        <p class="text-sm text-gray-900">
          {{ order.addressSnapshot?.receiver }} {{ order.addressSnapshot?.phone }}
        </p>
        <p class="text-sm text-gray-500">{{ order.addressSnapshot?.fullAddress }}</p>
      </div>

      <div class="border-t border-gray-100 pt-4">
        <h2 class="text-sm font-medium text-gray-700 mb-2">商品信息</h2>
        <div
          v-for="item in order.items"
          :key="item.skuId"
          class="flex items-center gap-3 py-2"
        >
          <img :src="item.mainImage || '/icons.svg'" class="w-14 h-14 object-cover rounded" alt="" />
          <div class="flex-1">
            <p class="text-sm font-medium text-gray-900">{{ item.productTitle }}</p>
            <p class="text-xs text-gray-500">{{ item.specName }} × {{ item.quantity }}</p>
          </div>
          <p class="text-sm text-gray-700">¥{{ formatPrice(item.priceSnapshot) }}</p>
        </div>
      </div>

      <div class="border-t border-gray-100 pt-4 flex items-center justify-between">
        <span class="text-sm text-gray-600">应付总额</span>
        <span class="text-xl font-bold text-red-600">¥{{ formatPrice(order.payAmount) }}</span>
      </div>

      <div v-if="refundVisible" class="border-t border-gray-100 pt-4 space-y-3">
        <h2 class="text-sm font-medium text-gray-700">申请退款</h2>
        <div class="grid gap-3 sm:grid-cols-3">
          <label class="text-sm text-gray-600">
            退款金额（元）
            <input
              v-model="refundAmount"
              type="number"
              min="0.01"
              step="0.01"
              class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            />
          </label>
          <label class="text-sm text-gray-600 sm:col-span-2">
            退款原因
            <input
              v-model="refundReason"
              maxlength="255"
              class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              placeholder="选填，例如商品有瑕疵"
            />
          </label>
        </div>
        <div class="flex justify-end gap-2">
          <button
            class="px-4 py-2 rounded-md border border-gray-300 text-gray-600 text-sm hover:bg-gray-50"
            @click="refundVisible = false"
          >
            取消
          </button>
          <button
            class="px-4 py-2 rounded-md bg-red-600 text-white text-sm hover:bg-red-700 disabled:bg-gray-300"
            :disabled="processing"
            @click="submitRefund"
          >
            {{ processing ? '提交中…' : '提交退款申请' }}
          </button>
        </div>
      </div>

      <div class="flex justify-end gap-2 pt-2">
        <button
          v-if="order.status === 'PENDING_PAYMENT'"
          class="px-5 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700"
          @click="pay"
        >
          去支付
        </button>
        <button
          v-if="order.status === 'PENDING_PAYMENT'"
          class="px-5 py-2 rounded-md border border-gray-300 text-gray-600 text-sm hover:bg-gray-50"
          :disabled="processing"
          @click="cancel"
        >
          取消订单
        </button>
        <button
          v-if="order.status === 'SHIPPED'"
          class="px-5 py-2 rounded-md bg-green-600 text-white text-sm hover:bg-green-700"
          :disabled="processing"
          @click="confirmReceive"
        >
          确认收货
        </button>
        <button
          v-if="['PAID', 'SHIPPED', 'COMPLETED'].includes(order.status)"
          class="px-5 py-2 rounded-md border border-red-500 text-red-600 text-sm hover:bg-red-50"
          :disabled="processing"
          @click="openRefund"
        >
          申请退款
        </button>
      </div>
    </div>
  </div>
</template>
