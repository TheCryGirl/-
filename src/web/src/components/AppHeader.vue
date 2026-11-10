<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { cartApi, productApi, unwrapResult, userApi } from '../api'
import { auth, isAdmin, isLoggedIn, isSeller, logout, setLogin } from '../stores/auth'
import { showError, showSuccess, resolveErrorMessage } from './ui/message'
import { hotKeywords } from '../data/showcase'
import StoreIcon from './store/StoreIcon.vue'

const route = useRoute()
const router = useRouter()

const menuOpen = ref(false)
const sellerOpen = ref(false)
const adminOpen = ref(false)
const catOpen = ref(false)
const catLoaded = ref(false)
const categories = ref([])
const keyword = ref('')
const cartCount = ref(0)
const scrolled = ref(false)
const activating = ref(false)

const navItems = [
  { name: 'products', label: '商品市场', icon: 'grid' },
  { name: 'cart', label: '购物车', auth: true, icon: 'cart' },
  { name: 'orders', label: '我的订单', auth: true, icon: 'truck' },
  { name: 'after-sales', label: '我的售后', auth: true, icon: 'refresh' },
]

const sellerItems = [
  { name: 'seller-products', label: '商品管理' },
  { name: 'seller-shop', label: '店铺资料' },
  { name: 'seller-shipments', label: '待发货' },
  { name: 'seller-refunds', label: '退款审核' },
  { name: 'seller-after-sales', label: '售后处理' },
]

const adminItems = [
  { name: 'admin-users', label: '用户管理' },
  { name: 'admin-shops', label: '店铺管理' },
  { name: 'admin-products', label: '商品审核' },
  { name: 'admin-refunds', label: '退款复核' },
  { name: 'admin-after-sales', label: '售后仲裁' },
  { name: 'admin-finance', label: '资金对账' },
  { name: 'admin-risk', label: '基础风控' },
  { name: 'admin-config', label: '系统配置' },
  { name: 'admin-evaluations', label: '评价管理' },
  { name: 'admin-audit-logs', label: '审计日志' },
]

const visibleNav = computed(() =>
  navItems.filter((n) => {
    if (n.auth && !isLoggedIn.value) return false
    return true
  }),
)
const userName = computed(() => auth.user?.nickname || auth.user?.account || '')
const topCategories = computed(() => categories.value.filter((c) => !c.parentId))

function childrenOf(id) {
  return categories.value.filter((c) => c.parentId === id)
}

function isActive(item) {
  return route.name === item.name
}

function handleLogout() {
  logout()
  menuOpen.value = false
  router.push({ name: 'products' })
}

function goLogin() {
  sessionStorage.setItem('redirectAfterLogin', route.fullPath)
  router.push({ name: 'login' })
}

/** 打开帮助中心弹窗（顶栏/页脚共用） */
function openHelp(topic = 'all') {
  window.dispatchEvent(new CustomEvent('help:open', { detail: { topic } }))
}

/** 搜索框与地址栏保持一致：商品页与搜索结果页都回填当前关键词 */
function syncKeywordFromRoute() {
  if (route.name !== 'products' && route.name !== 'search') return
  keyword.value = typeof route.query.keyword === 'string' ? route.query.keyword : ''
}

/** 搜索统一跳到独立的结果页 */
function goSearch(value) {
  const text = String(value || '').trim()
  router.push({ name: 'search', query: text ? { keyword: text } : {} })
}

function submitSearch() {
  goSearch(keyword.value)
}

function searchHot(word) {
  keyword.value = word
  goSearch(word)
}

/** 普通用户点击「我要卖」：先开通卖家身份（后端升级角色并换新令牌）再进入工作台 */
async function goSell() {
  if (!isLoggedIn.value) {
    sessionStorage.setItem('redirectAfterLogin', '/seller/products')
    router.push({ name: 'login' })
    return
  }
  if (isSeller.value) {
    router.push({ name: 'seller-products' })
    return
  }
  if (activating.value) return
  activating.value = true
  try {
    const { data } = await userApi.activateSeller()
    const payload = unwrapResult(data)
    if (!payload?.token || !payload?.user) {
      showError(data?.message || '开通卖家身份失败')
      return
    }
    setLogin(payload.token, payload.user)
    showSuccess('已开通卖家身份，可以发布商品了')
    router.push({ name: 'seller-products' })
  } catch (err) {
    showError(resolveErrorMessage(err, '开通卖家身份失败'))
  } finally {
    activating.value = false
  }
}

function goCategory(categoryId) {
  catOpen.value = false
  router.push({ name: 'products', query: categoryId ? { categoryId } : {} })
}

async function loadCategories() {
  if (catLoaded.value) return
  catLoaded.value = true
  try {
    const { data } = await productApi.categories()
    const payload = unwrapResult(data)
    categories.value = Array.isArray(payload) ? payload : payload?.list || []
  } catch {
    // 分类拿不到时只隐藏分类面板，不影响搜索与导航
    categories.value = []
  }
}

/** 购物车角标：登录后静默拉一次，失败就不显示，不打扰用户 */
async function loadCartCount() {
  if (!isLoggedIn.value) {
    cartCount.value = 0
    return
  }
  try {
    const { data } = await cartApi.get()
    const payload = unwrapResult(data)
    const items = payload?.items || payload?.list || []
    cartCount.value = items.reduce((sum, item) => sum + (Number(item.quantity) || 1), 0)
  } catch {
    cartCount.value = 0
  }
}

function onScroll() {
  scrolled.value = window.scrollY > 8
}

function openCategoryPanel() {
  catOpen.value = true
  loadCategories()
}

watch(() => route.fullPath, () => {
  sellerOpen.value = false
  adminOpen.value = false
  menuOpen.value = false
  catOpen.value = false
  syncKeywordFromRoute()
})
watch(isLoggedIn, loadCartCount)

onMounted(() => {
  syncKeywordFromRoute()
  loadCartCount()
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('cart:updated', loadCartCount)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('cart:updated', loadCartCount)
})
</script>

<template>
  <div class="jd-theme-header" :class="{ 'is-scrolled': scrolled }">
    <!-- 顶部工具条：配送地址与常用入口 -->
    <div class="jd-topbar">
      <div class="jd-container jd-topbar__inner">
        <div class="flex items-center gap-2 text-[12px]">
          <StoreIcon name="location" :size="14" />
          <span>配送至：河北工业大学 · 北辰校区</span>
        </div>
        <div class="flex items-center gap-4 text-[12px]">
          <RouterLink v-if="isLoggedIn" :to="{ name: 'orders' }" class="jd-topbar__link">我的订单</RouterLink>
          <RouterLink v-if="isLoggedIn" :to="{ name: 'favorites' }" class="jd-topbar__link">我的收藏</RouterLink>
          <RouterLink v-if="isLoggedIn" :to="{ name: 'browse-history' }" class="jd-topbar__link">浏览足迹</RouterLink>
          <RouterLink v-if="isSeller" :to="{ name: 'seller-products' }" class="jd-topbar__link">卖家中心</RouterLink>
          <RouterLink v-if="isAdmin" :to="{ name: 'admin-users' }" class="jd-topbar__link">管理中心</RouterLink>
          <span class="jd-topbar__divider" aria-hidden="true"></span>
          <button type="button" class="jd-topbar__link jd-topbar__btn" @click="openHelp('contact')">
            客户服务
            <span class="text-white/50">9:00 - 22:00</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 主搜索区 -->
    <div class="jd-head">
      <div class="jd-container jd-head__inner">
        <RouterLink to="/products" class="jd-logo" aria-label="校园商城首页">
          <span class="jd-logo__mark">商城</span>
          <span class="jd-logo__text">
            <strong>校园商城</strong>
            <small>CAMPUS MALL</small>
          </span>
        </RouterLink>

        <div class="jd-category" @mouseenter="openCategoryPanel" @mouseleave="catOpen = false">
          <button class="jd-category__btn" type="button" @click="openCategoryPanel">
            <StoreIcon name="menu" :size="16" />
            全部商品分类
            <StoreIcon name="chevron-down" :size="14" />
          </button>
          <transition name="jd-drop">
            <div v-if="catOpen" class="jd-category__panel">
              <div v-if="!categories.length" class="px-4 py-6 text-sm text-[var(--jd-ink-4)]">
                分类加载中，或服务暂不可用
              </div>
              <ul v-else class="jd-category__list">
                <li v-for="cat in topCategories" :key="cat.categoryId" class="jd-category__item">
                  <button type="button" class="jd-category__title" @click="goCategory(cat.categoryId)">
                    {{ cat.name }}
                    <StoreIcon name="chevron-right" :size="12" />
                  </button>
                  <div class="jd-category__children">
                    <button
                      v-for="child in childrenOf(cat.categoryId)"
                      :key="child.categoryId"
                      type="button"
                      class="jd-category__child"
                      @click="goCategory(child.categoryId)"
                    >
                      {{ child.name }}
                    </button>
                    <button type="button" class="jd-category__child jd-category__child--all" @click="goCategory(cat.categoryId)">
                      全部
                    </button>
                  </div>
                </li>
              </ul>
            </div>
          </transition>
        </div>

        <div class="jd-search">
          <div class="jd-search__box">
            <StoreIcon name="search" :size="18" class="jd-search__icon" />
            <input
              v-model="keyword"
              class="jd-search__input"
              type="search"
              placeholder="搜索商品，试试「耳机」「笔记本」"
              aria-label="搜索商品"
              @keyup.enter="submitSearch"
            />
            <button type="button" class="jd-search__btn" @click="submitSearch">搜索</button>
          </div>
          <div class="jd-search__hot">
            <span class="jd-search__hot-label">热搜</span>
            <button
              v-for="word in hotKeywords"
              :key="word"
              type="button"
              class="jd-search__hot-item"
              @click="searchHot(word)"
            >
              {{ word }}
            </button>
          </div>
        </div>

        <div class="jd-actions">
          <RouterLink :to="{ name: 'cart' }" class="jd-cart" :class="{ 'is-bump': cartCount > 0 }">
            <span class="jd-cart__icon">
              <StoreIcon name="cart" :size="20" />
              <span v-if="cartCount" class="jd-cart__badge">{{ cartCount > 99 ? '99+' : cartCount }}</span>
            </span>
            <span class="jd-cart__text">购物车</span>
          </RouterLink>

          <div v-if="isLoggedIn" class="relative">
            <button type="button" class="jd-user" @click="menuOpen = !menuOpen">
              <span class="jd-user__avatar">{{ (userName || 'U').slice(0, 1).toUpperCase() }}</span>
              <span class="jd-user__name">{{ userName }}</span>
              <StoreIcon name="chevron-down" :size="12" />
            </button>
            <transition name="jd-drop">
              <div v-if="menuOpen" class="jd-user__panel" @click="menuOpen = false">
                <RouterLink :to="{ name: 'profile' }" class="jd-user__item">个人中心</RouterLink>
                <RouterLink :to="{ name: 'orders' }" class="jd-user__item">我的订单</RouterLink>
                <RouterLink :to="{ name: 'favorites' }" class="jd-user__item">我的收藏</RouterLink>
                <RouterLink :to="{ name: 'browse-history' }" class="jd-user__item">浏览足迹</RouterLink>
                <button type="button" class="jd-user__item jd-user__item--danger" @click="handleLogout">
                  退出登录
                </button>
              </div>
            </transition>
          </div>

          <template v-else>
            <button type="button" class="jd-ghost-btn" @click="goLogin">登录</button>
            <RouterLink :to="{ name: 'register' }" class="jd-primary-btn">注册</RouterLink>
          </template>
        </div>
      </div>
    </div>

    <!-- 主导航 -->
    <nav class="jd-nav">
      <div class="jd-container jd-nav__inner">
        <RouterLink
          v-for="item in visibleNav"
          :key="item.name"
          :to="{ name: item.name }"
          class="jd-nav__link"
          :class="{ 'is-active': isActive(item) }"
        >
          <StoreIcon :name="item.icon" :size="15" />
          {{ item.label }}
        </RouterLink>

        <!-- 普通用户：一键开通卖家身份，买卖一体 -->
        <button
          v-if="isLoggedIn && !isSeller"
          type="button"
          class="jd-nav__link jd-nav__link--sell"
          :disabled="activating"
          data-testid="header-sell-entry"
          @click="goSell"
        >
          <StoreIcon name="tag" :size="15" />
          {{ activating ? '开通中…' : '我要卖' }}
        </button>

        <div v-if="isSeller" class="jd-nav__group" :class="{ 'is-open': sellerOpen }">
          <button
            type="button"
            class="jd-nav__link"
            :aria-expanded="sellerOpen"
            @click="sellerOpen = !sellerOpen"
          >
            卖家中心
            <StoreIcon name="chevron-down" :size="12" />
          </button>
          <div class="jd-nav__panel">
            <RouterLink
              v-for="item in sellerItems"
              :key="item.name"
              :to="{ name: item.name }"
              class="jd-nav__panel-item"
              @click="sellerOpen = false"
            >
              {{ item.label }}
            </RouterLink>
          </div>
        </div>

        <div v-if="isAdmin" class="jd-nav__group" :class="{ 'is-open': adminOpen }">
          <button
            type="button"
            class="jd-nav__link"
            :aria-expanded="adminOpen"
            @click="adminOpen = !adminOpen"
          >
            管理中心
            <StoreIcon name="chevron-down" :size="12" />
          </button>
          <div class="jd-nav__panel jd-nav__panel--wide">
            <RouterLink
              v-for="item in adminItems"
              :key="item.name"
              :to="{ name: item.name }"
              class="jd-nav__panel-item"
              @click="adminOpen = false"
            >
              {{ item.label }}
            </RouterLink>
          </div>
        </div>

        <span class="jd-nav__hint">
          <StoreIcon name="verified" :size="14" />
          全场正品保障 · 7 天无理由退换
        </span>
      </div>
    </nav>
  </div>
</template>

<style scoped>
.jd-theme-header {
  position: sticky;
  top: 0;
  z-index: 40;
  background: #fff;
  transition: box-shadow 0.28s ease;
}
.jd-theme-header.is-scrolled {
  box-shadow: 0 8px 24px -18px rgba(17, 24, 39, 0.4);
}

/* ---------- 顶部工具条 ---------- */
.jd-topbar {
  background: #21252b;
  color: rgba(255, 255, 255, 0.82);
}
.jd-topbar__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  font-size: 13px;
}
.jd-topbar__link {
  color: rgba(255, 255, 255, 0.75);
  transition: color 0.2s ease;
}
.jd-topbar__link:hover {
  color: #fff;
}
/* 顶栏里的按钮型入口（客户服务） */
.jd-topbar__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: rgba(255, 255, 255, 0.75);
  font-size: inherit;
  transition: color 0.2s ease;
}
.jd-topbar__btn:hover {
  color: #fff;
}
.jd-topbar__divider {
  width: 1px;
  height: 12px;
  background: rgba(255, 255, 255, 0.2);
}

/* ---------- 主搜索区 ---------- */
.jd-head {
  border-bottom: 1px solid var(--jd-line);
}
.jd-head__inner {
  display: flex;
  align-items: center;
  gap: 20px;
  padding-top: 12px;
  padding-bottom: 12px;
}
.jd-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.jd-logo__mark {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(140deg, #ff5b4a, var(--jd-red) 55%, var(--jd-red-dark));
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.5px;
  box-shadow: var(--jd-shadow-red);
}
.jd-logo__text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}
.jd-logo__text strong {
  font-size: 18px;
  letter-spacing: 0.5px;
  color: var(--jd-ink);
}
.jd-logo__text small {
  font-size: 9px;
  letter-spacing: 2px;
  color: var(--jd-ink-4);
}

/* 分类抽屉按钮 */
.jd-category {
  position: relative;
  flex-shrink: 0;
}
.jd-category__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--jd-red-soft);
  color: var(--jd-red);
  font-size: 14px;
  font-weight: 600;
  transition: background 0.2s ease;
}
.jd-category__btn:hover {
  background: #ffe3e0;
}
.jd-category__panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  width: 520px;
  max-height: 420px;
  overflow: auto;
  padding: 10px;
  border-radius: var(--jd-radius);
  background: #fff;
  border: 1px solid var(--jd-line);
  box-shadow: var(--jd-shadow-lift);
}
.jd-category__list {
  display: grid;
  gap: 4px;
}
.jd-category__item {
  padding: 8px 10px;
  border-radius: 10px;
  transition: background 0.2s ease;
}
.jd-category__item:hover {
  background: #fafafa;
}
.jd-category__title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  color: var(--jd-ink);
}
.jd-category__title:hover {
  color: var(--jd-red);
}
.jd-category__children {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.jd-category__child {
  padding: 3px 10px;
  border-radius: 999px;
  background: #f4f5f7;
  font-size: 12px;
  color: var(--jd-ink-3);
  transition:
    background 0.2s ease,
    color 0.2s ease;
}
.jd-category__child:hover {
  background: var(--jd-red-soft);
  color: var(--jd-red);
}
.jd-category__child--all {
  background: #fff;
  border: 1px dashed #f0c2be;
  color: var(--jd-red);
}

/* 搜索框 */
.jd-search {
  flex: 1;
  min-width: 0;
  max-width: 620px;
}
.jd-search__box {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 42px;
  padding: 0 4px 0 14px;
  border: 2px solid var(--jd-red);
  border-radius: 999px;
  background: #fff;
  transition: box-shadow 0.25s ease;
}
.jd-search__box:focus-within {
  box-shadow: 0 0 0 4px rgba(225, 37, 27, 0.12);
}
.jd-search__icon {
  color: var(--jd-red);
  flex-shrink: 0;
}
.jd-search__input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  font-size: 15px;
  color: var(--jd-ink);
  outline: none;
}
.jd-search__input::placeholder {
  color: #b8bcc4;
}
.jd-search__btn {
  height: 34px;
  padding: 0 22px;
  border-radius: 999px;
  background: linear-gradient(120deg, #ff5b4a, var(--jd-red));
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}
.jd-search__btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--jd-shadow-red);
}
.jd-search__hot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
  font-size: 12.5px;
  overflow: hidden;
  white-space: nowrap;
}
.jd-search__hot-label {
  color: var(--jd-ink-4);
}
.jd-search__hot-item {
  color: var(--jd-ink-3);
  transition: color 0.2s ease;
}
.jd-search__hot-item:hover {
  color: var(--jd-red);
}

/* 右侧动作区 */
.jd-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.jd-cart {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid var(--jd-line);
  background: #fff;
  color: var(--jd-ink-2);
  font-size: 13px;
  transition:
    border-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}
.jd-cart:hover {
  border-color: var(--jd-red);
  color: var(--jd-red);
  box-shadow: 0 6px 16px -12px rgba(225, 37, 27, 0.6);
}
.jd-cart__icon {
  position: relative;
  display: inline-flex;
}
.jd-cart__badge {
  position: absolute;
  top: -7px;
  right: -10px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--jd-red);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  animation: jd-count-pop 0.5s ease;
}
.jd-user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 14px 0 6px;
  border-radius: 999px;
  border: 1px solid var(--jd-line);
  background: #fff;
  font-size: 13px;
  color: var(--jd-ink-2);
  transition: border-color 0.2s ease;
}
.jd-user:hover {
  border-color: #d5d8de;
}
.jd-user__avatar {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(140deg, #ffb199, var(--jd-red));
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}
.jd-user__name {
  max-width: 88px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.jd-user__panel {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  width: 148px;
  padding: 6px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid var(--jd-line);
  box-shadow: var(--jd-shadow-lift);
}
.jd-user__item {
  display: block;
  width: 100%;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--jd-ink-2);
  text-align: left;
  transition: background 0.18s ease;
}
.jd-user__item:hover {
  background: #f5f6f8;
}
.jd-user__item--danger {
  color: var(--jd-red);
}
.jd-ghost-btn {
  height: 38px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid var(--jd-line);
  font-size: 13px;
  color: var(--jd-ink-2);
  transition: border-color 0.2s ease, color 0.2s ease;
}
.jd-ghost-btn:hover {
  border-color: var(--jd-red);
  color: var(--jd-red);
}
.jd-primary-btn {
  height: 38px;
  display: inline-flex;
  align-items: center;
  padding: 0 18px;
  border-radius: 999px;
  background: linear-gradient(120deg, #ff5b4a, var(--jd-red));
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.jd-primary-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--jd-shadow-red);
}

/* ---------- 主导航 ---------- */
.jd-nav {
  border-bottom: 1px solid var(--jd-line);
}
.jd-nav__inner {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 44px;
}
.jd-nav__link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
  height: 32px;
  border-radius: 999px;
  font-size: 14px;
  color: var(--jd-ink-2);
  transition:
    background 0.2s ease,
    color 0.2s ease;
}
.jd-nav__link:hover {
  background: #f4f5f7;
  color: var(--jd-red);
}
.jd-nav__link.is-active {
  background: var(--jd-red-soft);
  color: var(--jd-red);
  font-weight: 600;
}
/* 「我要卖」入口：和普通导航区分开，点一次即可开通卖家身份 */
.jd-nav__link--sell {
  color: var(--jd-red);
  font-weight: 600;
  background: linear-gradient(120deg, #fff5f3, #fff);
  border: 1px dashed #ffc9c2;
}
.jd-nav__link--sell:hover {
  background: var(--jd-red-soft);
  color: var(--jd-red);
}
.jd-nav__link--sell:disabled {
  opacity: 0.6;
  cursor: progress;
}
.jd-nav__group {
  position: relative;
}
.jd-nav__group:hover .jd-nav__panel,
.jd-nav__group.is-open .jd-nav__panel {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}
.jd-nav__panel {
  position: absolute;
  top: 100%;
  left: 0;
  min-width: 132px;
  padding: 6px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid var(--jd-line);
  box-shadow: var(--jd-shadow-lift);
  opacity: 0;
  visibility: hidden;
  transform: translateY(6px);
  transition: all 0.2s ease;
  z-index: 50;
}
.jd-nav__panel--wide {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: 300px;
}
.jd-nav__panel-item {
  display: block;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--jd-ink-2);
}
.jd-nav__panel-item:hover {
  background: #f5f6f8;
  color: var(--jd-red);
}
.jd-nav__hint {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--jd-ink-4);
}

/* 下拉过渡 */
.jd-drop-enter-active,
.jd-drop-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}
.jd-drop-enter-from,
.jd-drop-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 1024px) {
  .jd-head__inner {
    flex-wrap: wrap;
    gap: 12px;
  }
  .jd-search {
    order: 3;
    max-width: none;
    flex-basis: 100%;
  }
  .jd-actions {
    margin-left: auto;
  }
  .jd-nav__hint {
    display: none;
  }
}

@media (max-width: 640px) {
  .jd-topbar {
    display: none;
  }
  .jd-logo__text small {
    display: none;
  }
  .jd-logo__mark {
    width: 34px;
    height: 34px;
    border-radius: 10px;
    font-size: 11px;
  }
  .jd-head__inner {
    gap: 8px;
    padding-top: 8px;
    padding-bottom: 8px;
  }
  /* 移动端首屏高度有限，热搜词让位给搜索框与导航 */
  .jd-search__hot {
    display: none;
  }
  .jd-search__box {
    height: 38px;
  }
  .jd-nav__inner {
    height: 38px;
    overflow-x: auto;
    scrollbar-width: none;
  }
  .jd-category__panel {
    width: min(92vw, 420px);
  }
}
</style>