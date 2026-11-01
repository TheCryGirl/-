<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isOk, orderApi, paymentApi, unwrapResult } from '../api'
import {
  ORDER_STATUS_MAP,
  ORDER_STATUS_TAG,
  PAYMENT_STATUS_MAP,
  PAYMENT_STATUS_TAG,
  statusTag,
  statusText,
} from '../constants/status'
import { resolveErrorMessage, showError, showSuccess } from '../components/ui/message'
import { formatPrice } from '../utils/format'

const route = useRoute()
const router = useRouter()
const orderId = route.params.id

const order = ref(null)
const payment = ref(null)
const loading = ref(true)
const paying = ref(false)
const loadError = ref('')

let redirectTimer = null

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [{ data: orderRes }, paymentRes] = await Promise.all([
      orderApi.detail(orderId),
      paymentApi.byOrder(orderId).catch(() => null),
    ])
    if (!isOk(orderRes)) {
      loadError.value = orderRes?.message || '订单不存在或无权查看'
      return
    }
    const orderData = unwrapResult(orderRes)
    if (!orderData) {
      loadError.value = orderRes?.message || '订单不存在或无权查看'
      return
    }
    order.value = orderData
    // 未创建支付单时后端返回业务错误（code 60001），不能把错误包装当成支付单
    payment.value = paymentRes ? unwrapResult(paymentRes.data) : null
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载支付信息失败')
  } finally {
    loading.value = false
  }
}

async function createPayment() {
  if (paying.value) return
  paying.value = true
  try {
    const idempotencyKey = crypto.randomUUID()
    const { data } = await paymentApi.create({ orderId, channel: 'MOCK' }, idempotencyKey)
    if (!isOk(data)) {
      showError(data?.message || '创建支付单失败')
      return
    }
    const created = unwrapResult(data)
    if (!created?.paymentId) {
      showError('创建支付单失败：未返回支付单号')
      return
    }
    payment.value = created
    showSuccess('支付单已创建')
  } catch (err) {
    showError(resolveErrorMessage(err, '创建支付单失败'))
  } finally {
    paying.value = false
  }
}

async function mockPay() {
  if (paying.value || !payment.value) return
  paying.value = true
  try {
    // MOCK 支付接口按 paymentId 定位支付单，内部走完整回调流程
    const { data } = await paymentApi.mockPay({ paymentId: payment.value.paymentId })
    if (!isOk(data)) {
      showError(data?.message || '支付失败')
      return
    }
    showSuccess('支付成功')
    await load()
    redirectTimer = setTimeout(() => {
      router.push({ name: 'order-detail', params: { id: orderId } })
    }, 1500)
  } catch (err) {
    showError(resolveErrorMessage(err, '支付失败'))
  } finally {
    paying.value = false
  }
}

function goDetail() {
  router.push({ name: 'order-detail', params: { id: orderId } })
}

onMounted(load)
onBeforeUnmount(() => {
  if (redirectTimer) clearTimeout(redirectTimer)
})
</script>

<template>
  <div class="max-w-xl mx-auto space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h1 class="text-lg font-semibold text-gray-900">支付订单</h1>
      <span class="text-xs text-gray-400">当前仅支持 MOCK 模拟支付</span>
    </div>

    <el-result v-if="loadError" status="error" title="加载支付信息失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="order-pay-retry" @click="load">重试</el-button>
        <el-button data-testid="order-pay-back-detail" @click="goDetail">返回订单详情</el-button>
      </template>
    </el-result>

    <div
      v-else-if="loading"
      v-loading="true"
      class="min-h-60 flex items-center justify-center text-sm text-gray-400"
    >
      正在加载支付信息…
    </div>

    <el-card v-else-if="order" shadow="never">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb-4"
        title="演示环境不接入真实支付渠道，请使用「模拟支付成功」完成支付流程。"
      />

      <div class="space-y-3">
        <div class="flex items-start justify-between gap-4">
          <span class="text-sm text-gray-500 shrink-0">订单号</span>
          <span class="text-sm text-gray-900 font-medium break-all text-right">{{ order.orderNo }}</span>
        </div>
        <div class="flex items-center justify-between gap-4">
          <span class="text-sm text-gray-500">订单状态</span>
          <el-tag :type="statusTag(ORDER_STATUS_TAG, order.status)" size="small">
            {{ statusText(ORDER_STATUS_MAP, order.status) }}
          </el-tag>
        </div>
        <div class="flex items-center justify-between gap-4">
          <span class="text-sm text-gray-500">应付金额</span>
          <span class="text-2xl font-bold text-red-600">¥{{ formatPrice(order.payAmount) }}</span>
        </div>
      </div>

      <el-divider />

      <div v-if="order.status === 'PENDING_PAYMENT'">
        <div v-if="!payment" class="text-center space-y-3">
          <p class="text-sm text-gray-500">尚未创建支付单</p>
          <el-button
            type="primary"
            :loading="paying"
            data-testid="order-pay-create"
            @click="createPayment"
          >
            {{ paying ? '处理中…' : '创建支付单' }}
          </el-button>
        </div>

        <div v-else class="text-center space-y-3">
          <div class="flex flex-wrap items-center justify-center gap-2 text-sm text-gray-600">
            <span>支付单号：{{ payment.payNo || payment.paymentId }}</span>
            <el-tag :type="statusTag(PAYMENT_STATUS_TAG, payment.status)" size="small">
              {{ statusText(PAYMENT_STATUS_MAP, payment.status) }}
            </el-tag>
          </div>

          <el-alert
            v-if="payment.status === 'SUCCESS'"
            type="success"
            :closable="false"
            show-icon
            title="支付单已支付，正在刷新订单状态…"
          />
          <el-button
            v-else
            type="success"
            :loading="paying"
            data-testid="order-pay-mock"
            @click="mockPay"
          >
            {{ paying ? '支付中…' : '模拟支付成功' }}
          </el-button>
        </div>
      </div>

      <el-alert
        v-else
        type="success"
        :closable="false"
        show-icon
        title="订单已支付或已结束，无需继续支付。"
      >
        <el-button type="primary" link data-testid="order-pay-view-detail" @click="goDetail">
          查看订单详情
        </el-button>
      </el-alert>
    </el-card>
  </div>
</template>

<style scoped>
/* Element Plus 按钮默认不显示焦点环，键盘操作时需要可见反馈 */
.el-button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
