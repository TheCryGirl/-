<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth, isLoggedIn, logout } from '../../stores/auth'
import StoreIcon from '../store/StoreIcon.vue'

const route = useRoute()
const router = useRouter()

const navItems = [
  { name: 'admin-users', label: '用户管理' },
  { name: 'admin-shops', label: '店铺管理' },
  { name: 'admin-products', label: '商品审核' },
  { name: 'admin-refunds', label: '退款复核' },
  { name: 'admin-after-sales', label: '售后仲裁' },
  { name: 'admin-finance', label: '资金对账' },
  { name: 'admin-settlements', label: '结算管理' },
  { name: 'admin-risk', label: '基础风控' },
  { name: 'admin-config', label: '系统配置' },
  { name: 'admin-evaluations', label: '评价管理' },
  { name: 'admin-audit-logs', label: '审计日志' },
  { name: 'admin-tickets', label: '客服工单' },
]

const userName = computed(() => auth.user?.nickname || auth.user?.account || '管理员')

function isActive(item) {
  return route.name === item.name
}

function handleLogout() {
  logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <header class="admin-bar">
    <div class="admin-bar__inner">
      <RouterLink :to="{ name: 'admin-users' }" class="admin-bar__brand">
        <span class="admin-bar__mark">
          <StoreIcon name="shield" :size="18" />
        </span>
        <span class="admin-bar__brand-text">
          <strong>校园商城 · 管理中心</strong>
          <small>ADMIN CONSOLE</small>
        </span>
      </RouterLink>

      <nav class="admin-bar__nav" aria-label="管理功能">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          :to="{ name: item.name }"
          class="admin-bar__link"
          :class="{ 'is-active': isActive(item) }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="admin-bar__actions">
        <RouterLink :to="{ name: 'products' }" class="admin-bar__ghost">
          返回商城
        </RouterLink>
        <template v-if="isLoggedIn">
          <span class="admin-bar__user">
            <span class="admin-bar__avatar">{{ (userName || 'A').slice(0, 1).toUpperCase() }}</span>
            {{ userName }}
          </span>
          <button type="button" class="admin-bar__logout" @click="handleLogout">退出登录</button>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.admin-bar {
  position: sticky;
  top: 0;
  z-index: 40;
  background: linear-gradient(100deg, #151a24, #232b3b 55%, #2d2433);
  color: #fff;
  box-shadow: 0 10px 30px -24px rgba(0, 0, 0, 0.9);
}
.admin-bar__inner {
  display: flex;
  align-items: center;
  gap: 18px;
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 20px;
  height: 58px;
}
.admin-bar__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.admin-bar__mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(140deg, #ff6a4d, var(--jd-red));
  box-shadow: 0 8px 18px -12px rgba(225, 37, 27, 0.9);
}
.admin-bar__brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}
.admin-bar__brand-text strong {
  font-size: 14px;
  letter-spacing: 0.3px;
}
.admin-bar__brand-text small {
  font-size: 9px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.55);
}
.admin-bar__nav {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}
.admin-bar__nav::-webkit-scrollbar {
  display: none;
}
.admin-bar__link {
  flex-shrink: 0;
  padding: 6px 11px;
  border-radius: 8px;
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.72);
  transition: background-color 0.2s ease, color 0.2s ease;
}
.admin-bar__link:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
}
.admin-bar__link.is-active {
  color: #fff;
  background: rgba(225, 37, 27, 0.85);
  font-weight: 600;
}
.admin-bar__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.admin-bar__ghost {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.82);
  transition: border-color 0.2s ease, color 0.2s ease;
}
.admin-bar__ghost:hover {
  border-color: rgba(255, 255, 255, 0.5);
  color: #fff;
}
.admin-bar__user {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.admin-bar__avatar {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  font-size: 11px;
  font-weight: 700;
}
.admin-bar__logout {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.66);
  transition: color 0.2s ease;
}
.admin-bar__logout:hover {
  color: #ff8b7b;
}

@media (max-width: 1080px) {
  .admin-bar__brand-text small {
    display: none;
  }
  .admin-bar__ghost {
    display: none;
  }
}
@media (max-width: 760px) {
  .admin-bar__inner {
    height: auto;
    flex-wrap: wrap;
    padding: 8px 12px;
    gap: 8px;
  }
  .admin-bar__nav {
    order: 3;
    flex-basis: 100%;
  }
  .admin-bar__user {
    display: none;
  }
}
</style>