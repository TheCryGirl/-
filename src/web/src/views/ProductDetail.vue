<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { cartApi, isOk, productApi, unwrapResult } from '../api'
import { isLoggedIn } from '../stores/auth'

const route = useRoute()
const router = useRouter()

const product = ref(null)
const selectedSku = ref(null)
const quantity = ref(1)
const loading = ref(true)
const error = ref('')
const notice = ref('')

// 分类 id → 名称的映射。商品详情接口只返回 categoryId，
// 需要另取分类树把 id 翻成名字，否则页面只能显示「未分类」。
const categoryNames = ref({})

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

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await productApi.detail(route.params.id)
    const productData = unwrapResult(data)
    if (!productData) {
      error.value = data?.message || '商品不存在或已下架'
      return
    }
    product.value = productData
    if (product.value?.skus?.length) {
      selectedSku.value = product.value.skus[0]
    }
  } catch (err) {
    error.value = err.response?.status === 404 ? '商品不存在或已下架' : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function formatPrice(cents) {
  return (cents / 100).toFixed(2)
}

function maxQuantity() {
  return Math.min(999, selectedSku.value?.stockAvailable ?? 999)
}

function changeQuantity(delta) {
  const next = quantity.value + delta
  quantity.value = Math.min(maxQuantity(), Math.max(1, next))
}

function ensureLogin() {
  if (!isLoggedIn.value) {
    sessionStorage.setItem('redirectAfterLogin', route.fullPath)
    router.push({ name: 'login' })
    return false
  }
  return true
}

async function addToCart() {
  if (!ensureLogin()) return
  if (!selectedSku.value) {
    notice.value = '请选择规格'
    return
  }
  if ((selectedSku.value.stockAvailable ?? 0) <= 0) {
    notice.value = '该规格暂时缺货'
    return
  }
  try {
    const { data } = await cartApi.add({ productId: route.params.id, skuId: selectedSku.value.skuId, quantity: quantity.value })
    notice.value = isOk(data) ? '已加入购物车' : data?.message || '加入购物车失败'
  } catch (err) {
    notice.value = err.response?.data?.message || '加入购物车失败'
  }
}

async function buyNow() {
  if (!ensureLogin()) return
  if (!selectedSku.value) {
    notice.value = '请选择规格'
    return
  }
  if ((selectedSku.value.stockAvailable ?? 0) <= 0) {
    notice.value = '该规格暂时缺货'
    return
  }
  try {
    const desiredQuantity = quantity.value
    // 契约的下单接口只接受 cartItemIds，立即购买先入购物车再取回真实条目
    const addRes = await cartApi.add({ productId: route.params.id, skuId: selectedSku.value.skuId, quantity: desiredQuantity })
    if (!isOk(addRes.data)) {
      notice.value = addRes.data?.message || '加入购物车失败'
      return
    }
    const { data } = await cartApi.get()
    const cart = unwrapResult(data)
    const item = cart?.items?.find((i) => i.skuId === selectedSku.value.skuId)
    if (!item) {
      notice.value = '创建购物车条目失败'
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
    notice.value = err.response?.data?.message || '立即购买失败'
  }
}

onMounted(() => {
  loadCategoryNames()
  load()
})
</script>

<template>
  <div>
    <div v-if="loading" class="py-20 text-center text-gray-400 text-sm">加载中…</div>

    <div v-else-if="error" class="py-20 text-center space-y-3">
      <p class="text-gray-500">{{ error }}</p>
      <RouterLink :to="{ name: 'products' }" class="text-blue-600 text-sm hover:underline">
        返回商品列表
      </RouterLink>
    </div>

    <div v-else-if="product" class="space-y-4">
      <nav class="text-sm text-gray-400">
        <RouterLink :to="{ name: 'products' }" class="hover:text-blue-600">商品市场</RouterLink>
        <span class="mx-2">/</span>
        <span class="text-gray-600">{{ product.title }}</span>
      </nav>

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
          </dl>

          <div v-if="product.skus?.length" class="space-y-2">
            <span class="text-sm text-gray-600">规格</span>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="sku in product.skus"
                :key="sku.skuId"
                class="px-3 py-1.5 text-sm border rounded-md"
                :class="selectedSku?.skuId === sku.skuId ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-gray-300 text-gray-700 hover:bg-gray-50'"
                @click="selectedSku = sku"
              >
                {{ sku.specName }}
              </button>
            </div>
          </div>

          <div class="flex items-center gap-3">
            <span class="text-sm text-gray-600">数量</span>
            <div class="flex items-center gap-2">
              <button
                class="w-7 h-7 rounded border border-gray-300 text-gray-600 hover:bg-gray-100 disabled:opacity-40"
                :disabled="quantity <= 1"
                @click="changeQuantity(-1)"
              >
                −
              </button>
              <input
                v-model.number="quantity"
                type="number"
                min="1"
                :max="maxQuantity()"
                class="w-14 text-center text-sm border border-gray-300 rounded py-1"
                @blur="quantity = Math.min(maxQuantity(), Math.max(1, Number(quantity) || 1))"
              />
              <button
                class="w-7 h-7 rounded border border-gray-300 text-gray-600 hover:bg-gray-100 disabled:opacity-40"
                :disabled="quantity >= maxQuantity()"
                @click="changeQuantity(1)"
              >
                +
              </button>
            </div>
            <span class="text-xs text-gray-400">
              库存 {{ selectedSku?.stockAvailable ?? '—' }}
            </span>
          </div>

          <div class="mt-2 flex gap-2">
            <button
              class="px-6 py-2.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
              :disabled="(selectedSku?.stockAvailable ?? 0) <= 0"
              @click="buyNow"
            >
              立即购买
            </button>
            <button
              class="px-6 py-2.5 rounded-md border border-blue-600 text-blue-600 text-sm hover:bg-blue-50 disabled:opacity-40"
              :disabled="(selectedSku?.stockAvailable ?? 0) <= 0"
              @click="addToCart"
            >
              加入购物车
            </button>
          </div>

          <p v-if="notice" class="text-sm rounded-md px-3 py-2" :class="notice.includes('失败') ? 'text-red-600 bg-red-50' : 'text-green-600 bg-green-50'">
            {{ notice }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
