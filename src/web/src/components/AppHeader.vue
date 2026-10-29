<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth, isLoggedIn, logout, isSeller, isAdmin } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)

const navItems = [
  { name: 'products', label: '商品市场' },
  { name: 'cart', label: '购物车', auth: true },
  { name: 'orders', label: '我的订单', auth: true },
  { name: 'after-sales', label: '我的售后', auth: true, buyerOnly: true },
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
    { name: 'admin-shops', label: '店铺管理' },
  { name: 'admin-after-sales', label: '售后仲裁' },
  { name: 'seller-after-sales', label: '售后处理' },
  { name: 'admin-finance', label: '资金对账' },
  { name: 'admin-risk', label: '基础风控' },
  { name: 'admin-config', label: '系统配置' },
]

const visibleNav = computed(() =>
  navItems.filter((n) => {
    if (n.auth && !isLoggedIn.value) return false
    if (n.buyerOnly && (isSeller.value || isAdmin.value)) return false
    return true
  }),
)
const userName = computed(() => auth.user?.nickname || auth.user?.account || '')

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
</script>

<template>
  <header class="bg-white border-b border-gray-200 sticky top-0 z-30">
    <div class="max-w-7xl mx-auto px-4 h-16 flex items-center gap-6">
      <RouterLink to="/products" class="text-lg font-bold text-blue-600 whitespace-nowrap">
        在线商城
      </RouterLink>

      <nav class="flex items-center gap-1 flex-1">
        <RouterLink
          v-for="item in visibleNav"
          :key="item.name"
          :to="{ name: item.name }"
          class="px-3 py-2 rounded-md text-sm transition-colors"
          :class="
            isActive(item)
              ? 'bg-blue-50 text-blue-600 font-medium'
              : 'text-gray-600 hover:bg-gray-100'
          "
        >
          {{ item.label }}
        </RouterLink>

        <!-- 卖家中心 -->
        <div v-if="isSeller" class="relative group">
          <button
            class="px-3 py-2 rounded-md text-sm transition-colors"
            :class="sellerItems.some((i) => isActive(i)) ? 'bg-blue-50 text-blue-600 font-medium' : 'text-gray-600 hover:bg-gray-100'"
          >
            卖家中心 ▾
          </button>
          <div class="absolute left-0 top-full hidden group-hover:block bg-white border border-gray-200 rounded-md shadow-lg py-1 min-w-[120px]">
            <RouterLink
              v-for="item in sellerItems"
              :key="item.name"
              :to="{ name: item.name }"
              class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
            >
              {{ item.label }}
            </RouterLink>
          </div>
        </div>

        <!-- 管理中心 -->
        <div v-if="isAdmin" class="relative group">
          <button
            class="px-3 py-2 rounded-md text-sm transition-colors"
            :class="adminItems.some((i) => isActive(i)) ? 'bg-blue-50 text-blue-600 font-medium' : 'text-gray-600 hover:bg-gray-100'"
          >
            管理中心 ▾
          </button>
          <div class="absolute left-0 top-full hidden group-hover:block bg-white border border-gray-200 rounded-md shadow-lg py-1 min-w-[120px]">
            <RouterLink
              v-for="item in adminItems"
              :key="item.name"
              :to="{ name: item.name }"
              class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
            >
              {{ item.label }}
            </RouterLink>
          </div>
        </div>
      </nav>

      <template v-if="isLoggedIn">
        <div class="relative">
          <button
            class="px-3 py-2 rounded-md text-sm text-gray-700 hover:bg-gray-100 flex items-center gap-1"
            @click="menuOpen = !menuOpen"
          >
            {{ userName }}
            <span class="text-xs text-gray-400">▾</span>
          </button>
          <div
            v-if="menuOpen"
            class="absolute right-0 mt-1 w-36 bg-white border border-gray-200 rounded-md shadow-lg py-1"
            @click="menuOpen = false"
          >
            <RouterLink
              :to="{ name: 'profile' }"
              class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
            >
              个人中心
            </RouterLink>
            <RouterLink
              :to="{ name: 'orders' }"
              class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
            >
              我的订单
            </RouterLink>
            <RouterLink
              :to="{ name: 'favorites' }"
              class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
            >
              我的收藏
            </RouterLink>
            <RouterLink
              :to="{ name: 'browse-history' }"
              class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
            >
              浏览足迹
            </RouterLink>
            <button
              class="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-gray-100"
              @click="handleLogout"
            >
              退出登录
            </button>
          </div>
        </div>
      </template>

      <template v-else>
        <button
          class="px-4 py-2 rounded-md text-sm bg-blue-600 text-white hover:bg-blue-700"
          @click="goLogin"
        >
          登录
        </button>
        <RouterLink
          :to="{ name: 'register' }"
          class="px-4 py-2 rounded-md text-sm border border-blue-600 text-blue-600 hover:bg-blue-50"
        >
          注册
        </RouterLink>
      </template>
    </div>
  </header>
</template>