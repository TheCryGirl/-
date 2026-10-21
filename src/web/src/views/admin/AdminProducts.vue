<script setup>
import { onMounted, ref } from 'vue'
import { productApi } from '../../api'

const products = ref([])
const loading = ref(true)
const error = ref('')
const processing = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    // 契约中无专门“待审核商品”接口，先用商品列表按状态过滤
    const { data } = await productApi.list({ status: 'PENDING', size: 100 })
    const payload = data?.data ?? data
    products.value = payload?.list || payload || []
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function audit(productId, approved) {
  processing.value = productId
  try {
    await productApi.audit(productId, { approved, remark: '' })
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '审核失败'
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
    <h1 class="text-lg font-semibold text-gray-900">商品审核</h1>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
    <div v-else-if="!products.length" class="py-16 text-center text-gray-400 text-sm">
      暂无待审核商品
    </div>

    <div v-else class="bg-white rounded-lg border border-gray-200 overflow-hidden divide-y divide-gray-100">
      <div v-for="p in products" :key="p.productId" class="p-4">
        <div class="flex items-center gap-3 mb-2">
          <img :src="p.mainImage || '/icons.svg'" class="w-14 h-14 object-cover rounded" alt="" />
          <div>
            <p class="text-sm font-medium text-gray-900">{{ p.title }}</p>
            <p class="text-xs text-gray-500">卖家：{{ p.sellerId }}</p>
          </div>
        </div>
        <p class="text-sm text-gray-700">价格：¥{{ formatPrice(p.price) }}</p>
        <div class="flex justify-end gap-2 mt-3">
          <button
            class="px-3 py-1 rounded border border-gray-300 text-xs text-gray-600 hover:bg-gray-50"
            :disabled="processing === p.productId"
            @click="audit(p.productId, false)"
          >
            驳回
          </button>
          <button
            class="px-3 py-1 rounded bg-green-600 text-white text-xs hover:bg-green-700"
            :disabled="processing === p.productId"
            @click="audit(p.productId, true)"
          >
            通过
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
