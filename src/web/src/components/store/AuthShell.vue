<script setup>
import StoreIcon from './StoreIcon.vue'

defineProps({
  badge: { type: String, default: '在线商城' },
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  image: { type: String, default: '/media/bg/auth-panel.jpg' },
  bullets: { type: Array, default: () => [] },
  wide: { type: Boolean, default: false },
})
</script>

<template>
  <div class="auth-shell">
    <!-- 左侧品牌图：桌面端展示，移动端隐藏 -->
    <aside class="auth-shell__aside" :style="{ backgroundImage: `url(${image})` }">
      <div class="auth-shell__overlay"></div>
      <div class="auth-shell__aside-inner">
        <div class="auth-shell__brand">
          <span class="auth-shell__mark">商城</span>
          <div>
            <strong>在线商城</strong>
            <small>ONLINE MALL</small>
          </div>
        </div>

        <h2 class="auth-shell__slogan">把好物与品质生活<br />放进同一个购物车</h2>

        <ul class="auth-shell__bullets">
          <li v-for="item in bullets" :key="item">
            <StoreIcon name="check" :size="14" />
            {{ item }}
          </li>
        </ul>

        <div class="auth-shell__float auth-shell__float--a">
          <StoreIcon name="shield" :size="16" />
          <div><strong>正品保障</strong><small>假一赔十</small></div>
        </div>
        <div class="auth-shell__float auth-shell__float--b">
          <StoreIcon name="truck" :size="16" />
          <div><strong>当日发货</strong><small>满 99 包邮</small></div>
        </div>
        <div class="auth-shell__float auth-shell__float--c">
          <StoreIcon name="star" :size="16" />
          <div><strong>4.9 分</strong><small>同学真实评价</small></div>
        </div>
      </div>
    </aside>

    <!-- 右侧表单 -->
    <section class="auth-shell__main">
      <div class="auth-shell__card" :class="{ 'is-wide': wide }">
        <span class="auth-shell__badge">{{ badge }}</span>
        <h1 class="auth-shell__title">{{ title }}</h1>
        <p class="auth-shell__subtitle">{{ subtitle }}</p>

        <slot />

        <div class="auth-shell__footer">
          <slot name="footer" />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  max-width: 980px;
  margin: 8px auto 0;
  border-radius: 24px;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--jd-line);
  box-shadow: 0 30px 70px -50px rgba(17, 24, 39, 0.55);
  min-height: 560px;
}

/* ---------- 左侧 ---------- */
.auth-shell__aside {
  position: relative;
  background-size: cover;
  background-position: center;
  isolation: isolate;
  overflow: hidden;
}
.auth-shell__overlay {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(160deg, rgba(225, 37, 27, 0.88), rgba(120, 18, 12, 0.92) 55%, rgba(29, 20, 36, 0.94)),
    radial-gradient(80% 60% at 80% 10%, rgba(255, 166, 87, 0.5), transparent 70%);
  mix-blend-mode: multiply;
}
.auth-shell__aside-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  padding: 34px 34px 30px;
  color: #fff;
}
.auth-shell__brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.auth-shell__mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.35);
  font-size: 12px;
  font-weight: 700;
  backdrop-filter: blur(6px);
}
.auth-shell__brand strong {
  display: block;
  font-size: 16px;
  letter-spacing: 0.5px;
}
.auth-shell__brand small {
  font-size: 9px;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.7);
}
.auth-shell__slogan {
  margin-top: 12px;
  font-size: 26px;
  font-weight: 800;
  line-height: 1.45;
  letter-spacing: 0.5px;
  text-shadow: 0 6px 24px rgba(0, 0, 0, 0.25);
}
.auth-shell__bullets {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.9);
}
.auth-shell__bullets li {
  display: flex;
  align-items: center;
  gap: 8px;
}
.auth-shell__bullets svg {
  flex: none;
  padding: 2px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
}
.auth-shell__float {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.24);
  backdrop-filter: blur(10px);
  font-size: 11px;
  animation: jd-float 7s ease-in-out infinite;
}
.auth-shell__float div {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.auth-shell__float small {
  color: rgba(255, 255, 255, 0.66);
}
.auth-shell__float--a {
  right: 26px;
  bottom: 126px;
}
.auth-shell__float--b {
  left: 26px;
  bottom: 70px;
  animation-delay: 1.2s;
}
.auth-shell__float--c {
  right: 40px;
  bottom: 40px;
  animation-delay: 2.1s;
}

/* ---------- 右侧 ---------- */
.auth-shell__main {
  display: grid;
  place-items: center;
  padding: 34px 36px;
  background:
    radial-gradient(46rem 20rem at 110% -10%, rgba(225, 37, 27, 0.06), transparent 60%),
    #fff;
}
.auth-shell__card {
  width: 100%;
  max-width: 360px;
  animation: jd-fade-up 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
}
.auth-shell__card.is-wide {
  max-width: 430px;
}
.auth-shell__badge {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--jd-red-soft);
  color: var(--jd-red);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.auth-shell__title {
  margin-top: 12px;
  font-size: 26px;
  font-weight: 800;
  color: var(--jd-ink);
  letter-spacing: 0.3px;
  display: block;
}
.auth-shell__title::before {
  content: none;
}
.auth-shell__subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: var(--jd-ink-4);
  line-height: 1.7;
}
.auth-shell__card :deep(.el-form) {
  margin-top: 22px;
}
.auth-shell__card :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--jd-ink-2);
  padding-bottom: 4px;
}
.auth-shell__card :deep(.el-input__wrapper) {
  height: 42px;
  border-radius: 10px;
}
.auth-shell__card :deep(.el-button) {
  height: 44px;
  border-radius: 999px;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.auth-shell__footer {
  margin-top: 18px;
  font-size: 13px;
  color: var(--jd-ink-4);
  text-align: center;
}
.auth-shell__footer :deep(a) {
  color: var(--jd-red);
  font-weight: 600;
}
.auth-shell__footer :deep(a:hover) {
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 900px) {
  .auth-shell {
    grid-template-columns: 1fr;
    min-height: 0;
    margin-top: 0;
  }
  .auth-shell__aside {
    display: none;
  }
  .auth-shell__main {
    padding: 26px 20px 30px;
  }
}
</style>