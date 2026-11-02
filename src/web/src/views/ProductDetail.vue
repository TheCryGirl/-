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

      <div class="bg-white rounded-lg border border-gray-200 p-5 grid md:grid-cols-2 gap-6">
        <div>
          <img
            v-if="cover"
            :src="cover"
            :alt="product.title"
            class="w-full rounded-lg object-cover aspect-4/3 bg-gray-100"
          />
          <div
            v-else
            class="w-full rounded-lg aspect-4/3 bg-gray-100 flex items-center justify-center text-gray-400 text-sm"
          >
            暂无图片
          </div>
        </div>

        <div class="flex flex-col gap-3">
          <h1 class="text-xl font-semibold text-gray-900">{{ product.title }}</h1>
          <p class="text-sm text-gray-500">{{ product.subtitle }}</p>
          <p class="text-2xl font-bold text-red-600">
            ¥{{ formatPrice(selectedSku?.price ?? product.price ?? 0) }}
          </p>

          <dl class="grid grid-cols-[80px_1fr] gap-y-2 text-sm">
            <dt class="text-gray-500">分类</dt>
            <dd class="text-gray-800">{{ categoryLabel }}</dd>
            <dt class="text-gray-500">卖家</dt>
            <dd class="text-gray-800">{{ product.sellerId }}</dd>
            <dt class="text-gray-500">商品 ID</dt>
            <dd class="text-gray-800 break-all">{{ product.productId || route.params.id }}</dd>
          </dl>

          <div>
            <el-button
              :type="favorited ? 'danger' : ''"
              :plain="!favorited"
              :loading="favoriteBusy"
              @click="toggleFavorite"
            >
              {{ favorited ? '已收藏' : '收藏' }}
            </el-button>
          </div>

          <div v-if="product.skus?.length" class="space-y-2">
            <span class="text-sm text-gray-600">规格</span>
            <div class="flex flex-wrap gap-2">
              <el-button
                v-for="sku in product.skus"
                :key="sku.skuId"
                size="small"
                :type="selectedSku?.skuId === sku.skuId ? 'primary' : ''"
                :plain="selectedSku?.skuId !== sku.skuId"
                @click="selectedSku = sku"
              >
                {{ sku.specName }}
                <span class="text-xs opacity-70">（库存 {{ sku.stockAvailable ?? 0 }}）</span>
              </el-button>
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

          <div class="flex flex-wrap items-center gap-3">
            <span class="text-sm text-gray-600">数量</span>
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
            <span class="text-xs text-gray-400">
              库存 {{ selectedSku?.stockAvailable ?? '—' }}
            </span>
          </div>

          <div class="mt-2 flex flex-wrap gap-2">
            <el-button
              type="primary"
              :disabled="soldOut || !selectedSku"
              :loading="buyBusy"
              @click="buyNow"
            >
              立即购买
            </el-button>
            <el-button
              type="primary"
              plain
              :disabled="soldOut || !selectedSku"
              :loading="cartBusy"
              @click="addToCart"
            >
              加入购物车
            </el-button>
          </div>

          <el-alert
            v-if="notice"
            :type="noticeType"
            :title="notice"
            :closable="true"
            show-icon
            @close="notice = ''"
          />
        </div>
      </div>

      <div class="bg-white rounded-lg border border-gray-200 p-5 space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <h2 class="text-base font-semibold text-gray-900">
            商品评价
            <span class="text-sm font-normal text-gray-400">（{{ evalStats.total || 0 }}）</span>
          </h2>
          <div class="flex items-center gap-2 text-sm">
            <span class="text-amber-500 tracking-wide">{{ stars(evalStats.average) }}</span>
            <span class="text-gray-500">{{ Number(evalStats.average || 0).toFixed(1) }} 分</span>
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
