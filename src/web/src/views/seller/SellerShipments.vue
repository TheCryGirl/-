<script setup>
import { onMounted, ref } from 'vue'
import { orderApi } from '../../api'

const orders = ref([])
const loading = ref(true)
const error = ref('')
const processing = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await orderApi.list({ role: 'seller', status: 'PAID', page: 1, size: 50 })
    const payload = data?.data ?? data
    orders.value = Array.isArray(payload) ? payload : payload?.list || []
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function ship(orderId) {
  const no = prompt('请输入物流单号')
  if (!no) return
  processing.value = orderId
  try {
    await orderApi.ship(orderId, { trackingNo: no })
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '发货失败'
  } finally {
    processing.value = ''
  }
}

function formatPrice(cents) {
  return (cents / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">待发货</h1>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p class="text-xs text-gray-400">
      卖家订单列表依赖 GET /api/orders?role=seller，该参数尚未在《05-接口契约》中定义，待后端补充。
    </p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
    <div v-else-if="!orders.length" class="py-16 text-center text-gray-400 text-sm">
      暂无待发货订单
    </div>

    <div v-else class="bg-white rounded-lg border border-gray-200 overflow-hidden divide-y divide-gray-100">
      <div v-for="o in orders" :key="o.orderId" class="p-4">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm text-gray-500">{{ o.orderNo }}</span>
          <span class="text-xs px-2 py-0.5 rounded bg-blue-50 text-blue-600">待发货</span>
        </div>
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm text-gray-900">{{ o.items?.[0]?.productTitle }} 等</p>
            <p class="text-xs text-gray-500">{{ o.addressSnapshot?.receiver }} {{ o.addressSnapshot?.phone }}</p>
          </div>
          <div class="text-right">
            <p class="text-sm text-red-600 font-medium">¥{{ formatPrice(o.payAmount) }}</p>
            <button
              class="mt-1 px-3 py-1 rounded bg-blue-600 text-white text-xs hover:bg-blue-700 disabled:bg-gray-300"
              :disabled="processing === o.orderId"
              @click="ship(o.orderId)"
            >
              {{ processing === o.orderId ? '处理中…' : '发货' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
