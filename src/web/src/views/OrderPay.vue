<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isOk, orderApi, paymentApi, unwrapResult } from '../api'

const route = useRoute()
const router = useRouter()
const orderId = route.params.id

const order = ref(null)
const payment = ref(null)
const loading = ref(true)
const paying = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [{ data: orderRes }, paymentRes] = await Promise.all([
      orderApi.detail(orderId),
      paymentApi.byOrder(orderId).catch(() => null),
    ])
    const orderData = unwrapResult(orderRes)
    if (!orderData) {
      error.value = orderRes?.message || '订单不存在或无权查看'
      return
    }
    order.value = orderData
    // 未创建支付单时后端返回业务错误（code 60001），不能把错误包装当成支付单
    payment.value = paymentRes ? unwrapResult(paymentRes.data) : null
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function createPayment() {
  paying.value = true
  error.value = ''
  try {
    const idempotencyKey = crypto.randomUUID()
    const { data } = await paymentApi.create({ orderId, channel: 'MOCK' }, idempotencyKey)
    const created = unwrapResult(data)
    if (!created) {
      error.value = data?.message || '创建支付单失败'
      return
    }
    payment.value = created
  } catch (err) {
    error.value = err.response?.data?.message || '创建支付单失败'
  } finally {
    paying.value = false
  }
}

async function mockPay() {
  if (!payment.value) return
  paying.value = true
  error.value = ''
  try {
    // MOCK 支付接口按 paymentId 定位支付单，内部走完整回调流程
    const { data } = await paymentApi.mockPay({ paymentId: payment.value.paymentId })
    if (!isOk(data)) {
      error.value = data?.message || '支付失败'
      return
    }
    await load()
    // 1.5 秒后跳转到订单详情
    setTimeout(() => router.push({ name: 'order-detail', params: { id: orderId } }), 1500)
  } catch (err) {
    error.value = err.response?.data?.message || '支付失败'
  } finally {
    paying.value = false
  }
}

function statusText(status) {
  const map = {
    PENDING_PAYMENT: '待支付',
    PAID: '已支付',
    SHIPPED: '已发货',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    REFUNDED: '已退款',
  }
  return map[status] || status
}

function formatPrice(cents) {
  return (cents / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div class="max-w-xl mx-auto space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">支付订单</h1>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

    <div v-else-if="order" class="bg-white rounded-lg border border-gray-200 p-6 space-y-4">
      <div class="flex items-center justify-between">
        <span class="text-sm text-gray-500">订单号</span>
        <span class="text-sm text-gray-900 font-medium">{{ order.orderNo }}</span>
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm text-gray-500">订单状态</span>
        <span class="text-sm text-blue-600">{{ statusText(order.status) }}</span>
      </div>
      <div class="flex items-center justify-between">
        <span class="text-sm text-gray-500">应付金额</span>
        <span class="text-2xl font-bold text-red-600">¥{{ formatPrice(order.payAmount) }}</span>
      </div>

      <hr class="border-gray-100" />

      <div v-if="order.status === 'PENDING_PAYMENT'">
        <div v-if="!payment" class="text-center">
          <button
            class="px-8 py-2.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
            :disabled="paying"
            @click="createPayment"
          >
            {{ paying ? '处理中…' : '创建支付单' }}
          </button>
        </div>
        <div v-else class="text-center space-y-3">
          <p class="text-sm text-gray-600">支付单号：{{ payment.payNo }}</p>
          <p v-if="payment.status === 'SUCCESS'" class="text-sm text-green-600">
            支付单已支付，即将跳转订单详情…
          </p>
          <button
            v-else
            class="px-8 py-2.5 rounded-md bg-green-600 text-white text-sm hover:bg-green-700 disabled:bg-gray-300"
            :disabled="paying"
            @click="mockPay"
          >
            {{ paying ? '支付中…' : '模拟支付成功' }}
          </button>
        </div>
      </div>

      <div v-else class="text-center text-sm text-green-600">订单已支付或已结束</div>
    </div>
  </div>
</template>
