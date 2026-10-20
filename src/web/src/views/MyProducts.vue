<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '../stores/auth'
import { productApi, purchaseApi } from '../api'

const router = useRouter()
const products = ref([])
const requests = ref([])
const loading = ref(true)
const error = ref('')
const tab = ref('all')

const filtered = computed(() => {
  if (tab.value === 'on') return products.value.filter((p) => isOnSale(p))
  if (tab.value === 'off') return products.value.filter((p) => !isOnSale(p))
  return products.value
})

function isOnSale(p) {
  const s = String(p.status ?? '')
  return s === '1' || s === '在售' || s === 'ON_SALE' || s === 'true'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const uid = auth.user?.userId
    const [prodRes, reqRes] = await Promise.all([
      productApi.byUserAll(uid),
      purchaseApi.byBuyer(uid).catch(() => ({ data: [] })),
    ])
    products.value = prodRes.data || []
    requests.value = reqRes.data || []
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleStatus(p) {
  const id = p.productId ?? p.id
  const next = isOnSale(p) ? 0 : 1
  try {
    await productApi.updateStatus(id, next)
    p.status = next
  } catch (err) {
    error.value = err.response?.data?.message || '状态更新失败'
  }
}

async function remove(p) {
  const id = p.productId ?? p.id
  if (!window.confirm(`确认删除「${p.name}」？删除后不可恢复。`)) return
  try {
    await productApi.remove(id)
    products.value = products.value.filter((x) => (x.productId ?? x.id) !== id)
  } catch (err) {
    error.value = err.response?.data?.message || '删除失败'
  }
}

function confirmSale(req) {
  router.push({ name: 'messages' })
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-gray-900">我的商品</h1>
      <RouterLink
        :to="{ name: 'sell' }"
        class="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700"
      >
        + 发布商品
      </RouterLink>
    </div>

    <div class="flex gap-2">
      <button
        v-for="t in [
          { key: 'all', label: '全部' },
          { key: 'on', label: '在售' },
          { key: 'off', label: '已下架' },
        ]"
        :key="t.key"
        class="px-3 py-1.5 text-sm rounded-md border"
        :class="
          tab === t.key
            ? 'bg-blue-50 border-blue-300 text-blue-600'
            : 'bg-white border-gray-300 text-gray-600 hover:bg-gray-50'
        "
        @click="tab = t.key"
      >
        {{ t.label }}
      </button>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
    <div v-else-if="!filtered.length" class="py-16 text-center text-gray-400 text-sm">
      还没有商品，去发布一件吧
    </div>

    <div v-else class="bg-white rounded-lg border border-gray-200 divide-y divide-gray-100">
      <div
        v-for="p in filtered"
        :key="p.productId ?? p.id"
        class="p-4 flex items-center gap-4"
      >
        <img
          v-if="p.image || p.imageUrl"
          :src="(p.image || p.imageUrl).startsWith('http') ? p.image || p.imageUrl : `/images/${p.image || p.imageUrl}`"
          :alt="p.name"
          class="w-16 h-16 rounded-md object-cover bg-gray-100 shrink-0"
        />
        <div v-else class="w-16 h-16 rounded-md bg-gray-100 shrink-0"></div>

        <div class="flex-1 min-w-0">
          <p class="text-sm font-medium text-gray-900 truncate">{{ p.name }}</p>
          <p class="text-xs text-gray-500 mt-1">
            ¥{{ Number(p.price ?? 0).toFixed(2) }} · {{ p.classification || '未分类' }}
          </p>
        </div>

        <span
          class="text-xs px-2 py-0.5 rounded shrink-0"
          :class="isOnSale(p) ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'"
        >
          {{ isOnSale(p) ? '在售' : '已下架' }}
        </span>

        <div class="flex gap-2 shrink-0">
          <button
            class="px-3 py-1.5 text-xs rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50"
            @click="toggleStatus(p)"
          >
            {{ isOnSale(p) ? '下架' : '上架' }}
          </button>
          <button
            class="px-3 py-1.5 text-xs rounded-md border border-red-300 text-red-600 hover:bg-red-50"
            @click="remove(p)"
          >
            删除
          </button>
        </div>
      </div>
    </div>

    <section v-if="requests.length" class="bg-white rounded-lg border border-gray-200 p-4">
      <h2 class="text-sm font-medium text-gray-800 mb-3">收到的购买申请</h2>
      <ul class="divide-y divide-gray-100">
        <li v-for="r in requests" :key="r.id ?? r.productId" class="py-2 flex items-center gap-3">
          <span class="text-sm text-gray-700 flex-1">
            商品 {{ r.productId }} · 申请人 {{ r.buyerId || r.userId || '—' }}
          </span>
          <button
            class="px-3 py-1.5 text-xs rounded-md bg-blue-600 text-white hover:bg-blue-700"
            @click="confirmSale(r)"
          >
            去沟通
          </button>
        </li>
      </ul>
    </section>
  </div>
</template>
