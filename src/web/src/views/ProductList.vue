<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi } from '../api'
import ProductCard from '../components/ProductCard.vue'

const route = useRoute()
const router = useRouter()

const products = ref([])
const categories = ref([])
const loading = ref(false)
const error = ref('')
const filterError = ref('')

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

/** 当前生效的筛选条件，空结果时告诉用户到底筛了什么（#50） */
const activeFilters = computed(() => {
  const list = []
  if (keyword.value.trim()) list.push(`关键词「${keyword.value.trim()}」`)
  if (categoryId.value) list.push(`分类「${categoryName(categoryId.value)}」`)
  if (priceRange.value.min !== '' && priceRange.value.min !== null)
    list.push(`最低价 ¥${priceRange.value.min}`)
  if (priceRange.value.max !== '' && priceRange.value.max !== null)
    list.push(`最高价 ¥${priceRange.value.max}`)
  return list
})

function categoryName(id) {
  return categories.value.find((c) => c.categoryId === id)?.name || id
}

/** 分类下拉按后端返回的层级顺序展示，子分类缩进（#50） */
function categoryLabel(category) {
  return category.parentId ? `　└ ${category.name}` : category.name
}

/** 筛选条件写回地址栏，刷新、前进后退与分享链接都能还原（#50） */
function syncQuery() {
  const query = {}
  if (keyword.value.trim()) query.keyword = keyword.value.trim()
  if (categoryId.value) query.categoryId = categoryId.value
  if (sort.value !== 'default') query.sort = sort.value
  if (priceRange.value.min !== '' && priceRange.value.min !== null)
    query.minPrice = String(priceRange.value.min)
  if (priceRange.value.max !== '' && priceRange.value.max !== null)
    query.maxPrice = String(priceRange.value.max)
  if (page.value > 1) query.page = String(page.value)
  router.replace({ name: 'products', query })
}

function readQuery() {
  keyword.value = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  categoryId.value = typeof route.query.categoryId === 'string' ? route.query.categoryId : ''
  sort.value = sortOptions.some((o) => o.value === route.query.sort)
    ? route.query.sort
    : 'default'
  priceRange.value = {
    min: route.query.minPrice ?? '',
    max: route.query.maxPrice ?? '',
  }
  const queryPage = Number(route.query.page)
  page.value = Number.isFinite(queryPage) && queryPage > 0 ? queryPage : 1
}

function validatePrice() {
  const min =
    priceRange.value.min === '' || priceRange.value.min === null
      ? null
      : Number(priceRange.value.min)
  const max =
    priceRange.value.max === '' || priceRange.value.max === null
      ? null
      : Number(priceRange.value.max)
  if ((min !== null && (Number.isNaN(min) || min < 0)) || (max !== null && (Number.isNaN(max) || max < 0))) {
    filterError.value = '价格必须是不小于 0 的数字'
    return false
  }
  if (min !== null && max !== null && min > max) {
    filterError.value = '最低价不能高于最高价'
    return false
  }
  filterError.value = ''
  return true
}

async function load(resetPage = true) {
  if (resetPage) page.value = 1
  if (!validatePrice()) return
  loading.value = true
  error.value = ''
  try {
    const params = { page: page.value, size: size.value, sort: sort.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (categoryId.value) params.categoryId = categoryId.value
    if (priceRange.value.min !== '' && priceRange.value.min !== null)
      params.minPrice = Math.round(Number(priceRange.value.min) * 100)
    if (priceRange.value.max !== '' && priceRange.value.max !== null)
      params.maxPrice = Math.round(Number(priceRange.value.max) * 100)

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
    const payload = data?.data ?? data
    categories.value = Array.isArray(payload) ? payload : payload?.list || []
  } catch {
    categories.value = []
  }
}

/** 筛选条件变化后：回第一页、重新查询、同步地址栏 */
function applyFilters() {
  load(true)
  syncQuery()
}

function goPage(target) {
  page.value = Math.min(totalPages.value, Math.max(1, target))
  load(false)
  syncQuery()
}

function resetFilters() {
  keyword.value = ''
  categoryId.value = ''
  sort.value = 'default'
  priceRange.value = { min: '', max: '' }
  filterError.value = ''
  applyFilters()
}

function openDetail(product) {
  router.push({ name: 'product-detail', params: { id: product.productId } })
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

let searchTimer = null

onMounted(() => {
  readQuery()
  // 先按地址栏恢复条件再监听输入，避免初始化时把 ?page=2 重置回第一页
  watch(keyword, () => {
    if (searchTimer) clearTimeout(searchTimer)
    searchTimer = setTimeout(() => applyFilters(), 400)
  })
  loadCategories()
  load(false)
})

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
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
          @change="applyFilters"
        >
          <option value="">全部分类</option>
          <option v-for="c in categories" :key="c.categoryId" :value="c.categoryId">
            {{ categoryLabel(c) }}
          </option>
        </select>
        <select
          v-model="sort"
          class="px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          @change="applyFilters"
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
          @click="applyFilters"
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

      <p v-if="filterError" class="text-sm text-red-600">{{ filterError }}</p>
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
        <button class="ml-2 text-blue-600 underline" @click="load()">重试</button>
      </div>

      <div v-else-if="!products.length" class="py-16 text-center text-gray-400 text-sm space-y-3">
        <p class="text-gray-600">没有找到符合条件的商品</p>
        <p v-if="activeFilters.length" class="text-xs text-gray-500">
          当前筛选：{{ activeFilters.join('、') }}
        </p>
        <p class="text-xs text-gray-400">可以试着换个关键词，或者放宽价格区间</p>
        <button
          class="px-4 py-2 rounded-md border border-blue-600 text-blue-600 text-sm hover:bg-blue-50"
          @click="resetFilters"
        >
          清空筛选条件
        </button>
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
          @click="goPage(page - 1)"
        >
          上一页
        </button>
        <span class="text-sm text-gray-500">{{ page }} / {{ totalPages }}</span>
        <button
          class="px-3 py-1.5 text-sm rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50 disabled:opacity-40"
          :disabled="page === totalPages"
          @click="goPage(page + 1)"
        >
          下一页
        </button>
      </div>
    </section>
  </div>
</template>
