<script setup>
import { onMounted, ref } from 'vue'
import { auth } from '../stores/auth'
import { orderApi } from '../api'

const role = ref('buyer') // buyer | seller
const orders = ref([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const uid = auth.user?.userId
    const res =
      role.value === 'buyer' ? await orderApi.byBuyer(uid) : await orderApi.bySeller(uid)
    orders.value = res.data || []
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
    orders.value = []
  } finally {
    loading.value = false
  }
}

function switchRole(r) {
  if (role.value === r) return
  role.value = r
  load()
}

function statusText(o) {
  const s = String(o.status ?? '')
  const map = {
    '0': '待付款',
    '1': '待发货',
    '2': '待收货',
    '3': '已完成',
    '4': '已取消',
  }
  return map[s] || s || '未知'
}

function statusClass(o) {
  const s = statusText(o)
  if (s === '已完成') return 'bg-green-100 text-green-700'
  if (s === '已取消') return 'bg-gray-100 text-gray-500'
  return 'bg-blue-50 text-blue-600'
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-gray-900">我的订单</h1>
      <div class="flex gap-2">
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
      </div>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
    <div v-else-if="!orders.length" class="py-16 text-center text-gray-400 text-sm">
      暂无订单记录
    </div>

    <div v-else class="bg-white rounded-lg border border-gray-200 overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-gray-50 text-gray-600">
          <tr>
            <th class="text-left px-4 py-2.5 font-medium">订单号</th>
            <th class="text-left px-4 py-2.5 font-medium">商品</th>
            <th class="text-right px-4 py-2.5 font-medium">金额</th>
            <th class="text-left px-4 py-2.5 font-medium">下单时间</th>
            <th class="text-left px-4 py-2.5 font-medium">状态</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr v-for="o in orders" :key="o.orderId ?? o.id">
            <td class="px-4 py-2.5 text-gray-700">{{ o.orderId ?? o.id }}</td>
            <td class="px-4 py-2.5 text-gray-700">{{ o.productId ?? o.productName ?? '—' }}</td>
            <td class="px-4 py-2.5 text-right text-red-600 font-medium">
              ¥{{ Number(o.amount ?? o.price ?? 0).toFixed(2) }}
            </td>
            <td class="px-4 py-2.5 text-gray-500">
              {{ o.createTime || o.createdAt || '—' }}
            </td>
            <td class="px-4 py-2.5">
              <span class="text-xs px-2 py-0.5 rounded" :class="statusClass(o)">
                {{ statusText(o) }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
