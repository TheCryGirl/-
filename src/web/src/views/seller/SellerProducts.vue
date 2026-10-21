<script setup>
import { onMounted, ref } from 'vue'
import { productApi } from '../../api'

const products = ref([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await productApi.mine()
    const payload = data?.data ?? data
    products.value = Array.isArray(payload) ? payload : payload?.list || []
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function updateStatus(productId, status) {
  try {
    await productApi.updateStatus(productId, status)
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '操作失败'
  }
}

function statusText(s) {
  const map = { PENDING: '待审核', ON_SALE: '在售', OFF_SHELF: '已下架', REJECTED: '已驳回' }
  return map[s] || s
}

function statusClass(s) {
  if (s === 'ON_SALE') return 'bg-green-100 text-green-700'
  if (s === 'REJECTED') return 'bg-red-100 text-red-700'
  if (s === 'OFF_SHELF') return 'bg-gray-100 text-gray-500'
  return 'bg-yellow-100 text-yellow-700'
}

function formatPrice(cents) {
  return (cents / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-gray-900">商品管理</h1>
      <RouterLink
        :to="{ name: 'seller-product-new' }"
        class="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700"
      >
        + 发布商品
      </RouterLink>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
    <div v-else-if="!products.length" class="py-16 text-center text-gray-400 text-sm">
      暂无商品
    </div>

    <div v-else class="bg-white rounded-lg border border-gray-200 overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-gray-50 text-gray-600">
          <tr>
            <th class="text-left px-4 py-2.5 font-medium">商品</th>
            <th class="text-right px-4 py-2.5 font-medium">价格</th>
            <th class="text-left px-4 py-2.5 font-medium">状态</th>
            <th class="text-left px-4 py-2.5 font-medium">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr v-for="p in products" :key="p.productId">
            <td class="px-4 py-2.5">
              <div class="flex items-center gap-3">
                <img :src="p.mainImage || '/icons.svg'" class="w-12 h-12 object-cover rounded" alt="" />
                <span class="text-gray-900">{{ p.title }}</span>
              </div>
            </td>
            <td class="px-4 py-2.5 text-right text-red-600 font-medium">
              ¥{{ formatPrice(p.price) }}
            </td>
            <td class="px-4 py-2.5">
              <span class="text-xs px-2 py-0.5 rounded" :class="statusClass(p.status)">
                {{ statusText(p.status) }}
              </span>
            </td>
            <td class="px-4 py-2.5">
              <RouterLink
                :to="{ name: 'seller-product-edit', params: { id: p.productId } }"
                class="text-xs text-gray-600 hover:text-blue-600 mr-2"
              >
                编辑
              </RouterLink>
              <button
                v-if="p.status === 'ON_SALE'"
                class="text-xs text-gray-600 hover:text-blue-600 mr-2"
                @click="updateStatus(p.productId, 'OFF_SHELF')"
              >
                下架
              </button>
              <button
                v-if="p.status === 'OFF_SHELF'"
                class="text-xs text-gray-600 hover:text-blue-600"
                @click="updateStatus(p.productId, 'ON_SALE')"
              >
                上架
              </button>
              <span v-if="p.status === 'REJECTED'" class="text-xs text-gray-400">
                请编辑后重新提交审核
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
