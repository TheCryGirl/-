<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { orderApi } from '../api'

const router = useRouter()
const role = ref('buyer')
const status = ref('')
const page = ref(1)
const size = 10
const total = ref(0)
const orders = ref([])
const loading = ref(true)
const error = ref('')

const statusOptions = [
  { value: '', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '待收货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { role: role.value, page: page.value, size }
    if (status.value) params.status = status.value
    const { data } = await orderApi.list(params)
    const payload = data?.data ?? data
    orders.value = Array.isArray(payload) ? payload : payload?.list || []
    total.value = payload?.total ?? orders.value.length
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
    orders.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

function changePage(next) {
  if (next < 1 || next > totalPages.value) return
  page.value = next
  load()
}

function switchRole(r) {
  role.value = r
  page.value = 1
  load()
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

function goDetail(order) {
  router.push({ name: 'order-detail', params: { id: order.orderId } })
}

function formatPrice(cents) {
  return (cents / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-lg font-semibold text-gray-900">我的订单</h1>
      <div class="flex items-center gap-2">
        <button
          v-for="r in [
            { key: 'buyer', label: '我买到的' },
            { key: 'seller', label: '我卖出的' },
          ]"
          :key="r.key"
          class="px-3 py-1.5 text-sm rounded-md border"
          :class="
            role === r.key
              ? 'bg-blue-50 border-blue-300 text-blue-600'
              : 'bg-white border-gray-300 text-gray-600 hover:bg-gray-50'
          "
          @click="switchRole(r.key)"
        >
          {{ r.label }}
        </button>
        <select
          v-model="status"
          class="px-3 py-1.5 text-sm border border-gray-300 rounded-md bg-white"
          @change="page = 1; load()"
        >
          <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </div>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
    <div v-else-if="!orders.length" class="py-16 text-center text-gray-400 text-sm">
      暂无订单记录
    </div>

    <div v-else class="space-y-3">
      <div class="bg-white rounded-lg border border-gray-200 overflow-hidden divide-y divide-gray-100">
        <div
          v-for="o in orders"
          :key="o.orderId"
          class="p-4 hover:bg-gray-50 cursor-pointer"
          @click="goDetail(o)"
        >
          <div class="flex items-center justify-between mb-2">
            <span class="text-sm text-gray-500">{{ o.orderNo }}</span>
            <span class="text-xs px-2 py-0.5 rounded" :class="statusClass(o.status)">
              {{ statusText(o.status) }}
            </span>
          </div>
          <div class="flex items-center gap-3">
            <div class="flex-1">
              <p class="text-sm text-gray-900">
                共 {{ o.items?.length || 1 }} 件商品
              </p>
              <p class="text-xs text-gray-500">{{ o.createdAt || '—' }}</p>
            </div>
            <p class="text-sm text-red-600 font-medium">¥{{ formatPrice(o.payAmount) }}</p>
          </div>
        </div>
      </div>

      <div v-if="totalPages > 1" class="flex items-center justify-center gap-3">
        <button
          class="px-3 py-1.5 text-sm rounded border border-gray-300 text-gray-600 hover:bg-gray-50 disabled:opacity-40"
          :disabled="page <= 1"
          @click="changePage(page - 1)"
        >
          上一页
        </button>
        <span class="text-sm text-gray-500">{{ page }} / {{ totalPages }}</span>
        <button
          class="px-3 py-1.5 text-sm rounded border border-gray-300 text-gray-600 hover:bg-gray-50 disabled:opacity-40"
          :disabled="page >= totalPages"
          @click="changePage(page + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>
