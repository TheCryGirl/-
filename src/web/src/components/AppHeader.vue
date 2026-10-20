<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth, isLoggedIn, logout } from '../stores/auth'
import { messageApi } from '../api'

const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)
const unread = ref(0)
let timer = null

const navItems = [
  { name: 'products', label: '商品市场' },
  { name: 'my-products', label: '我的商品', auth: true },
  { name: 'my-orders', label: '我的订单', auth: true },
  { name: 'sell', label: '发布商品', auth: true },
]

const visibleNav = computed(() => navItems.filter((n) => !n.auth || isLoggedIn.value))
const userName = computed(() => auth.user?.name || auth.user?.userId || '')

function isActive(item) {
  return route.name === item.name
}

async function refreshUnread() {
  if (!isLoggedIn.value) {
    unread.value = 0
    return
  }
  try {
    const { data } = await messageApi.unreadCount(auth.user.userId)
    unread.value = typeof data === 'number' ? data : (data?.count ?? 0)
  } catch {
    // 未读数失败不该打扰用户，静默忽略
    unread.value = 0
  }
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

onMounted(() => {
  refreshUnread()
  timer = setInterval(refreshUnread, 30000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <header class="bg-white border-b border-gray-200 sticky top-0 z-30">
    <div class="max-w-7xl mx-auto px-4 h-16 flex items-center gap-6">
      <RouterLink to="/products" class="text-lg font-bold text-blue-600 whitespace-nowrap">
        校园二手交易平台
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
      </nav>

      <template v-if="isLoggedIn">
        <RouterLink
          :to="{ name: 'messages' }"
          class="relative px-3 py-2 rounded-md text-sm text-gray-600 hover:bg-gray-100"
        >
          消息
          <span
            v-if="unread > 0"
            class="absolute -top-0.5 right-0 min-w-4 h-4 px-1 rounded-full bg-red-500 text-white text-[10px] leading-4 text-center"
          >
            {{ unread > 99 ? '99+' : unread }}
          </span>
        </RouterLink>

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
              :to="{ name: 'my-orders' }"
              class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100"
            >
              我的订单
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
