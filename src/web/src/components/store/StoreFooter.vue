<script setup>
import { computed } from 'vue'
import { isLoggedIn } from '../../stores/auth'
import StoreIcon from './StoreIcon.vue'

const columns = [
  {
    title: '购物指南',
    items: [
      { label: '商品市场', to: { name: 'products' } },
      { label: '购物车', to: { name: 'cart' } },
      { label: '我的订单', to: { name: 'orders' } },
      { label: '我的收藏', to: { name: 'favorites' } },
    ],
  },
  {
    title: '配送与支付',
    items: [
      { label: '配送说明', help: 'delivery' },
      { label: '支付方式', help: 'payment' },
      { label: '发票说明', help: 'payment' },
      { label: '帮助中心', help: 'all' },
    ],
  },
  {
    title: '售后服务',
    items: [
      { label: '退款与售后', to: { name: 'after-sales' } },
      { label: '取消订单', to: { name: 'orders' } },
      { label: '退换货流程', help: 'after-sales' },
      { label: '联系客服', help: 'contact' },
    ],
  },
]

function openHelp(topic = 'all') {
  window.dispatchEvent(new CustomEvent('help:open', { detail: { topic } }))
}

const year = new Date().getFullYear()
const showAccount = computed(() => isLoggedIn.value)
</script>

<template>
  <footer class="jd-footer">
    <div class="jd-container">
      <div class="jd-footer__top">
        <div class="jd-footer__brand">
          <div class="jd-footer__logo">
            <span class="jd-footer__mark">商城</span>
            <div>
              <strong>在线商城</strong>
              <small>ONLINE MALL</small>
            </div>
          </div>
          <p>
            云原生微服务架构的在线商城教学项目：注册登录、商品浏览、购物车、下单支付、售后与评价全链路。
          </p>
          <div class="jd-footer__badges">
            <span><StoreIcon name="verified" :size="14" /> 正品保障</span>
            <span><StoreIcon name="refresh" :size="14" /> 7 天无理由</span>
            <span><StoreIcon name="truck" :size="14" /> 极速发货</span>
          </div>
        </div>

        <div v-for="col in columns" :key="col.title" class="jd-footer__col">
          <h3>{{ col.title }}</h3>
          <template v-for="item in col.items" :key="item.label">
            <RouterLink v-if="item.to" :to="item.to" class="jd-footer__link">
              {{ item.label }}
            </RouterLink>
            <button v-else type="button" class="jd-footer__link" @click="openHelp(item.help)">
              {{ item.label }}
            </button>
          </template>
        </div>

        <div class="jd-footer__contact">
          <h3>客服热线</h3>
          <strong class="jd-footer__phone">400-000-0000</strong>
          <p>服务时间 9:00 - 22:00</p>
          <RouterLink v-if="!showAccount" :to="{ name: 'login' }" class="jd-footer__cta">
            登录 / 注册
          </RouterLink>
          <RouterLink v-else :to="{ name: 'profile' }" class="jd-footer__cta">
            进入个人中心
          </RouterLink>
        </div>
      </div>

      <div class="jd-footer__bottom">
        <span>© {{ year }} 在线商城 · 软件工程课程实验</span>
        <span class="jd-footer__sep">|</span>
        <span>本站为教学演示项目，不提供真实交易</span>
        <span class="jd-footer__sep">|</span>
        <span>图片来源于 Unsplash，仅用于界面演示</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.jd-footer {
  margin-top: 40px;
  padding: 34px 0 22px;
  background: #fff;
  border-top: 1px solid var(--jd-line);
}
.jd-footer__top {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) repeat(3, minmax(0, 0.8fr)) minmax(0, 1fr);
  gap: 24px;
}
.jd-footer__logo {
  display: flex;
  align-items: center;
  gap: 10px;
}
.jd-footer__mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: linear-gradient(140deg, #ff5b4a, var(--jd-red));
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}
.jd-footer__logo strong {
  display: block;
  font-size: 16px;
  color: var(--jd-ink);
}
.jd-footer__logo small {
  font-size: 9px;
  letter-spacing: 2px;
  color: var(--jd-ink-4);
}
.jd-footer__brand p {
  margin-top: 12px;
  max-width: 320px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--jd-ink-4);
}
.jd-footer__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}
.jd-footer__badges span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #f7f8fa;
  font-size: 11px;
  color: var(--jd-ink-3);
}
.jd-footer__col h3,
.jd-footer__contact h3 {
  font-size: 14px;
  font-weight: 700;
  color: var(--jd-ink);
  margin-bottom: 12px;
}
.jd-footer__link {
  display: block;
  font-size: 13px;
  line-height: 2.1;
  color: var(--jd-ink-4);
  transition: color 0.2s ease;
}
.jd-footer__link:hover {
  color: var(--jd-red);
}
.jd-footer__phone {
  display: block;
  font-size: 20px;
  font-weight: 800;
  color: var(--jd-red);
  letter-spacing: 0.5px;
  font-variant-numeric: tabular-nums;
}
.jd-footer__contact p {
  margin: 6px 0 14px;
  font-size: 11px;
  color: var(--jd-ink-4);
}
.jd-footer__cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid var(--jd-red);
  color: var(--jd-red);
  font-size: 12px;
  font-weight: 600;
  transition: background 0.2s ease, color 0.2s ease;
}
.jd-footer__cta:hover {
  background: var(--jd-red);
  color: #fff;
}
.jd-footer__bottom {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 28px;
  padding-top: 16px;
  border-top: 1px solid var(--jd-line);
  font-size: 12px;
  color: var(--jd-ink-4);
}
.jd-footer__sep {
  color: #dfe2e8;
}
@media (max-width: 1024px) {
  .jd-footer__top {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .jd-footer__top {
    grid-template-columns: 1fr;
  }
}
</style>