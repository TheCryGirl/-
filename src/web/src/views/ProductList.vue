<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { productApi } from '../api'
import { auth, isLoggedIn } from '../stores/auth'
import { purchaseApi } from '../api'
import ProductCard from '../components/ProductCard.vue'

const router = useRouter()

const products = ref([])
const loading = ref(false)
const error = ref('')

const keyword = ref('')
const classification = ref('')
const sortBy = ref('default')
const priceRange = ref({ low: '', high: '' })

const PAGE_SIZE = 12
const page = ref(1)

const toast = ref('')
let toastTimer = null
function showToast(msg) {
  toast.value = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 2200)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    let data = []

    if (priceRange.value.low !== '' && priceRange.value.high !== '') {
      const res = await productApi.byPriceRange(priceRange.value.low, priceRange.value.high)
      data = res.data || []
    } else if (keyword.value.trim()) {
      const res = await productApi.search(keyword.value.trim())
      data = res.data || []
    } else if (classification.value.trim()) {
      const res = await productApi.byClassification(classification.value.trim())
      data = res.data || []
    } else {
      // 默认展示在售商品
      const res = await productApi.byStatus(1)
      data = res.data || []
    }

    if (!Array.isArray(data)) data = data.list || data.content || []
    products.value = data
    page.value = 1
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
    products.value = []
  } finally {
    loading.value = false
  }
}

const sorted = computed(() => {
  const arr = [...products.value]
  const num = (v) => {
    const n = Number(v)
    return Number.isFinite(n) ? n : 0
  }
  if (sortBy.value === 'price-asc') arr.sort((a, b) => num(a.price) - num(b.price))
  else if (sortBy.value === 'price-desc') arr.sort((a, b) => num(b.price) - num(a.price))
  else if (sortBy.value === 'newest')
    arr.sort((a, b) =>
      String(b.createTime || b.createdAt || '').localeCompare(
        String(a.createTime || a.createdAt || ''),
      ),
    )
  return arr
})

const totalPages = computed(() => Math.max(1, Math.ceil(sorted.value.length / PAGE_SIZE)))
const paged = computed(() =>
  sorted.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
)

function resetFilters() {
  keyword.value = ''
  classification.value = ''
  priceRange.value = { low: '', high: '' }
  sortBy.value = 'default'
  load()
}

async function want(product) {
  if (!isLoggedIn.value) {
    sessionStorage.setItem('redirectAfterLogin', '/products')
    router.push({ name: 'login' })
    return
  }
  try {
    await purchaseApi.create(product.productId ?? product.id)
    showToast('已向卖家发起购买申请，请到消息页沟通')
  } catch (err) {
    showToast(err.response?.data?.message || '发起申请失败')
  }
}

function openDetail(product) {
  const id = product.productId ?? product.id
  if (id) router.push({ name: 'product-detail', params: { id } })
}

let searchTimer = null
watch(keyword, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(load, 400)
})

onMounted(load)
</script>

<template>
  <div class="space-y-5">
    <!-- 搜索与筛选 -->
    <section class="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
      <div class="flex flex-wrap items-center gap-2">
        <input
          v-model="keyword"
          type="text"
          placeholder="搜索商品名称"
          class="flex-1 min-w-50 px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <input
          v-model="classification"
          type="text"
          placeholder="分类"
          class="w-32 px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <select
          v-model="sortBy"
          class="px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="default">默认排序</option>
          <option value="newest">最新发布</option>
          <option value="price-asc">价格从低到高</option>
          <option value="price-desc">价格从高到低</option>
        </select>
      </div>

      <div class="flex flex-wrap items-center gap-2 text-sm">
        <span class="text-gray-500">价格区间</span>
        <input
          v-model="priceRange.low"
          type="number"
          min="0"
          placeholder="最低"
          class="w-24 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <span class="text-gray-400">—</span>
        <input
          v-model="priceRange.high"
          type="number"
          min="0"
          placeholder="最高"
          class="w-24 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <button
          class="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700"
          @click="load"
        >
          查询
        </button>
        <button
          class="px-4 py-2 rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50"
          @click="resetFilters"
        >
          重置
        </button>
      </div>
    </section>

    <!-- 结果 -->
    <section>
      <div class="flex items-center justify-between mb-3">
        <h2 class="text-base font-medium text-gray-800">
          商品市场
          <span class="text-sm text-gray-400 font-normal">共 {{ sorted.length }} 件</span>
        </h2>
        <RouterLink
          v-if="isLoggedIn"
          :to="{ name: 'sell' }"
          class="text-sm text-blue-600 hover:underline"
        >
          + 发布商品
        </RouterLink>
      </div>

      <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

      <div v-else-if="error" class="py-16 text-center text-red-500 text-sm">
        {{ error }}
        <button class="ml-2 text-blue-600 underline" @click="load">重试</button>
      </div>

      <div v-else-if="!paged.length" class="py-16 text-center text-gray-400 text-sm">
        没有找到符合条件的商品
      </div>

      <div v-else class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
        <ProductCard
          v-for="p in paged"
          :key="p.productId ?? p.id"
          :product="p"
          :current-user-id="auth.user?.userId || ''"
          @want="want"
          @click="openDetail"
        />
      </div>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="flex items-center justify-center gap-2 mt-6">
        <button
          class="px-3 py-1.5 text-sm rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50 disabled:opacity-40"
          :disabled="page === 1"
          @click="page--"
        >
          上一页
        </button>
        <span class="text-sm text-gray-500">{{ page }} / {{ totalPages }}</span>
        <button
          class="px-3 py-1.5 text-sm rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50 disabled:opacity-40"
          :disabled="page === totalPages"
          @click="page++"
        >
          下一页
        </button>
      </div>
    </section>

    <div
      v-if="toast"
      class="fixed left-1/2 -translate-x-1/2 bottom-24 px-4 py-2 rounded-md bg-gray-800 text-white text-sm shadow-lg z-50"
    >
      {{ toast }}
    </div>
  </div>
</template>
