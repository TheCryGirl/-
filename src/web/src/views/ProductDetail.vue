<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  cartApi,
  evaluationApi,
  favoriteApi,
  historyApi,
  isOk,
  productApi,
  unwrapResult,
} from '../api'
import { isLoggedIn } from '../stores/auth'
import StoreIcon from '../components/store/StoreIcon.vue'

const route = useRoute()
const router = useRouter()

/* ---------- 1. 状态 ---------- */
const product = ref(null)
const selectedSku = ref(null)
const quantity = ref(1)
const loading = ref(false)
const loadError = ref('')
const notice = ref('')
const noticeType = ref('success')
const favorited = ref(false)
const favoriteBusy = ref(false)
const cartBusy = ref(false)
const buyBusy = ref(false)

// 商品评价（#57）：公开列表 + 平均分统计
const evalStats = ref({ total: 0, average: 0 })
const evalList = ref([])
const evalTotal = ref(0)
const evalPage = ref(1)
const evalSize = 5
const evalLoading = ref(false)

// 分类 id → 名称的映射。商品详情接口只返回 categoryId，
// 需要另取分类树把 id 翻成名字，否则页面只能显示「未分类」。
const categoryNames = ref({})

/* ---------- 2. 计算属性 ---------- */
const categoryLabel = computed(() => {
  const p = product.value
  if (!p) return ''
  if (p.categoryName) return p.categoryName
  return categoryNames.value[p.categoryId] || '未分类'
})

const cover = computed(() => {
  const raw = product.value?.mainImage || product.value?.image
  if (!raw) return ''
  if (raw.startsWith('http') || raw.startsWith('data:') || raw.startsWith('/')) return raw
  return `/images/${raw}`
})

/** 价格按「整数 + 小数」拆开排版，跟随所选规格变化 */
const priceParts = computed(() => {
  const cents = Number(selectedSku.value?.price ?? product.value?.price ?? 0)
  const [int, dec] = (cents / 100).toFixed(2).split('.')
  return { int, dec }
})

/** 当前 SKU 可购上限：库存与 999 取小，至少 1（库存为 0 时按钮禁用） */
const quantityMax = computed(() => Math.max(1, Math.min(999, selectedSku.value?.stockAvailable ?? 999)))

const soldOut = computed(() => (selectedSku.value?.stockAvailable ?? 0) <= 0)

watch(selectedSku, () => {
  quantity.value = Math.min(quantityMax.value, Math.max(1, Number(quantity.value) || 1))
})

/* ---------- 3. 方法 ---------- */
/** 拉取分类树，拍平成 id → name 的查表 */
async function loadCategoryNames() {
  try {
    const { data } = await productApi.categories()
    const payload = unwrapResult(data)
    const list = Array.isArray(payload) ? payload : payload?.list || []
    const map = {}
    const walk = (nodes) => {
      for (const node of nodes || []) {
        if (!node) continue
        if (node.categoryId) map[node.categoryId] = node.name
        if (node.children?.length) walk(node.children)
      }
    }
    walk(list)
    categoryNames.value = map
  } catch {
    // 分类拿不到不影响主流程，页面退化为显示「未分类」
  }
}

async function loadEvaluations() {
  evalLoading.value = true
  try {
    const [statsRes, listRes] = await Promise.all([
      evaluationApi.productStats(route.params.id),
      evaluationApi.byProduct(route.params.id, { page: evalPage.value, size: evalSize }),
    ])
    const stats = unwrapResult(statsRes.data)
    if (stats) evalStats.value = stats
    const payload = unwrapResult(listRes.data)
    evalList.value = payload?.list || []
    evalTotal.value = payload?.total || 0
  } catch {
    // 评价加载失败不影响商品主信息展示
  } finally {
    evalLoading.value = false
  }
}

function stars(n) {
  const value = Math.max(0, Math.min(5, Math.round(n || 0)))
  return '★'.repeat(value) + '☆'.repeat(5 - value)
}

function changeEvalPage(next) {
  evalPage.value = next
  loadEvaluations()
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await productApi.detail(route.params.id)
    if (!isOk(data)) {
      loadError.value = data?.message || '商品不存在或已下架'
      return
    }
    const productData = unwrapResult(data)
    if (!productData) {
      loadError.value = data?.message || '商品不存在或已下架'
      return
    }
    product.value = productData
    // 无 SKU 时保持 selectedSku 为空，加购/购买必须提示而不是假成功
    selectedSku.value = productData.skus?.length ? productData.skus[0] : null
    // 收藏状态与浏览足迹都是登录后的附加能力，失败不影响商品展示
    loadFavoriteState()
    recordHistory()
  } catch (err) {
    loadError.value = err.response?.status === 404 ? '商品不存在或已下架' : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/** 后端金额单位为分，展示按元 */
function formatPrice(cents) {
  const n = Number(cents)
  return (Number.isFinite(n) ? n / 100 : 0).toFixed(2)
}

function setNotice(text, type = 'success') {
  notice.value = text
  noticeType.value = type
}

function changeQuantity(delta) {
  const next = Number(quantity.value || 1) + delta
  quantity.value = Math.min(quantityMax.value, Math.max(1, next))
}

function ensureLogin() {
  if (!isLoggedIn.value) {
    sessionStorage.setItem('redirectAfterLogin', route.fullPath)
    router.push({ name: 'login' })
    return false
  }
  return true
}

async function toggleFavorite() {
  if (!ensureLogin()) return
  if (favoriteBusy.value) return
  favoriteBusy.value = true
  notice.value = ''
  try {
    const { data } = favorited.value
      ? await favoriteApi.remove(route.params.id)
      : await favoriteApi.add(route.params.id)
    if (!isOk(data)) {
      setNotice(data?.message || '收藏操作失败', 'error')
      return
    }
    favorited.value = !favorited.value
    setNotice(favorited.value ? '已加入收藏' : '已取消收藏')
  } catch (err) {
    setNotice(err.response?.data?.message || '收藏操作失败', 'error')
  } finally {
    favoriteBusy.value = false
  }
}

async function loadFavoriteState() {
  if (!isLoggedIn.value) return
  try {
    const { data } = await favoriteApi.status(route.params.id)
    const payload = unwrapResult(data)
    favorited.value = Boolean(payload?.favorited)
  } catch {
    // 收藏状态取不到不影响浏览
  }
}

function recordHistory() {
  if (!isLoggedIn.value) return
  // 足迹只用于展示，失败不打断浏览
  historyApi.record(route.params.id).catch(() => {})
}

/** 商品/SKU 可售校验：不满足时提示并返回 null，避免请求后假成功 */
function checkedSku() {
  if (!selectedSku.value) {
    setNotice('请先选择商品规格', 'warning')
    return null
  }
  if ((selectedSku.value.stockAvailable ?? 0) <= 0) {
    setNotice('该规格暂时缺货', 'warning')
    return null
  }
  return selectedSku.value
}

async function addToCart() {
  if (!ensureLogin()) return
  const sku = checkedSku()
  if (!sku) return
  if (cartBusy.value) return
  cartBusy.value = true
  notice.value = ''
  try {
    const { data } = await cartApi.add({
      productId: route.params.id,
      skuId: sku.skuId,
      quantity: quantity.value,
    })
    if (!isOk(data)) {
      setNotice(data?.message || '加入购物车失败', 'error')
      return
    }
    setNotice('已加入购物车')
  } catch (err) {
    setNotice(err.response?.data?.message || '加入购物车失败', 'error')
  } finally {
    cartBusy.value = false
  }
}

async function buyNow() {
  if (!ensureLogin()) return
  const sku = checkedSku()
  if (!sku) return
  if (buyBusy.value) return
  buyBusy.value = true
  notice.value = ''
  try {
    const desiredQuantity = quantity.value
    // 契约的下单接口只接受 cartItemIds，立即购买先入购物车再取回真实条目
    const addRes = await cartApi.add({
      productId: route.params.id,
      skuId: sku.skuId,
      quantity: desiredQuantity,
    })
    if (!isOk(addRes.data)) {
      setNotice(addRes.data?.message || '加入购物车失败', 'error')
      return
    }
    const { data } = await cartApi.get()
    const cart = unwrapResult(data)
    const item = cart?.items?.find((i) => i.skuId === sku.skuId)
    if (!item) {
      setNotice('创建购物车条目失败', 'error')
      return
    }
    // 同一 SKU 在购物车中会累加数量，立即购买只结算本次选择的数量
    if (item.quantity !== desiredQuantity) {
      await cartApi.updateQuantity(item.cartItemId, desiredQuantity)
      item.quantity = desiredQuantity
    }
    sessionStorage.setItem('checkoutItems', JSON.stringify([item]))
    router.push({ name: 'order-confirm' })
  } catch (err) {
    setNotice(err.response?.data?.message || '立即购买失败', 'error')
  } finally {
    buyBusy.value = false
  }
}

/* ---------- 4. 生命周期 ---------- */
onMounted(() => {
  loadCategoryNames()
  load()
  loadEvaluations()
})
</script>

<template>
  <div>
    <div v-if="loading" v-loading="loading" class="min-h-40" />

    <el-result v-else-if="loadError" status="error" icon="error" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" @click="router.push({ name: 'products' })">返回商品列表</el-button>
      </template>
    </el-result>

    <div v-else-if="product" class="space-y-4">
      <el-breadcrumb separator="/" class="text-sm">
        <el-breadcrumb-item :to="{ name: 'products' }">商品市场</el-breadcrumb-item>
        <el-breadcrumb-item>{{ product.title }}</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="jd-detail__top">
        <!-- 左侧主图 -->
        <div class="jd-detail__gallery">
          <div class="jd-detail__figure">
            <img v-if="cover" :src="cover" :alt="product.title" class="jd-detail__img" />
            <div v-else class="jd-detail__img jd-detail__img--empty">暂无图片</div>
          </div>
          <div class="jd-detail__thumbs">
            <span class="jd-detail__thumb is-active">
              <img v-if="cover" :src="cover" :alt="product.title" />
            </span>
            <span class="jd-detail__thumb-note">更多实拍图由卖家上传后展示</span>
          </div>
        </div>

        <!-- 右侧信息 -->
        <div class="jd-detail__info">
          <div class="jd-detail__tags">
            <span class="jd-detail__tag">包邮</span>
            <span class="jd-detail__tag jd-detail__tag--soft">7 天无理由</span>
          </div>

          <h1 class="jd-detail__title">{{ product.title }}</h1>
          <p class="jd-detail__subtitle">{{ product.subtitle }}</p>

          <div class="jd-detail__pricebox">
            <span class="jd-detail__pricelabel">商城价</span>
            <span class="jd-price jd-detail__price">
              <span class="jd-price__symbol">¥</span>
              <span class="jd-price__int">{{ priceParts.int }}</span>
              <span class="jd-price__dec">.{{ priceParts.dec }}</span>
            </span>
            <span class="jd-detail__tax">已含税 · 满 99 包邮</span>
          </div>

          <ul class="jd-detail__promises">
            <li><StoreIcon name="shield" :size="15" /> 正品保障</li>
            <li><StoreIcon name="truck" :size="15" /> 当日发货</li>
            <li><StoreIcon name="refresh" :size="15" /> 7 天无理由</li>
            <li><StoreIcon name="headset" :size="15" /> 客服 9:00-22:00</li>
          </ul>

          <dl class="jd-detail__meta">
            <dt>分类</dt>
            <dd>{{ categoryLabel }}</dd>
            <dt>卖家</dt>
            <dd>{{ product.sellerId }}</dd>
            <dt>商品编号</dt>
            <dd class="break-all">{{ product.productId || route.params.id }}</dd>
          </dl>

          <div v-if="product.skus?.length" class="jd-detail__section">
            <span class="jd-detail__section-label">选择规格</span>
            <div class="jd-detail__skus">
              <button
                v-for="sku in product.skus"
                :key="sku.skuId"
                type="button"
                class="jd-detail__sku"
                :class="{ 'is-active': selectedSku?.skuId === sku.skuId }"
                @click="selectedSku = sku"
              >
                {{ sku.specName }}
                <em>库存 {{ sku.stockAvailable ?? 0 }}</em>
              </button>
            </div>
          </div>
          <el-alert
            v-else
            type="warning"
            :closable="false"
            show-icon
            title="该商品暂无可售规格"
            description="商品未配置规格，暂时无法加入购物车或购买。"
          />

          <div class="jd-detail__section jd-detail__section--qty">
            <span class="jd-detail__section-label">购买数量</span>
            <el-input-number
              v-model="quantity"
              :min="1"
              :max="quantityMax"
              :step="1"
              step-strictly
              :precision="0"
              size="small"
              style="width: 120px"
            />
            <span class="jd-detail__stock">
              库存 {{ selectedSku?.stockAvailable ?? '—' }}
            </span>
          </div>

          <el-alert
            v-if="notice"
            :type="noticeType"
            :title="notice"
            :closable="true"
            show-icon
            @close="notice = ''"
          />

          <div class="jd-detail__actions">
            <el-button
              class="jd-detail__fav"
              :type="favorited ? 'danger' : ''"
              :plain="!favorited"
              :loading="favoriteBusy"
              @click="toggleFavorite"
            >
              <StoreIcon :name="favorited ? 'heart-filled' : 'heart'" :size="15" />
              <span>{{ favorited ? '已收藏' : '收藏' }}</span>
            </el-button>
            <el-button
              type="primary"
              plain
              class="jd-detail__btn"
              :disabled="soldOut || !selectedSku"
              :loading="cartBusy"
              @click="addToCart"
            >
              加入购物车
            </el-button>
            <el-button
              type="danger"
              class="jd-detail__btn jd-detail__btn--main"
              :disabled="soldOut || !selectedSku"
              :loading="buyBusy"
              @click="buyNow"
            >
              立即购买
            </el-button>
          </div>
        </div>
      </div>

      <div class="jd-detail__reviews">
        <div class="jd-detail__reviews-head">
          <div class="jd-detail__reviews-title">
            <h2>商品评价</h2>
            <span>（{{ evalStats.total || 0 }} 条）</span>
          </div>
          <div class="jd-detail__score">
            <strong>{{ Number(evalStats.average || 0).toFixed(1) }}</strong>
            <div>
              <span class="jd-detail__stars">{{ stars(evalStats.average) }}</span>
              <small>综合评分</small>
            </div>
          </div>
        </div>

        <div v-loading="evalLoading" class="min-h-16">
          <el-empty
            v-if="!evalLoading && !evalList.length"
            description="还没有评价，购买并确认收货后可以评价晒图"
            :image-size="80"
          />

          <div v-else class="divide-y divide-gray-100">
            <div v-for="ev in evalList" :key="ev.evaluationId" class="py-3 space-y-2">
              <div class="flex flex-wrap items-center gap-3 text-sm">
                <span class="text-gray-700 font-medium">{{ ev.buyerLabel }}</span>
                <span class="text-amber-500 tracking-wide">{{ stars(ev.rating) }}</span>
                <span class="text-xs text-gray-400">{{ ev.createdAt || '—' }}</span>
              </div>
              <p v-if="ev.content" class="text-sm text-gray-700 whitespace-pre-line">{{ ev.content }}</p>
              <div v-if="ev.images?.length" class="flex flex-wrap gap-2">
                <el-image
                  v-for="(img, i) in ev.images"
                  :key="i"
                  :src="img"
                  :preview-src-list="ev.images"
                  :initial-index="i"
                  fit="cover"
                  class="w-24 h-24 rounded border border-gray-200"
                  preview-teleported
                />
              </div>
            </div>
          </div>
        </div>

        <div v-if="evalTotal > evalSize" class="flex justify-end">
          <el-pagination
            :current-page="evalPage"
            :page-size="evalSize"
            :total="evalTotal"
            layout="prev, pager, next"
            small
            @current-change="changeEvalPage"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 商品详情：与首页同一套视觉语言（京东风格），逻辑与接口调用保持原样 */

.jd-detail__top {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  gap: 28px;
  padding: 22px;
  border-radius: var(--jd-radius-lg);
  background: #fff;
  border: 1px solid var(--jd-line);
  box-shadow: var(--jd-shadow-card);
}

/* ---------- 左侧主图 ---------- */
.jd-detail__gallery {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.jd-detail__figure {
  position: relative;
  border-radius: var(--jd-radius);
  overflow: hidden;
  background: #f4f5f7;
}
.jd-detail__img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  transition: transform 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}
.jd-detail__figure:hover .jd-detail__img {
  transform: scale(1.05);
}
.jd-detail__img--empty {
  display: grid;
  place-items: center;
  color: #b8bcc4;
  font-size: 13px;
}
.jd-detail__flag {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  background: linear-gradient(120deg, #ff6a3d, var(--jd-red));
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  box-shadow: var(--jd-shadow-red);
}
.jd-detail__thumbs {
  display: flex;
  align-items: center;
  gap: 10px;
}
.jd-detail__thumb {
  width: 58px;
  height: 58px;
  border-radius: 10px;
  overflow: hidden;
  border: 2px solid var(--jd-red);
  background: #f4f5f7;
}
.jd-detail__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.jd-detail__thumb-note {
  font-size: 11px;
  color: var(--jd-ink-4);
}

/* ---------- 右侧信息 ---------- */
.jd-detail__info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.jd-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.jd-detail__tag {
  padding: 2px 9px;
  border-radius: 6px;
  background: var(--jd-red-soft);
  color: var(--jd-red);
  font-size: 11px;
  font-weight: 600;
}
.jd-detail__tag--soft {
  background: #f4f5f7;
  color: var(--jd-ink-3);
  font-weight: 500;
}
.jd-detail__title {
  font-size: 24px;
  font-weight: 800;
  line-height: 1.4;
  color: var(--jd-ink);
  letter-spacing: 0.2px;
}
.jd-detail__subtitle {
  font-size: 14px;
  color: var(--jd-ink-3);
}
.jd-detail__pricebox {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  padding: 14px 16px;
  border-radius: var(--jd-radius);
  background: linear-gradient(100deg, #fff3f1, #fff9f2 70%, #fff);
  border: 1px solid #ffe1dd;
}
.jd-detail__pricelabel {
  font-size: 12px;
  color: var(--jd-ink-4);
}
.jd-detail__price {
  font-size: 22px;
}
.jd-detail__tax {
  margin-left: auto;
  font-size: 11px;
  color: var(--jd-ink-4);
}
.jd-detail__promises {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 13px;
  color: var(--jd-ink-3);
}
.jd-detail__promises li {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.jd-detail__promises svg {
  color: var(--jd-red);
}
.jd-detail__meta {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 8px 12px;
  padding: 12px 14px;
  border-radius: var(--jd-radius-sm);
  background: #fafbfc;
  font-size: 13px;
}
.jd-detail__meta dt {
  color: var(--jd-ink-4);
}
.jd-detail__meta dd {
  color: var(--jd-ink-2);
  min-width: 0;
}

/* 规格选择 */
.jd-detail__section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.jd-detail__section-label {
  font-size: 12px;
  color: var(--jd-ink-4);
}
.jd-detail__skus {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.jd-detail__sku {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid var(--jd-line);
  background: #fff;
  font-size: 13px;
  color: var(--jd-ink-2);
  transition: all 0.2s ease;
}
.jd-detail__sku em {
  font-style: normal;
  font-size: 11px;
  color: var(--jd-ink-4);
}
.jd-detail__sku:hover {
  border-color: var(--jd-red);
  color: var(--jd-red);
}
.jd-detail__sku.is-active {
  border-color: var(--jd-red);
  background: var(--jd-red-soft);
  color: var(--jd-red);
  font-weight: 600;
}
.jd-detail__section--qty {
  flex-direction: row;
  align-items: center;
  gap: 10px;
}
.jd-detail__stock {
  font-size: 12px;
  color: var(--jd-ink-4);
}

/* 操作区：桌面贴底，移动端吸底 */
.jd-detail__actions {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
  padding: 12px 0 2px;
  background: #fff;
}
.jd-detail__actions :deep(.el-button) {
  height: 44px;
  border-radius: 999px;
  padding: 0 26px;
  font-weight: 600;
}
.jd-detail__fav :deep(span) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.jd-detail__btn--main {
  background: linear-gradient(120deg, #ff5b4a, var(--jd-red));
  border-color: transparent;
  box-shadow: var(--jd-shadow-red);
}
.jd-detail__btn--main:hover {
  filter: brightness(1.04);
}

/* ---------- 评价区 ---------- */
.jd-detail__reviews {
  padding: 20px 22px 22px;
  border-radius: var(--jd-radius-lg);
  background: #fff;
  border: 1px solid var(--jd-line);
  box-shadow: var(--jd-shadow-card);
}
.jd-detail__reviews-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--jd-line);
}
.jd-detail__reviews-title {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.jd-detail__reviews-title h2 {
  font-size: 17px;
  font-weight: 800;
  color: var(--jd-ink);
}
.jd-detail__reviews-title span {
  font-size: 12px;
  color: var(--jd-ink-4);
}
.jd-detail__score {
  display: flex;
  align-items: center;
  gap: 10px;
}
.jd-detail__score strong {
  font-size: 26px;
  font-weight: 800;
  color: var(--jd-amber);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.jd-detail__score div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.jd-detail__stars {
  color: #ff9f1c;
  letter-spacing: 1px;
  font-size: 13px;
}
.jd-detail__score small {
  font-size: 11px;
  color: var(--jd-ink-4);
}

@media (max-width: 900px) {
  .jd-detail__top {
    grid-template-columns: 1fr;
    padding: 14px;
  }
  .jd-detail__title {
    font-size: 18px;
  }
  .jd-detail__actions {
    position: sticky;
    bottom: 0;
    padding: 10px 0;
    box-shadow: 0 -8px 20px -18px rgba(17, 24, 39, 0.6);
  }
  .jd-detail__actions :deep(.el-button) {
    flex: 1;
    padding: 0 12px;
  }
}
</style>