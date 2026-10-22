<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
import { logout } from './stores/auth'

const router = useRouter()

/** 令牌过期（401）时清空内存登录态并回到登录页 */
function handleAuthExpired() {
  logout()
  router.push({ name: 'login' })
}

onMounted(() => window.addEventListener('auth:expired', handleAuthExpired))
onBeforeUnmount(() => window.removeEventListener('auth:expired', handleAuthExpired))
</script>

<template>
  <div class="min-h-screen flex flex-col bg-gray-50">
    <AppHeader />
    <main class="flex-1 w-full max-w-7xl mx-auto px-4 py-6">
      <RouterView />
    </main>
    <footer class="py-4 text-center text-xs text-gray-400 border-t border-gray-200">
      在线商城 · 软件工程课程实验
    </footer>
  </div>
</template>
