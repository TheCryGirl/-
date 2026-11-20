<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import AppHeader from './components/AppHeader.vue'
import AdminTopBar from './components/admin/AdminTopBar.vue'
import StoreFooter from './components/store/StoreFooter.vue'
import ServiceDialog from './components/store/ServiceDialog.vue'
import { logout } from './stores/auth'

const route = useRoute()
const router = useRouter()

/** 管理端是一套独立界面：顶部换成深色管理导航，不再出现商城搜索头与页脚 */
const isAdminArea = computed(() => String(route.name || '').startsWith('admin-'))
/** 卖家端与买家端保持原有界面，只复用工作区样式 */
const isWorkspace = computed(() => /^(admin|seller)-/.test(String(route.name || '')))
const workspaceLabel = computed(() => (route.meta.role === 'ADMIN' ? '管理中心' : '卖家中心'))

/**
 * 路由过渡收尾：显式把页面根元素拉回可见终态。
 *
 * 实测缺陷：page-fade 的 out-in 过渡在某些时序下会留下一个不结束的
 * opacity 过渡，过渡类已移除但计算值仍停在 0，整页内容只剩残影。
 * 该残留由动画本身持有，CSS 兜底压不住，只能在钩子里取消动画并复位。
 */
function clearPageFade(el) {
  if (!el || typeof el.getAnimations !== 'function') return
  for (const anim of el.getAnimations()) {
    const prop = anim.transitionProperty || anim.animationName || ''
    if (prop === 'opacity' || prop === 'transform') anim.cancel()
  }
  el.style.removeProperty('opacity')
  el.style.removeProperty('transform')
}

/** 令牌过期（401）时清空内存登录态并回到登录页 */
function handleAuthExpired() {
  logout()
  router.push({ name: 'login' })
}

onMounted(() => window.addEventListener('auth:expired', handleAuthExpired))
onBeforeUnmount(() => window.removeEventListener('auth:expired', handleAuthExpired))
</script>

<template>
  <el-config-provider :locale="zhCn">
    <div class="min-h-screen flex flex-col relative" :class="isAdminArea ? 'admin-area' : ''">
      <!-- 全站背景：渐变光斑 + 网格纹理，固定在内容之下 -->
      <div class="app-backdrop" aria-hidden="true"></div>

      <AdminTopBar v-if="isAdminArea" />
      <AppHeader v-else />

      <main
        class="flex-1 w-full mx-auto px-4 py-6"
        :class="[
          isAdminArea ? 'max-w-[1440px] admin-main' : 'max-w-7xl',
          isWorkspace ? 'workspace storefront-page' : 'storefront-page',
        ]"
      >
        <div v-if="isWorkspace && !isAdminArea" class="workspace-crumb">
          <span class="workspace-crumb__badge">{{ workspaceLabel }}</span>
          <span class="workspace-crumb__sep">/</span>
          <strong>{{ route.meta.title }}</strong>
          <span class="workspace-crumb__hint">数据实时同步 · 操作即时生效</span>
        </div>

        <RouterView v-slot="{ Component, route: current }">
          <transition
            name="page-fade"
            mode="out-in"
            @after-enter="clearPageFade"
            @enter-cancelled="clearPageFade"
            @after-leave="clearPageFade"
          >
            <component :is="Component" :key="current.path" />
          </transition>
        </RouterView>
      </main>

      <StoreFooter v-if="!isAdminArea" />
      <ServiceDialog />
    </div>
  </el-config-provider>
</template>

<style scoped>
/* 管理端：深色导航 + 更宽的内容区，与买家/卖家界面明显区分 */
.admin-main {
  padding-top: 22px;
}
</style>