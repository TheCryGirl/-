<script setup>
import StoreIcon from './StoreIcon.vue'

defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  icon: { type: String, default: 'grid' },
  /** 主题：red / amber / indigo / teal，用于不同页面之间做区分 */
  tone: { type: String, default: 'red' },
  /** 可选：背景实拍图（个人中心等需要氛围感的页面） */
  image: { type: String, default: '' },
})
</script>

<template>
  <header
    class="page-hero"
    :class="[`page-hero--${tone}`, { 'has-image': image }]"
    :style="image ? { backgroundImage: `url(${image})` } : undefined"
  >
    <span v-if="image" class="page-hero__overlay" aria-hidden="true"></span>
    <span class="page-hero__glow" aria-hidden="true"></span>
    <span class="page-hero__watermark" aria-hidden="true">
      <StoreIcon :name="icon" :size="132" :stroke-width="1" />
    </span>

    <div class="page-hero__main">
      <h1 class="page-hero__title">{{ title }}</h1>
      <p v-if="subtitle" class="page-hero__subtitle">{{ subtitle }}</p>
      <slot name="meta" />
    </div>

    <div v-if="$slots.actions" class="page-hero__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 18px 22px;
  border-radius: 18px;
  border: 1px solid var(--jd-line);
  overflow: hidden;
  isolation: isolate;
  background: #fff;
  box-shadow: 0 1px 3px rgba(17, 24, 39, 0.04), 0 16px 34px -30px rgba(17, 24, 39, 0.5);
}

/* 主标题不再使用全局 h1 的竖条，改由背景光环承担视觉重心 */
.page-hero__title::before {
  content: none !important;
}

.page-hero__glow {
  position: absolute;
  inset: -40% -10% auto auto;
  width: 340px;
  height: 200px;
  border-radius: 50%;
  filter: blur(48px);
  opacity: 0.5;
  pointer-events: none;
  z-index: -1;
}
.page-hero__watermark {
  position: absolute;
  right: 12px;
  bottom: -34px;
  color: currentColor;
  opacity: 0.08;
  pointer-events: none;
  z-index: -1;
  transition: transform 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}
.page-hero:hover .page-hero__watermark {
  transform: translateY(-6px) rotate(-4deg);
}

.page-hero__main {
  min-width: 0;
}
.page-hero__title {
  display: block;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 0.3px;
  color: var(--jd-ink);
  line-height: 1.3;
}
.page-hero__subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: var(--jd-ink-4);
  line-height: 1.6;
}
.page-hero__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* 四套主题只改背景色与文字强调色，保证同一个页面里可读性一致 */
.page-hero--red {
  background: linear-gradient(110deg, #fff5f3, #fff 46%, #fff8f1);
  color: var(--jd-red);
}
.page-hero--red .page-hero__glow {
  background: radial-gradient(circle, rgba(225, 37, 27, 0.4), transparent 70%);
}
.page-hero--amber {
  background: linear-gradient(110deg, #fff9ed, #fff 46%, #fff4e2);
  color: #d97706;
}
.page-hero--amber .page-hero__glow {
  background: radial-gradient(circle, rgba(245, 158, 11, 0.42), transparent 70%);
}
.page-hero--indigo {
  background: linear-gradient(110deg, #f3f5ff, #fff 46%, #eef2ff);
  color: #4f46e5;
}
.page-hero--indigo .page-hero__glow {
  background: radial-gradient(circle, rgba(79, 70, 229, 0.35), transparent 70%);
}
.page-hero--teal {
  background: linear-gradient(110deg, #f0fdfa, #fff 46%, #ecfeff);
  color: #0d9488;
}
.page-hero--teal .page-hero__glow {
  background: radial-gradient(circle, rgba(13, 148, 136, 0.35), transparent 70%);
}

/* 带实拍背景图的页面标题（个人中心等） */
.page-hero.has-image {
  color: #fff;
  border-color: transparent;
  background-size: cover;
  background-position: center;
  min-height: 132px;
}
.page-hero.has-image .page-hero__title {
  color: #fff;
  text-shadow: 0 2px 16px rgba(0, 0, 0, 0.28);
}
.page-hero.has-image .page-hero__subtitle {
  color: rgba(255, 255, 255, 0.82);
}
.page-hero.has-image .page-hero__watermark {
  color: #fff;
  opacity: 0.16;
}
.page-hero__overlay {
  position: absolute;
  inset: 0;
  z-index: -1;
  background: linear-gradient(100deg, rgba(24, 20, 32, 0.82), rgba(60, 26, 22, 0.45) 60%, rgba(24, 20, 32, 0.25));
}

@media (max-width: 640px) {
  .page-hero {
    padding: 16px;
  }
  .page-hero__title {
    font-size: 18px;
  }
  .page-hero__actions {
    width: 100%;
  }
}
</style>