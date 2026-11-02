<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isOk, productApi, unwrapResult } from '../api'
import ProductCard from '../components/ProductCard.vue'
import { resolveErrorMessage } from '../components/ui/message'
import { formatTotal } from '../utils/format'

const route = useRoute()
const router = useRouter()

const products = ref([])
const categories = ref([])
const loading = ref(true)
const loadError = ref('')
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

function hasPrice(value) {
  return value !== '' && value !== null && value !== undefined
}

/** 当前生效的筛选条件，空结果时告诉用户到底筛了什么（#50） */
const activeFilters = computed(() => {
  const list = []
  if (keyword.value.trim()) list.push(`关键词「${keyword.value.trim()}」`)
  if (categoryId.value) list.push(`分类「${categoryName(categoryId.value)}」`)
  if (hasPrice(priceRange.value.min)) list.push(`最低价 ¥${priceRange.value.min}`)
  if (hasPrice(priceRange.value.max)) list.push(`最高价 ¥${priceRange.value.max}`)
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
  if (hasPrice(priceRange.value.min)) query.minPrice = String(priceRange.value.min)
  if (hasPrice(priceRange.value.max)) query.maxPrice = String(priceRange.value.max)
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
  const min = hasPrice(priceRange.value.min) ? Number(priceRange.value.min) : null
  const max = hasPrice(priceRange.value.max) ? Number(priceRange.value.max) : null
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
  loadError.value = ''
  try {
    // 价格在界面按元输入，接口契约按分传递
    const params = { page: page.value, size: size.value, sort: sort.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (categoryId.value) params.categoryId = categoryId.value
    if (hasPrice(priceRange.value.min))
      params.minPrice = Math.round(Number(priceRange.value.min) * 100)
    if (hasPrice(priceRange.value.max))
      params.maxPrice = Math.round(Number(priceRange.value.max) * 100)

    const { data } = await productApi.list(params)
    if (!isOk(data)) {
      loadError.value = data?.message || '加载商品失败'
      products.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    products.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = Array.isArray(payload)
      ? payload.length
      : payload?.total ?? products.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载商品失败')
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    const { data } = await productApi.categories()
    if (!isOk(data)) {
      categories.value = []
      return
    }
    const payload = unwrapResult(data)
    categories.value = Array.isArray(payload) ? payload : payload?.list ?? []
  } catch {
    // 分类加载失败不阻断商品列表，筛选区仍可用关键词与价格
    categories.value = []
  }
}

/** 筛选条件变化后：回第一页、重新查询、同步地址栏 */
function applyFilters() {
  clearSearchTimer()
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

function clearSearchTimer() {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
}

onMounted(() => {
  readQuery()
  // 先按地址栏恢复条件再监听输入，避免初始化时把 ?page=2 重置回第一页
  watch(keyword, () => {
    clearSearchTimer()
    searchTimer = setTimeout(() => applyFilters(), 400)
  })
  loadCategories()
  load(false)
})

onBeforeUnmount(clearSearchTimer)
</script>

<template>
  <div class="space-y-4">
    <!-- 筛选区：不加卡片 -->
    <el-form inline class="bg-white rounded-lg p-4" @submit.prevent>
      <el-form-item label="关键词">
        <el-input
          v-model="keyword"
          placeholder="搜索商品名称"
          clearable
          style="width: 220px"
          data-testid="product-list-keyword"
          @keyup.enter="applyFilters"
        />
      </el-form-item>
      <el-form-item label="分类">
        <el-select
          v-model="categoryId"
          placeholder="全部分类"
          clearable
          style="width: 180px"
          data-testid="product-list-category"
          @change="applyFilters"
        >
          <el-option label="全部分类" value="" />
          <el-option
            v-for="c in categories"
            :key="c.categoryId"
            :label="categoryLabel(c)"
            :value="c.categoryId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="排序">
        <el-select
          v-model="sort"
          style="width: 160px"
          data-testid="product-list-sort"
          @change="applyFilters"
        >
          <el-option v-for="opt in sortOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="价格（元）">
        <div class="flex items-center gap-2">
          <el-input
            v-model="priceRange.min"
            type="number"
            min="0"
            placeholder="最低"
            style="width: 100px"
            data-testid="product-list-min-price"
            @keyup.enter="applyFilters"
          />
          <span class="text-gray-400">—</span>
          <el-input
            v-model="priceRange.max"
            type="number"
            min="0"
            placeholder="最高"
            style="width: 100px"
            data-testid="product-list-max-price"
            @keyup.enter="applyFilters"
          />
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" data-testid="product-list-search" @click="applyFilters">查询</el-button>
        <el-button data-testid="product-list-reset" @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <p v-if="filterError" class="text-sm text-red-600">{{ filterError }}</p>

    <div class="flex flex-wrap items-center justify-between gap-2">
      <h1 class="text-lg font-semibold text-gray-900">商品市场</h1>
      <span v-if="!loadError" class="text-xs text-gray-400">
        {{ formatTotal(total, '件') }}
      </span>
    </div>

    <!-- 失败态与空态严格分开 -->
    <el-result
      v-if="loadError && !loading"
      status="error"
      title="加载商品失败"
      :sub-title="loadError"
    >
      <template #extra>
        <el-button type="primary" data-testid="product-list-retry" @click="load(false)">重试</el-button>
      </template>
    </el-result>

    <div
      v-else-if="loading"
      v-loading="true"
      class="min-h-60 flex items-center justify-center text-sm text-gray-400"
    >
      正在加载商品…
    </div>

    <el-empty v-else-if="!products.length" description="没有找到符合条件的商品">
      <div class="space-y-2 text-center">
        <p v-if="activeFilters.length" class="text-xs text-gray-500">
          当前筛选：{{ activeFilters.join('、') }}
        </p>
        <p class="text-xs text-gray-400">可以试着换个关键词，或者放宽价格区间</p>
        <el-button type="primary" plain data-testid="product-list-clear-filters" @click="resetFilters">
          清空筛选条件
        </el-button>
      </div>
    </el-empty>

    <div v-else class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
      <ProductCard
        v-for="p in products"
        :key="p.productId"
        :product="p"
        @click="openDetail"
      />
    </div>

    <div v-if="!loading && !loadError && totalPages > 1" class="flex justify-center pt-2">
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="total"
        :pager-count="5"
        data-testid="product-list-pagination"
        @current-change="goPage"
      />
    </div>
  </div>
</template>

<style scoped>
/* Element Plus 按钮默认不显示焦点环，键盘操作时需要可见反馈 */
.el-button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
