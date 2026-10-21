<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { productApi } from '../api'
import ProductCard from '../components/ProductCard.vue'

const router = useRouter()

const products = ref([])
const categories = ref([])
const loading = ref(false)
const error = ref('')

const keyword = ref('')
const categoryId = ref('')
const sort = ref('default')
const priceRange = ref({ min: '', max: '' })

const page = ref(1)
const size = ref(20)
const total = ref(0)

const sortOptions = [
  { value: 'default', label: '默认排序' },
  { value: 'newest', label: '最新发布' },
  { value: 'price_asc', label: '价格从低到高' },
  { value: 'price_desc', label: '价格从高到低' },
]

async function load(resetPage = true) {
  if (resetPage) page.value = 1
  loading.value = true
  error.value = ''
  try {
    const params = { page: page.value, size: size.value, sort: sort.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (categoryId.value) params.categoryId = categoryId.value
    if (priceRange.value.min) params.minPrice = priceRange.value.min * 100
    if (priceRange.value.max) params.maxPrice = priceRange.value.max * 100

    const { data } = await productApi.list(params)
    const payload = data?.data ?? data
    products.value = Array.isArray(payload) ? payload : payload?.list || []
    total.value = payload?.total ?? products.value.length
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
    products.value = []
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    const { data } = await productApi.categories()
    categories.value = data?.data ?? data ?? []
  } catch {
    categories.value = []
  }
}

function resetFilters() {
  keyword.value = ''
  categoryId.value = ''
  sort.value = 'default'
  priceRange.value = { min: '', max: '' }
  load()
}

function openDetail(product) {
  router.push({ name: 'product-detail', params: { id: product.productId } })
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

let searchTimer = null
watch(keyword, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => load(), 400)
})

onMounted(() => {
  loadCategories()
  load()
})
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
        <select
          v-model="categoryId"
          class="px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          @change="load()"
        >
          <option value="">全部分类</option>
          <option v-for="c in categories" :key="c.categoryId" :value="c.categoryId">
            {{ c.name }}
          </option>
        </select>
        <select
          v-model="sort"
          class="px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          @change="load()"
        >
          <option v-for="opt in sortOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </div>

      <div class="flex flex-wrap items-center gap-2 text-sm">
        <span class="text-gray-500">价格区间（元）</span>
        <input
          v-model.number="priceRange.min"
          type="number"
          min="0"
          placeholder="最低"
          class="w-24 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <span class="text-gray-400">—</span>
        <input
          v-model.number="priceRange.max"
          type="number"
          min="0"
          placeholder="最高"
          class="w-24 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <button
          class="px-4 py-2 rounded-md bg-blue-600 text-white hover:bg-blue-700"
          @click="load()"
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
          <span class="text-sm text-gray-400 font-normal">共 {{ total }} 件</span>
        </h2>
      </div>

      <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

      <div v-else-if="error" class="py-16 text-center text-red-500 text-sm">
        {{ error }}
        <button class="ml-2 text-blue-600 underline" @click="load">重试</button>
      </div>

      <div v-else-if="!products.length" class="py-16 text-center text-gray-400 text-sm">
        没有找到符合条件的商品
      </div>

      <div v-else class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
        <ProductCard
          v-for="p in products"
          :key="p.productId"
          :product="p"
          @click="openDetail"
        />
      </div>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="flex items-center justify-center gap-2 mt-6">
        <button
          class="px-3 py-1.5 text-sm rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50 disabled:opacity-40"
          :disabled="page === 1"
          @click="page--; load(false)"
        >
          上一页
        </button>
        <span class="text-sm text-gray-500">{{ page }} / {{ totalPages }}</span>
        <button
          class="px-3 py-1.5 text-sm rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50 disabled:opacity-40"
          :disabled="page === totalPages"
          @click="page++; load(false)"
        >
          下一页
        </button>
      </div>
    </section>
  </div>
</template>
