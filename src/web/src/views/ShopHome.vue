<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isOk, productApi, shopApi, unwrapResult } from '../api'
import ProductCard from '../components/ProductCard.vue'
import PageHero from '../components/store/PageHero.vue'
import { resolveErrorMessage, showError, showSuccess } from '../components/ui/message'
import { currentUserId, isLoggedIn } from '../stores/auth'
import { formatTotal } from '../utils/format'

const route = useRoute()
const router = useRouter()

const shop = ref(null)
const products = ref([])
const total = ref(0)
const page = ref(1)
const size = 12
const loading = ref(true)
const productLoading = ref(false)
const error = ref('')
const followed = ref(false)
const followBusy = ref(false)

const shopId = computed(() => String(route.params.shopId || ''))
const suspended = computed(() => String(shop.value?.status || '') === 'SUSPENDED')
const isOwnShop = computed(() => Boolean(shop.value?.sellerId) && shop.value.sellerId === currentUserId.value)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function loadShop() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await shopApi.detail(shopId.value)
    if (!isOk(data)) {
      error.value = data?.message || '店铺不存在'
      shop.value = null
      return
    }
    shop.value = unwrapResult(data)
  } catch (err) {
    error.value = resolveErrorMessage(err, '加载店铺失败')
    shop.value = null
  } finally {
    loading.value = false
  }
}

async function loadProducts() {
  productLoading.value = true
  try {
    const { data } = await productApi.list({ shopId: shopId.value, page: page.value, size })
    if (!isOk(data)) {
      products.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    products.value = payload?.list || []
    total.value = payload?.total || 0
  } catch {
    products.value = []
    total.value = 0
  } finally {
    productLoading.value = false
  }
}

async function loadFollowState() {
  if (!isLoggedIn.value) {
    followed.value = false
    return
  }
  try {
    const { data } = await shopApi.following({ page: 1, size: 50 })
    const payload = unwrapResult(data)
    followed.value = (payload?.list || []).some((item) => item.shopId === shopId.value)
  } catch {
    followed.value = false
  }
}

async function toggleFollow() {
  if (!isLoggedIn.value) {
    sessionStorage.setItem('redirectAfterLogin', route.fullPath)
    router.push({ name: 'login' })
    return
  }
  if (followBusy.value) return
  followBusy.value = true
  try {
    const { data } = followed.value
      ? await shopApi.unfollow(shopId.value)
      : await shopApi.follow(shopId.value)
    if (!isOk(data)) {
      showError(data?.message || '操作失败')
      return
    }
    followed.value = !followed.value
    showSuccess(followed.value ? '已关注该店铺' : '已取消关注')
  } catch (err) {
    showError(resolveErrorMessage(err, '操作失败'))
  } finally {
    followBusy.value = false
  }
}

function goPage(next) {
  page.value = Math.min(totalPages.value, Math.max(1, next))
  loadProducts()
  const el = document.getElementById('shop-products')
  if (el) window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - 150, behavior: 'smooth' })
}

function openDetail(product) {
  router.push({ name: 'product-detail', params: { id: product.productId } })
}

onMounted(async () => {
  await loadShop()
  if (shop.value) {
    loadProducts()
    loadFollowState()
  }
})
</script>

<template>
  <div class="space-y-4">
    <el-result v-if="error" status="error" title="无法打开店铺" :sub-title="error">
      <template #extra>
        <el-button type="primary" @click="router.push({ name: 'products' })">返回商品市场</el-button>
      </template>
    </el-result>

    <template v-else-if="shop">
      <PageHero
        :title="shop.name || '店铺主页'"
        :subtitle="shop.description || '这家店铺还没有填写简介'"
        icon="grid"
        tone="teal"
      >
        <template #meta>
          <el-tag :type="suspended ? 'danger' : 'success'" size="small">
            {{ suspended ? '暂停营业' : '正常营业' }}
          </el-tag>
          <span class="text-xs text-[var(--jd-ink-4)]">店铺 ID：{{ shop.shopId }}</span>
        </template>
        <template #actions>
          <el-button
            v-if="isLoggedIn && !isOwnShop"
            :type="followed ? '' : 'primary'"
            :loading="followBusy"
            @click="toggleFollow"
          >
            {{ followed ? '已关注' : '关注店铺' }}
          </el-button>
          <el-tag v-else-if="isOwnShop" type="info" size="large">我的店铺</el-tag>
          <el-button v-else type="primary" @click="router.push({ name: 'login' })">登录后关注</el-button>
        </template>
      </PageHero>

      <el-alert
        v-if="suspended"
        type="warning"
        :closable="false"
        show-icon
        title="店铺暂停营业"
        description="管理员已暂停该店铺，商品暂不可下单，恢复营业后可继续购买。"
      />

      <section id="shop-products" class="space-y-3">
        <div class="flex items-center justify-between">
          <h2 class="text-base font-semibold text-gray-900">店铺商品</h2>
          <span class="text-xs text-gray-400">{{ formatTotal(total, '件', products.length) }}</span>
        </div>

        <div v-if="productLoading" class="py-10 text-center text-sm text-gray-400">加载商品中…</div>
        <el-empty v-else-if="!products.length" description="该店铺暂无在售商品" />
        <div v-else class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4" :class="suspended ? 'opacity-60 grayscale' : ''">
          <ProductCard
            v-for="(p, i) in products"
            :key="p.productId"
            :product="p"
            :index="i"
            :busy="suspended"
            @click="suspended ? null : openDetail"
          />
        </div>

        <div v-if="totalPages > 1" class="flex justify-center pt-2">
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
    </template>

    <div v-else-if="loading" class="py-16 text-center text-sm text-gray-400">加载店铺中…</div>
  </div>
</template>