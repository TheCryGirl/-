<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { cartApi, favoriteApi, isOk, productApi, unwrapResult } from '../api'
import ProductCard from '../components/ProductCard.vue'
import PageHero from '../components/store/PageHero.vue'
import StoreIcon from '../components/store/StoreIcon.vue'
import { resolveErrorMessage, showError, showInfo, showSuccess } from '../components/ui/message'
import { hotKeywords } from '../data/showcase'
import { isLoggedIn } from '../stores/auth'
import { formatTotal } from '../utils/format'
import { clearSearchHistory, pushSearchHistory, readSearchHistory } from '../utils/searchHistory'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const input = ref('')
const products = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const sort = ref('default')
const loading = ref(false)
const loadError = ref('')

const history = ref([])
const recommendations = ref([])
const recLoading = ref(false)
const recNote = ref('')

const favoriteIds = ref(new Set())
const favoriteBusyId = ref('')
const addBusyId = ref('')

const sortTabs = [
  { value: 'default', label: '综合' },
  { value: 'newest', label: '最新' },
]

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const hasKeyword = computed(() => Boolean(keyword.value.trim()))
const recKeywords = computed(() => history.value.filter((item) => item !== keyword.value).slice(0, 3))

function readQuery() {
  keyword.value = typeof route.query.keyword === 'string' ? route.query.keyword : ''
  input.value = keyword.value
  page.value = 1
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: page.value, size: size.value, sort: sort.value }
    if (hasKeyword.value) params.keyword = keyword.value.trim()
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

/** 记录搜索词：结果页每次有有效关键词就写一条历史 */
function rememberKeyword() {
  if (!hasKeyword.value) return
  history.value = pushSearchHistory(keyword.value)
}

/** 推荐区：按最近搜索过的其他关键词各取少量商品，去重后展示 */
async function loadRecommendations() {
  if (!hasKeyword.value) {
    recommendations.value = []
    recNote.value = ''
    return
  }
  const keywords = recKeywords.value
  if (!keywords.length) {
    recommendations.value = []
    recNote.value = ''
    return
  }
  recLoading.value = true
  try {
    const lists = await Promise.all(
      keywords.map(async (word) => {
        try {
          const { data } = await productApi.list({ keyword: word, page: 1, size: 6 })
          const payload = unwrapResult(data)
          return (Array.isArray(payload) ? payload : payload?.list || []).map((item) => ({ ...item, __from: word }))
        } catch {
          return []
        }
      }),
    )
    const seen = new Set()
    const merged = []
    for (const item of lists.flat()) {
      if (!item?.productId || seen.has(item.productId)) continue
      seen.add(item.productId)
      merged.push(item)
    }
    recommendations.value = merged.slice(0, 10)
    recNote.value = keywords.join('、')
  } finally {
    recLoading.value = false
  }
}

async function loadFavorites() {
  if (!isLoggedIn.value) {
    favoriteIds.value = new Set()
    return
  }
  try {
    const { data } = await favoriteApi.list({ page: 1, size: 100 })
    const payload = unwrapResult(data)
    favoriteIds.value = new Set(
      (payload?.list || []).filter((item) => !item.invalid).map((item) => item.productId),
    )
  } catch {
    favoriteIds.value = new Set()
  }
}

function submitSearch() {
  const value = input.value.trim()
  router.push({ name: 'search', query: value ? { keyword: value } : {} })
}

function searchByWord(word) {
  input.value = word
  router.push({ name: 'search', query: { keyword: word } })
}

function setSort(value) {
  sort.value = value
  page.value = 1
  load()
  scrollToSection('search-results')
}

function goPage(target) {
  page.value = Math.min(totalPages.value, Math.max(1, target))
  load()
  scrollToSection('search-results')
}

/** 推荐区「换一批」：把最后一个关键词挪到最前，重新取推荐并停留在本区域 */
async function refreshRecommendations() {
  if (recKeywords.value.length > 1) {
    const rotated = [...recKeywords.value]
    rotated.push(rotated.shift())
    history.value = [...rotated, keyword.value].filter(Boolean)
  }
  await loadRecommendations()
  scrollToSection('search-recommend')
}

function scrollToSection(id) {
  window.setTimeout(() => {
    const el = document.getElementById(id)
    if (!el) return
    const top = el.getBoundingClientRect().top + window.scrollY - 150
    window.scrollTo({ top, behavior: 'smooth' })
  }, 80)
}

function openDetail(product) {
  router.push({ name: 'product-detail', params: { id: product.productId } })
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

async function quickAdd(product) {
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
    const { data } = await cartApi.add({ productId: product.productId, skuId: sku.skuId, quantity: 1 })
    if (!isOk(data)) {
      showError(data?.message || '加入购物车失败')
      return
    }
    showSuccess('已加入购物车')
    window.dispatchEvent(new Event('cart:updated'))
  } catch (err) {
    showError(resolveErrorMessage(err, '加入购物车失败'))
  } finally {
    addBusyId.value = ''
  }
}

function resetSearch() {
  input.value = ''
  router.push({ name: 'search' })
}

onMounted(() => {
  readQuery()
  history.value = readSearchHistory()
  rememberKeyword()
  load()
  loadRecommendations()
  loadFavorites()
})

watch(
  () => route.query.keyword,
  () => {
    readQuery()
    history.value = readSearchHistory()
    rememberKeyword()
    load()
    loadRecommendations()
  },
)

watch(isLoggedIn, loadFavorites)
</script>

<template>
  <div class="search-page">
    <PageHero
      :title="hasKeyword ? `搜索「${keyword}」` : '搜索商品'"
      :subtitle="hasKeyword ? '下面是与关键词相关的商品，推荐区会参考你最近搜索过的内容' : '输入关键词开始搜索，或从最近搜索里继续'"
      icon="search"
      tone="indigo"
    >
      <template #meta>
        <span v-if="hasKeyword && !loadError" class="text-xs text-[var(--jd-ink-4)]">
          {{ formatTotal(total, '件', products.length) }}
        </span>
      </template>
    </PageHero>

    <!-- 搜索框与历史 -->
    <section class="search-bar">
      <div class="search-bar__box">
        <StoreIcon name="search" :size="18" class="search-bar__icon" />
        <input
          v-model="input"
          class="search-bar__input"
          type="search"
          placeholder="搜索商品，试试「耳机」「笔记本」"
          aria-label="搜索商品"
          @keyup.enter="submitSearch"
        />
        <button type="button" class="search-bar__btn" @click="submitSearch">搜索</button>
      </div>

      <div class="search-bar__history">
        <span class="search-bar__label">最近搜索</span>
        <template v-if="history.length">
          <button
            v-for="word in history"
            :key="word"
            type="button"
            class="search-bar__chip"
            :class="{ 'is-active': word === keyword }"
            @click="searchByWord(word)"
          >
            {{ word }}
          </button>
          <button type="button" class="search-bar__clear" @click="history = clearSearchHistory()">
            清空
          </button>
        </template>
        <span v-else class="search-bar__empty">还没有搜索记录，先搜一个试试</span>
      </div>
    </section>

    <!-- 结果区 -->
    <section id="search-results" class="result-block">
      <header class="result-head">
        <div class="result-head__title">
          <span class="result-head__bar"></span>
          <h2>{{ hasKeyword ? '相关商品' : '全部商品' }}</h2>
          <span v-if="!loadError" class="result-head__count">{{ total }} 件</span>
        </div>
        <div class="result-head__tools">
          <div class="result-head__sorts">
            <button
              v-for="tab in sortTabs"
              :key="tab.value"
              type="button"
              class="result-head__sort"
              :class="{ 'is-active': sort === tab.value }"
              @click="setSort(tab.value)"
            >
              {{ tab.label }}
            </button>
            <button
              type="button"
              class="result-head__sort"
              :class="{ 'is-active': sort === 'price_asc' || sort === 'price_desc' }"
              @click="setSort(sort === 'price_asc' ? 'price_desc' : 'price_asc')"
            >
              价格
            </button>
          </div>
        </div>
      </header>

      <p v-if="loadError" class="result-error">{{ loadError }}</p>

      <div v-if="loading" class="result-grid">
        <div v-for="i in 8" :key="`sk-${i}`" class="skeleton-card">
          <div class="skeleton-card__img jd-skeleton"></div>
          <div class="skeleton-card__line jd-skeleton"></div>
          <div class="skeleton-card__line jd-skeleton" style="width: 55%"></div>
        </div>
      </div>

      <div v-else-if="!products.length" class="result-empty">
        <StoreIcon name="search" :size="40" />
        <h3>{{ hasKeyword ? `没有找到与「${keyword}」相关的商品` : '还没有商品' }}</h3>
        <p>换个关键词试试，或者从下面的热门搜索里挑一个。</p>
        <div class="result-empty__chips">
          <button v-for="word in hotKeywords" :key="word" type="button" class="search-bar__chip" @click="searchByWord(word)">
            {{ word }}
          </button>
        </div>
        <el-button v-if="hasKeyword" type="primary" plain @click="resetSearch">清空关键词</el-button>
      </div>

      <div v-else class="result-grid">
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

      <div v-if="!loading && !loadError && totalPages > 1" class="result-pager">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          :pager-count="5"
          @current-change="goPage"
        />
      </div>
    </section>

    <!-- 推荐区：只在有关键词时出现，内容来自最近搜索 -->
    <section v-if="hasKeyword" id="search-recommend" class="recommend-block">
      <header class="recommend-head">
        <div>
          <h2>为你推荐</h2>
          <p v-if="recNote">根据你最近搜索的「{{ recNote }}」推荐</p>
          <p v-else>多搜几次，这里会按你的搜索记录推荐</p>
        </div>
        <div class="recommend-head__actions">
          <button type="button" class="recommend-refresh" :disabled="recLoading || !recKeywords.length" @click="refreshRecommendations">
            <StoreIcon name="refresh" :size="14" />
            {{ recLoading ? '换新中…' : '换一批' }}
          </button>
          <button type="button" class="recommend-back" @click="scrollToSection('search-results')">
            回到结果
          </button>
        </div>
      </header>

      <div v-if="recommendations.length" class="recommend-grid">
        <ProductCard
          v-for="(p, i) in recommendations"
          :key="`rec-${p.productId}`"
          :product="p"
          :index="i"
          :favorited="favoriteIds.has(p.productId)"
          :busy="favoriteBusyId === p.productId || addBusyId === p.productId"
          @click="openDetail"
          @favorite="toggleFavorite"
          @add="quickAdd"
        />
      </div>
      <p v-else class="recommend-empty">
        还没有足够的搜索记录。搜过「耳机」「台灯」这类词之后，这里会推荐同类商品。
      </p>
    </section>
  </div>
</template>

<style scoped>
.search-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 搜索条 */
.search-bar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid var(--jd-line);
  box-shadow: var(--jd-shadow-card);
}
.search-bar__box {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 46px;
  padding: 0 6px 0 16px;
  border-radius: 999px;
  border: 2px solid var(--jd-red);
  background: #fff;
}
.search-bar__box:focus-within {
  box-shadow: 0 0 0 4px rgba(225, 37, 27, 0.12);
}
.search-bar__icon {
  color: var(--jd-red);
  flex-shrink: 0;
}
.search-bar__input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  font-size: 15px;
  color: var(--jd-ink);
  outline: none;
}
.search-bar__btn {
  height: 36px;
  padding: 0 24px;
  border-radius: 999px;
  background: linear-gradient(120deg, #ff5b4a, var(--jd-red));
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}
.search-bar__history {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
}
.search-bar__label {
  color: var(--jd-ink-4);
}
.search-bar__chip {
  padding: 4px 12px;
  border-radius: 999px;
  background: #f4f5f7;
  color: var(--jd-ink-3);
  font-size: 12px;
  transition: background-color 0.2s ease, color 0.2s ease;
}
.search-bar__chip:hover {
  background: var(--jd-red-soft);
  color: var(--jd-red);
}
.search-bar__chip.is-active {
  background: var(--jd-red-soft);
  color: var(--jd-red);
  font-weight: 600;
}
.search-bar__clear {
  color: var(--jd-ink-4);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.search-bar__empty {
  color: var(--jd-ink-4);
}

/* 结果区 */
.result-block,
.recommend-block {
  scroll-margin-top: 160px;
}
.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.result-head__title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.result-head__bar {
  width: 4px;
  height: 22px;
  border-radius: 999px;
  background: linear-gradient(180deg, #ff6a3d, var(--jd-red));
}
.result-head__title h2 {
  font-size: 20px;
  font-weight: 800;
  color: var(--jd-ink);
}
.result-head__count {
  font-size: 12px;
  color: var(--jd-ink-4);
}
.result-head__sorts {
  display: inline-flex;
  padding: 3px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--jd-line);
}
.result-head__sort {
  height: 30px;
  padding: 0 14px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--jd-ink-3);
}
.result-head__sort.is-active {
  background: var(--jd-red-soft);
  color: var(--jd-red);
  font-weight: 700;
}
.result-grid,
.recommend-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}
.result-pager {
  display: flex;
  justify-content: center;
  padding: 22px 0 6px;
}
.result-error {
  color: var(--jd-red);
  font-size: 12px;
}
.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 16px;
  border-radius: 16px;
  border: 1px dashed #e3e6ec;
  background: #fff;
  color: var(--jd-ink-4);
  text-align: center;
}
.result-empty h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--jd-ink);
}
.result-empty p {
  font-size: 12px;
}
.result-empty__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin: 6px 0 10px;
}

/* 推荐区 */
.recommend-block {
  margin-top: 6px;
  padding: 18px;
  border-radius: 18px;
  background: linear-gradient(180deg, #fff8f6, #fff 42%);
  border: 1px solid #ffe2dd;
}
.recommend-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.recommend-head h2 {
  font-size: 20px;
  font-weight: 800;
  color: var(--jd-ink);
}
.recommend-head p {
  margin-top: 3px;
  font-size: 12px;
  color: var(--jd-ink-4);
}
.recommend-head__actions {
  display: flex;
  gap: 8px;
}
.recommend-refresh,
.recommend-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 14px;
  border-radius: 999px;
  font-size: 12px;
  border: 1px solid #ffd8d4;
  background: #fff;
  color: var(--jd-red);
  font-weight: 600;
}
.recommend-refresh:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.recommend-back {
  color: var(--jd-ink-3);
  border-color: var(--jd-line);
}
.recommend-empty {
  padding: 18px;
  border-radius: 12px;
  background: #fff;
  border: 1px dashed #f0d4d0;
  font-size: 12px;
  color: var(--jd-ink-4);
}

.skeleton-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid var(--jd-line);
}
.skeleton-card__img {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 10px;
}
.skeleton-card__line {
  height: 10px;
  border-radius: 6px;
}

@media (max-width: 1280px) {
  .result-grid,
  .recommend-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 1024px) {
  .result-grid,
  .recommend-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .result-grid,
  .recommend-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>