<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isOk, orderApi, unwrapResult } from '../api'
import { currentRole } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const orderId = route.params.id

const order = ref(null)
const loading = ref(true)
const error = ref('')
const processing = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await orderApi.detail(orderId)
    const orderData = unwrapResult(data)
    if (!orderData) {
      error.value = data?.message || '订单不存在或无权查看'
      return
    }
    order.value = orderData
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function cancel() {
  if (!confirm('确定取消该订单吗？')) return
  processing.value = true
  error.value = ''
  try {
    const { data } = await orderApi.cancel(orderId)
    if (!isOk(data)) {
      error.value = data?.message || '取消失败'
      return
    }
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
  error.value = ''
  try {
    const { data } = await orderApi.confirm(orderId)
    if (!isOk(data)) {
      error.value = data?.message || '确认收货失败'
      return
    }
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

const canApplyAfterSale = computed(
  () =>
    currentRole.value === 'BUYER' &&
    ['PAID', 'SHIPPED', 'COMPLETED'].includes(order.value?.status),
)

function applyAfterSale() {
  router.push({ name: 'after-sales', query: { orderId } })
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
          v-if="canApplyAfterSale"
          class="px-5 py-2 rounded-md border border-red-500 text-red-600 text-sm hover:bg-red-50"
          :disabled="processing"
          @click="applyAfterSale"
        >
          申请售后
        </button>
      </div>
    </div>
  </div>
</template>
