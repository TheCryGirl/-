<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { cartApi, favoriteApi, isOk, productApi, unwrapResult } from '../api'
import ProductCard from '../components/ProductCard.vue'
import BackToTop from '../components/store/BackToTop.vue'
import CategoryGrid from '../components/store/CategoryGrid.vue'
import CuratedShowcase from '../components/store/CuratedShowcase.vue'
import FilterDrawer from '../components/store/FilterDrawer.vue'
import FlashSale from '../components/store/FlashSale.vue'
import HeroCarousel from '../components/store/HeroCarousel.vue'
import ServiceStrip from '../components/store/ServiceStrip.vue'
import { resolveErrorMessage, showError, showInfo, showSuccess } from '../components/ui/message'
import { curatedItems, flashSaleItems, heroSlides } from '../data/showcase'
import { isLoggedIn } from '../stores/auth'
import { formatTotal } from '../utils/format'

const route = useRoute()
const router = useRouter()

/* ---------------- 数据状态 ---------------- */
const products = ref([])
const categories = ref([])
const loading = ref(true)
const loadError = ref('')
const filterError = ref('')

/* ---------------- 查询条件 ---------------- */
const keyword = ref('')
const categoryId = ref('')
const sort = ref('default')
const minPrice = ref('')
const maxPrice = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)

/* ---------------- 界面状态 ---------------- */
const drawerOpen = ref(false)
const favoriteIds = ref(new Set())
const favoriteBusyId = ref('')
const addBusyId = ref('')

const sortOptions = [
  { value: 'default', label: '默认排序' },
  { value: 'newest', label: '最新发布' },
  { value: 'price_asc', label: '价格从低到高' },
  { value: 'price_desc', label: '价格从高到低' },
]

const sortTabs = [
  { value: 'default', label: '综合' },
  { value: 'newest', label: '最新' },
]

const activeFilters = computed(() => {
  const list = []
  if (keyword.value.trim()) list.push({ key: 'keyword', text: `关键词「${keyword.value.trim()}」` })
  if (categoryId.value) list.push({ key: 'category', text: `分类「${categoryName(categoryId.value)}」` })
  if (hasPrice(minPrice.value)) list.push({ key: 'min', text: `≥ ¥${minPrice.value}` })
  if (hasPrice(maxPrice.value)) list.push({ key: 'max', text: `≤ ¥${maxPrice.value}` })
  return list
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const priceSortActive = computed(() => sort.value === 'price_asc' || sort.value === 'price_desc')

function hasPrice(value) {
  return value !== '' && value !== null && value !== undefined
}

function categoryName(id) {
  return categories.value.find((c) => c.categoryId === id)?.name || id
}

function categoryLabel(category) {
  return category.parentId ? `　└ ${category.name}` : category.name
}

/* ---------------- 地址栏同步 ---------------- */
// 记录刚刚由本页写入的地址：路由变化时据此判断是「自己写的」还是「外部触发的」，
// 只跳过自己写入的那一次，避免禁用掉顶部搜索框带来的真实导航。
let skipRouteFullPath = ''

function syncQuery() {
  const query = {}
  if (keyword.value.trim()) query.keyword = keyword.value.trim()
  if (categoryId.value) query.categoryId = categoryId.value
  if (sort.value !== 'default') query.sort = sort.value
  if (hasPrice(minPrice.value)) query.minPrice = String(minPrice.value)
  if (hasPrice(maxPrice.value)) query.maxPrice = String(maxPrice.value)
  if (page.value > 1) query.page = String(page.value)
  const target = router.resolve({ name: 'products', query })
  skipRouteFullPath = target.fullPath
  router.replace(target)
  // 地址没有实际变化时路由不会触发 watcher，这里兜底清掉标记
  window.setTimeout(() => {
    if (skipRouteFullPath === target.fullPath) skipRouteFullPath = ''
  }, 600)
}

function readQuery() {
  keyword.value = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  categoryId.value = typeof route.query.categoryId === 'string' ? route.query.categoryId : ''
  sort.value = sortOptions.some((o) => o.value === route.query.sort) ? route.query.sort : 'default'
  minPrice.value = route.query.minPrice ?? ''
  maxPrice.value = route.query.maxPrice ?? ''
  const queryPage = Number(route.query.page)
  page.value = Number.isFinite(queryPage) && queryPage > 0 ? queryPage : 1
}

function validatePrice() {
  const min = hasPrice(minPrice.value) ? Number(minPrice.value) : null
  const max = hasPrice(maxPrice.value) ? Number(maxPrice.value) : null
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

/* ---------------- 加载数据 ---------------- */
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
    if (hasPrice(minPrice.value)) params.minPrice = Math.round(Number(minPrice.value) * 100)
    if (hasPrice(maxPrice.value)) params.maxPrice = Math.round(Number(maxPrice.value) * 100)

    const { data } = await productApi.list(params)
    if (!isOk(data)) {
      loadError.value = data?.message || '加载商品失败'
      products.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    products.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = Array.isArray(payload) ? payload.length : payload?.total ?? products.value.length
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
    categories.value = []
  }
}

/** 收藏状态：登录后一次性拉取，避免每张卡片各发一次请求 */
async function loadFavorites() {
  if (!isLoggedIn.value) {
    favoriteIds.value = new Set()
    return
  }
  try {
    const { data } = await favoriteApi.list({ page: 1, size: 100 })
    const payload = unwrapResult(data)
    const list = payload?.list || []
    favoriteIds.value = new Set(list.filter((item) => !item.invalid).map((item) => item.productId))
  } catch {
    favoriteIds.value = new Set()
  }
}

/* ---------------- 交互 ---------------- */
function applyFilters() {
  load(true)
  syncQuery()
}

function resetFilters() {
  keyword.value = ''
  categoryId.value = ''
  sort.value = 'default'
  minPrice.value = ''
  maxPrice.value = ''
  filterError.value = ''
  drawerOpen.value = false
  applyFilters()
}

function removeFilter(key) {
  if (key === 'keyword') keyword.value = ''
  if (key === 'category') categoryId.value = ''
  if (key === 'min') minPrice.value = ''
  if (key === 'max') maxPrice.value = ''
  applyFilters()
}

function goPage(target) {
  page.value = Math.min(totalPages.value, Math.max(1, target))
  load(false)
  syncQuery()
  scrollToFeed()
}

function setSort(value) {
  sort.value = value
  applyFilters()
}

function togglePriceSort() {
  setSort(sort.value === 'price_asc' ? 'price_desc' : 'price_asc')
}

function selectCategory(id) {
  categoryId.value = id
  applyFilters()
  scrollToFeed()
}

/**
 * 秒杀位/精选位的商品是运营内容，不是真实商品记录：
 * 点进去时先按关键词找到真实商品，直接进入详情页；找不到才退回搜索结果页。
 */
async function resolveToProduct(item) {
  const keyword = String(item?.keyword || '').trim()
  if (!keyword) {
    router.push({ name: 'search' })
    return
  }
  try {
    const { data } = await productApi.list({ keyword, page: 1, size: 1 })
    const payload = unwrapResult(data)
    const list = Array.isArray(payload) ? payload : payload?.list || []
    if (list.length) {
      router.push({ name: 'product-detail', params: { id: list[0].productId } })
      return
    }
  } catch {
    // 查询失败时退回搜索页，不把用户卡在当前页
  }
  router.push({ name: 'search', query: { keyword } })
}

function selectCurated(item) {
  resolveToProduct(item)
}

function openDetail(product) {
  router.push({ name: 'product-detail', params: { id: product.productId } })
}

function scrollToFeed() {
  window.setTimeout(() => {
    const el = document.getElementById('jd-feed')
    if (!el) return
    const top = el.getBoundingClientRect().top + window.scrollY - 150
    window.scrollTo({ top, behavior: 'smooth' })
  }, 60)
}

function requireLogin() {
  if (isLoggedIn.value) return true
  sessionStorage.setItem('redirectAfterLogin', route.fullPath)
  showInfo('登录后即可使用该功能')
  router.push({ name: 'login' })
  return false
}

async function toggleFavorite(product) {
  if (!requireLogin()) return
  if (favoriteBusyId.value) return
  favoriteBusyId.value = product.productId
  const wasFavorited = favoriteIds.value.has(product.productId)
  try {
    const { data } = wasFavorited
      ? await favoriteApi.remove(product.productId)
      : await favoriteApi.add(product.productId)
    if (!isOk(data)) {
      showError(data?.message || (wasFavorited ? '取消收藏失败' : '收藏失败'))
      return
    }
    const next = new Set(favoriteIds.value)
    if (wasFavorited) next.delete(product.productId)
    else next.add(product.productId)
    favoriteIds.value = next
    showSuccess(wasFavorited ? '已取消收藏' : '已加入收藏')
  } catch (err) {
    showError(resolveErrorMessage(err, wasFavorited ? '取消收藏失败' : '收藏失败'))
  } finally {
    favoriteBusyId.value = ''
  }
}

/** 快捷加购：列表没有 SKU，先取详情里的可售规格再加车 */
async function quickAdd(product, event) {
  if (!requireLogin()) return
  if (addBusyId.value) return
  addBusyId.value = product.productId
  try {
    const { data: detailData } = await productApi.detail(product.productId)
    const detail = unwrapResult(detailData)
    const skus = detail?.skus || []
    const sku = skus.find((s) => Number(s.stockAvailable) > 0) || skus[0]
    if (!sku) {
      showError('该商品暂无可售规格')
      return
    }
    const { data } = await cartApi.add({
      productId: product.productId,
      skuId: sku.skuId,
      quantity: 1,
    })
    if (!isOk(data)) {
      showError(data?.message || '加入购物车失败')
      return
    }
    showSuccess('已加入购物车')
    window.dispatchEvent(new Event('cart:updated'))
    flyToCart(event?.currentTarget)
  } catch (err) {
    showError(resolveErrorMessage(err, '加入购物车失败'))
  } finally {
    addBusyId.value = ''
  }
}

/** 加购成功后的抛物线小圆点，从按钮飞向顶部购物车 */
function flyToCart(startEl) {
  if (!startEl || typeof document === 'undefined') return
  const cartEl = document.querySelector('.jd-cart__icon')
  if (!cartEl) return
  const s = startEl.getBoundingClientRect()
  const t = cartEl.getBoundingClientRect()
  const dot = document.createElement('span')
  dot.className = 'jd-fly-dot'
  dot.style.left = `${s.left + s.width / 2}px`
  dot.style.top = `${s.top + s.height / 2}px`
  document.body.appendChild(dot)
  const dx = t.left + t.width / 2 - (s.left + s.width / 2)
  const dy = t.top + t.height / 2 - (s.top + s.height / 2)
  if (typeof dot.animate !== 'function') {
    window.setTimeout(() => dot.remove(), 300)
    return
  }
  dot
    .animate(
      [
        { transform: 'translate(0, 0) scale(1)', opacity: 1 },
        { transform: `translate(${dx * 0.55}px, ${dy - 70}px) scale(1.15)`, opacity: 1, offset: 0.55 },
        { transform: `translate(${dx}px, ${dy}px) scale(0.25)`, opacity: 0.15 },
      ],
      { duration: 720, easing: 'cubic-bezier(0.4, 0, 0.6, 1)' },
    )
    .addEventListener('finish', () => dot.remove())
  window.setTimeout(() => dot.remove(), 1200)
}

/* ---------------- 生命周期 ---------------- */
onMounted(() => {
  readQuery()
  loadCategories()
  load(false)
  loadFavorites()
})

// 顶部搜索框会改写地址栏：这里跟随路由变化重新查询
watch(
  () => route.fullPath,
  () => {
    if (route.name !== 'products') return
    if (skipRouteFullPath && route.fullPath === skipRouteFullPath) {
      skipRouteFullPath = ''
      return
    }
    readQuery()
    load(false)
  },
)

watch(isLoggedIn, loadFavorites)
</script>

<template>
  <div class="jd-store">
    <!-- 公告条：滚动播报 + 会员入口 -->
    <div class="jd-notice" v-reveal>
      <span class="jd-notice__label">公告</span>
      <div class="jd-notice__track">
        <div class="jd-notice__content">
          <span>9.9 元起的学生宿舍好物正在秒杀</span>
          <span>全场满 99 包邮，满 300 减 80</span>
          <span>卖家发布商品后需管理员审核上架</span>
          <span>本平台为课程实验项目，交易不产生真实扣款</span>
          <span>9.9 元起的学生宿舍好物正在秒杀</span>
          <span>全场满 99 包邮，满 300 减 80</span>
          <span>卖家发布商品后需管理员审核上架</span>
          <span>本平台为课程实验项目，交易不产生真实扣款</span>
        </div>
      </div>
      <RouterLink :to="{ name: 'register' }" class="jd-notice__cta">
        <StoreIcon name="coupon" :size="14" />
        新人领券
      </RouterLink>
    </div>

    <!-- 主视觉轮播 + 服务承诺 -->
    <section class="jd-hero-block">
      <HeroCarousel :slides="heroSlides" />
      <ServiceStrip />
    </section>

    <!-- 分类快捷入口 -->
    <CategoryGrid :categories="categories" :active-id="categoryId" @select="selectCategory" />

    <!-- 限时秒杀 -->
    <FlashSale :items="flashSaleItems" @select="resolveToProduct" />

    <!-- 精选好物 -->
    <CuratedShowcase :items="curatedItems" @select="resolveToProduct" />

    <!-- 全部商品 -->
    <section id="jd-feed" class="jd-feed">
      <header class="jd-feed__head">
        <div class="jd-feed__title">
          <span class="jd-feed__bar"></span>
          <div>
            <h2>全部商品</h2>
            <p v-if="!loadError">{{ formatTotal(total, '件', products.length) }}</p>
            <p v-else>商品数据暂时不可用</p>
          </div>
        </div>

        <div class="jd-feed__tools">
          <div class="jd-feed__sorts">
            <button
              v-for="tab in sortTabs"
              :key="tab.value"
              type="button"
              class="jd-feed__sort"
              :class="{ 'is-active': sort === tab.value }"
              @click="setSort(tab.value)"
            >
              {{ tab.label }}
            </button>
            <button
              type="button"
              class="jd-feed__sort"
              :class="{ 'is-active': priceSortActive }"
              @click="togglePriceSort"
            >
              价格
              <span class="jd-feed__arrows" :class="{ 'is-desc': sort === 'price_desc' }">
                <i class="up"></i><i class="down"></i>
              </span>
            </button>
          </div>

          <button type="button" class="jd-feed__filter" @click="drawerOpen = true">
            <StoreIcon name="tag" :size="15" />
            筛选
            <span v-if="activeFilters.length" class="jd-feed__filter-count">{{ activeFilters.length }}</span>
          </button>
        </div>
      </header>

      <div v-if="activeFilters.length" class="jd-feed__active">
        <span
          v-for="item in activeFilters"
          :key="item.key"
          class="jd-feed__chip"
        >
          {{ item.text }}
          <button type="button" :aria-label="`移除 ${item.text}`" @click="removeFilter(item.key)">
            <StoreIcon name="close" :size="12" />
          </button>
        </span>
        <button type="button" class="jd-feed__clear" data-testid="product-list-clear-filters" @click="resetFilters">
          清空条件
        </button>
      </div>

      <p v-if="filterError" class="jd-feed__error">{{ filterError }}</p>

      <!-- 失败态 -->
      <el-result
        v-if="loadError && !loading"
        status="error"
        title="加载商品失败"
        :sub-title="loadError"
        class="jd-feed__result"
      >
        <template #extra>
          <el-button type="primary" data-testid="product-list-retry" @click="load(false)">重试</el-button>
        </template>
      </el-result>

      <!-- 骨架屏 -->
      <div v-else-if="loading" class="jd-feed__grid" aria-busy="true" aria-live="polite">
        <div v-for="i in 8" :key="`sk-${i}`" class="jd-skeleton-card">
          <div class="jd-skeleton-card__img jd-skeleton"></div>
          <div class="jd-skeleton-card__line jd-skeleton"></div>
          <div class="jd-skeleton-card__line jd-skeleton" style="width: 60%"></div>
          <div class="jd-skeleton-card__line jd-skeleton" style="width: 40%"></div>
        </div>
      </div>

      <!-- 空态：如实说明，不伪造商品 -->
      <el-empty v-else-if="!products.length" description="还没有符合条件的商品" class="jd-feed__empty">
        <div class="space-y-3 text-center">
          <p v-if="activeFilters.length" class="text-xs text-gray-500">
            当前筛选：{{ activeFilters.map((f) => f.text).join('、') }}
          </p>
          <p class="text-xs text-gray-400">
            可以在上方「精选好物」逛逛，或换一个关键词；卖家发布并通过审核后，商品会出现在这里。
          </p>
          <el-button type="primary" plain @click="resetFilters">清空筛选条件</el-button>
        </div>
      </el-empty>

      <!-- 商品网格 -->
      <div v-else class="jd-feed__grid">
        <ProductCard
          v-for="(p, i) in products"
          :key="p.productId"
          :product="p"
          :index="i"
          :favorited="favoriteIds.has(p.productId)"
          :busy="favoriteBusyId === p.productId || addBusyId === p.productId"
          @click="openDetail"
          @favorite="toggleFavorite"
          @add="quickAdd"
        />
      </div>

      <div v-if="!loading && !loadError && totalPages > 1" class="jd-feed__pager">
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
    </section>

    <FilterDrawer
      v-model:open="drawerOpen"
      v-model:keyword="keyword"
      v-model:category-id="categoryId"
      v-model:sort="sort"
      v-model:min-price="minPrice"
      v-model:max-price="maxPrice"
      :categories="categories"
      :sort-options="sortOptions"
      :filter-error="filterError"
      @apply="applyFilters"
      @reset="resetFilters"
    />

    <BackToTop />
  </div>
</template>

<style scoped>
.jd-store {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ---------- 公告条 ---------- */
.jd-notice {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 38px;
  padding: 0 14px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--jd-line);
  overflow: hidden;
}
.jd-notice__label {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--jd-red-soft);
  color: var(--jd-red);
  font-size: 11px;
  font-weight: 700;
}
.jd-notice__track {
  flex: 1;
  overflow: hidden;
}
.jd-notice__content {
  display: flex;
  gap: 48px;
  width: max-content;
  font-size: 12px;
  color: var(--jd-ink-3);
  animation: jd-marquee 26s linear infinite;
}
.jd-notice:hover .jd-notice__content {
  animation-play-state: paused;
}
.jd-notice__cta {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--jd-red);
  font-weight: 600;
}

/* ---------- 主视觉 ---------- */
.jd-hero-block {
  display: flex;
  flex-direction: column;
  gap: 0;
}

/* ---------- 推荐流 ---------- */
.jd-feed {
  margin-top: 30px;
  scroll-margin-top: 160px;
}
.jd-feed__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.jd-feed__title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.jd-feed__bar {
  width: 4px;
  height: 32px;
  border-radius: 999px;
  background: linear-gradient(180deg, #ff6a3d, var(--jd-red));
}
.jd-feed__title h2 {
  font-size: 21px;
  font-weight: 800;
  color: var(--jd-ink);
  letter-spacing: 0.4px;
}
.jd-feed__title p {
  margin-top: 2px;
  font-size: 12px;
  color: var(--jd-ink-4);
}
.jd-feed__tools {
  display: flex;
  align-items: center;
  gap: 10px;
}
.jd-feed__sorts {
  display: inline-flex;
  padding: 3px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--jd-line);
}
.jd-feed__sort {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 14px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--jd-ink-3);
  transition: background 0.2s ease, color 0.2s ease;
}
.jd-feed__sort:hover {
  color: var(--jd-red);
}
.jd-feed__sort.is-active {
  background: var(--jd-red-soft);
  color: var(--jd-red);
  font-weight: 700;
}
.jd-feed__arrows {
  display: inline-flex;
  flex-direction: column;
  gap: 1px;
}
.jd-feed__arrows i {
  width: 0;
  height: 0;
  border-left: 3px solid transparent;
  border-right: 3px solid transparent;
}
.jd-feed__arrows .up {
  border-bottom: 4px solid #c3c7cf;
}
.jd-feed__arrows .down {
  border-top: 4px solid #c3c7cf;
}
.jd-feed__sort.is-active .jd-feed__arrows.is-desc .down {
  border-top-color: var(--jd-red);
}
.jd-feed__sort.is-active .jd-feed__arrows:not(.is-desc) .up {
  border-bottom-color: var(--jd-red);
}
.jd-feed__filter {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid var(--jd-line);
  background: #fff;
  font-size: 12px;
  color: var(--jd-ink-2);
  transition: border-color 0.2s ease, color 0.2s ease;
}
.jd-feed__filter:hover {
  border-color: var(--jd-red);
  color: var(--jd-red);
}
.jd-feed__filter-count {
  display: inline-grid;
  place-items: center;
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  border-radius: 999px;
  background: var(--jd-red);
  color: #fff;
  font-size: 10px;
}

.jd-feed__active {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.jd-feed__chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid #ffd9d5;
  font-size: 12px;
  color: var(--jd-red);
}
.jd-feed__clear {
  font-size: 12px;
  color: var(--jd-ink-4);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.jd-feed__error {
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--jd-red);
}

.jd-feed__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}
.jd-feed__empty,
.jd-feed__result {
  padding: 34px 0;
  border-radius: var(--jd-radius);
  background: #fff;
  border: 1px dashed #e3e6ec;
}
.jd-feed__pager {
  display: flex;
  justify-content: center;
  padding: 26px 0 8px;
}

/* 骨架卡片 */
.jd-skeleton-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border-radius: var(--jd-radius);
  background: #fff;
  border: 1px solid var(--jd-line);
}
.jd-skeleton-card__img {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 10px;
}
.jd-skeleton-card__line {
  height: 10px;
  border-radius: 6px;
}

@media (max-width: 1280px) {
  .jd-feed__grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 1024px) {
  .jd-feed__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .jd-feed__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .jd-notice {
    display: none;
  }
}
</style>