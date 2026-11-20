<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import StoreIcon from './StoreIcon.vue'

const props = defineProps({
  slides: { type: Array, required: true },
  interval: { type: Number, default: 5200 },
})

const index = ref(0)
const paused = ref(false)
let timer = null

const active = computed(() => props.slides[index.value] || {})

/**
 * 巨型标题按「·」拆两行：整行直排会在词中间断开（如「至 / 高直降」），
 * 语义断行既避免难看的折行，也让第二行可以带珊瑚重音。
 * 没有分隔符时整体作一行，行为与原来一致。
 */
const titleLines = computed(() => {
  const raw = String(active.value.title || '')
  const parts = raw.split('·').map((s) => s.trim()).filter(Boolean)
  return parts.length > 1 ? parts : [raw]
})

function start() {
  stop()
  timer = window.setInterval(() => {
    if (paused.value) return
    index.value = (index.value + 1) % props.slides.length
  }, props.interval)
}

function stop() {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
}

function goTo(target) {
  index.value = (target + props.slides.length) % props.slides.length
}

function pause() {
  paused.value = true
}
function resume() {
  paused.value = false
}

onMounted(start)
onBeforeUnmount(stop)
</script>

<template>
  <section
    class="jd-hero"
    :class="`jd-hero--${active.theme || 'red'}`"
    @mouseenter="pause"
    @mouseleave="resume"
    @focusin="pause"
    @focusout="resume"
  >
    <!-- 舞台纹理：细网格 + 珊瑚光晕 -->
    <span class="jd-hero__grid" aria-hidden="true"></span>
    <span class="jd-hero__glow jd-hero__glow--a" aria-hidden="true"></span>
    <span class="jd-hero__glow jd-hero__glow--b" aria-hidden="true"></span>

    <!-- 巨型序号水印：杂志式版面锚点 -->
    <span class="jd-hero__numeral" aria-hidden="true">
      {{ String(index + 1).padStart(2, '0') }}
    </span>

    <div class="jd-hero__copy" :key="active.id">
      <div class="jd-hero__eyebrow">
        <span class="accent-line jd-hero__line"></span>
        <span class="jd-hero__badge">{{ active.badge }}</span>
      </div>
      <h2 class="jd-hero__title">
        <span
          v-for="(line, li) in titleLines"
          :key="li"
          class="jd-hero__title-line"
          :class="{ 'is-accent': li > 0 }"
        >{{ line }}</span>
      </h2>
      <p class="jd-hero__subtitle">{{ active.subtitle }}</p>
      <div class="jd-hero__actions">
        <RouterLink :to="active.to" class="jd-hero__cta">
          {{ active.cta }}
          <StoreIcon name="chevron-right" :size="16" />
        </RouterLink>
        <div v-if="active.stat" class="jd-hero__stat">
          <strong>{{ active.stat.value }}</strong>
          <span>{{ active.stat.label }}</span>
        </div>
      </div>
    </div>

    <div class="jd-hero__media" :key="`m-${active.id}`">
      <div class="jd-hero__ring" aria-hidden="true"></div>
      <img :src="active.image" :alt="active.title" class="jd-hero__img" loading="eager" />
      <span class="jd-hero__float-tag">今日必买</span>
    </div>

    <div class="jd-hero__controls">
      <button type="button" class="jd-hero__arrow" aria-label="上一张" @click="goTo(index - 1)">
        <StoreIcon name="chevron-left" :size="18" />
      </button>
      <div class="jd-hero__dots">
        <button
          v-for="(slide, i) in slides"
          :key="slide.id"
          type="button"
          class="jd-hero__dot"
          :class="{ 'is-active': i === index }"
          :aria-label="`第 ${i + 1} 张`"
          :aria-current="i === index"
          @click="goTo(i)"
        >
          <span v-if="i === index" class="jd-hero__dot-progress" :style="{ animationDuration: `${interval}ms` }"></span>
        </button>
      </div>
      <button type="button" class="jd-hero__arrow" aria-label="下一张" @click="goTo(index + 1)">
        <StoreIcon name="chevron-right" :size="18" />
      </button>
    </div>

    <!-- coral 标志折线层 -->
    <svg class="zigzag-layer jd-hero__zigzag" viewBox="0 0 1200 26" preserveAspectRatio="none" aria-hidden="true">
      <polyline points="0,20 60,6 120,20 180,6 240,20 300,6 360,20 420,6 480,20 540,6 600,20 660,6 720,20 780,6 840,20 900,6 960,20 1020,6 1080,20 1140,6 1200,20" />
    </svg>
  </section>
</template>

<style scoped>
/* ============================================================
   CORAL 首屏：近黑舞台 + 巨型显示字 + 珊瑚重音
   ============================================================ */
.jd-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  align-items: center;
  gap: 24px;
  min-height: 460px;
  /* 底部留白必须容纳绝对定位的轮播控制条，否则它会压住 CTA */
  padding: 56px 56px 108px;
  border-radius: var(--c-r-lg, 28px);
  overflow: hidden;
  isolation: isolate;
  color: #fff;
  background: var(--v-grad-hero, linear-gradient(115deg, #ff4d6d, #ff7a45 52%, #ffb703));
  box-shadow: 0 30px 70px -40px rgba(255, 77, 109, 0.55);
}

/* 三套主题换鲜亮渐变：珊瑚橙 / 紫蓝 / 橙琥珀，保持高饱和明亮 */
.jd-hero--red {
  background: linear-gradient(115deg, #ff4d6d 0%, #ff7a45 52%, #ffb703 100%);
  --hero-glow: rgba(255, 255, 255, 0.4);
}
.jd-hero--dark {
  background: linear-gradient(115deg, #8b5cf6 0%, #6366f1 48%, #38bdf8 100%);
  --hero-glow: rgba(255, 255, 255, 0.34);
}
.jd-hero--amber {
  background: linear-gradient(115deg, #ff7a45 0%, #ffb703 55%, #ffd60a 100%);
  --hero-glow: rgba(255, 255, 255, 0.42);
}

/* 细网格纹理 */
.jd-hero__grid {
  position: absolute;
  inset: 0;
  z-index: -2;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.045) 1px, transparent 1px);
  background-size: 72px 72px;
  mask-image: radial-gradient(120% 100% at 20% 0%, #000 30%, transparent 78%);
}

.jd-hero__glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  z-index: -1;
  filter: blur(10px);
}
.jd-hero__glow--a {
  width: 460px;
  height: 460px;
  top: -180px;
  right: -80px;
  background: radial-gradient(circle, var(--hero-glow, rgba(232, 93, 93, 0.5)) 0%, transparent 66%);
}
.jd-hero__glow--b {
  width: 340px;
  height: 340px;
  bottom: -190px;
  left: 12%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.28) 0%, transparent 68%);
}

/* 巨型序号水印 */
.jd-hero__numeral {
  position: absolute;
  right: 40px;
  top: 18px;
  font-family: var(--c-display, 'Bebas Neue', sans-serif);
  font-size: 150px;
  line-height: 1;
  letter-spacing: 0.02em;
  color: rgba(255, 255, 255, 0.22);
  pointer-events: none;
  z-index: -1;
  user-select: none;
}

/* 切换幻灯片时只做位移、不做淡入：巨型标题必须始终满对比度，
   否则每次轮播都会出现「标题发灰」的一瞬间，削弱冲击力。 */
@keyframes coral-hero-in {
  from {
    transform: translate3d(0, 16px, 0);
  }
  to {
    transform: translate3d(0, 0, 0);
  }
}
.jd-hero__copy {
  position: relative;
  max-width: 700px;
  animation: coral-hero-in 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}
.jd-hero__eyebrow {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
}
.jd-hero__line {
  width: 44px;
  height: 4px;
  flex: none;
}
.jd-hero__badge {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--c-on-stage-muted, rgba(247, 243, 236, 0.62));
}

/* 巨型标题：中文走 900 字重专用显示字，形成真正的冲击力 */
.jd-hero__title {
  font-family: var(--c-display, 'Bebas Neue', sans-serif);
  font-size: clamp(38px, 5.6vw, 76px);
  font-weight: 900;
  line-height: 1.04;
  letter-spacing: -0.01em;
  color: #fff;
}
/* 每行独立成块：语义断行。窄屏放不下时用 balance 均分两行，
   避免末尾单字掉行（如「…的样 / 子」）这种难看的孤字。 */
.jd-hero__title-line {
  display: block;
  text-wrap: balance;
}
.jd-hero__title-line.is-accent {
  color: var(--c-accent, #e85d5d);
}
.jd-hero__subtitle {
  margin-top: 20px;
  max-width: 46ch;
  font-size: 15px;
  line-height: 1.7;
  color: var(--c-on-stage-muted, rgba(247, 243, 236, 0.62));
  /* 窄屏均分两行，避免末尾单字掉行（如「…低至 5 / 折」） */
  text-wrap: balance;
}
.jd-hero__actions {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
  margin-top: 34px;
}
.jd-hero__cta {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 52px;
  padding: 0 32px;
  border-radius: 999px;
  background: #fff;
  color: var(--c-accent-deep, #e63e5c);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.02em;
  overflow: hidden;
  box-shadow: 0 16px 34px -16px rgba(232, 93, 93, 0.8);
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1), background 0.3s ease;
}
.jd-hero__cta::after {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 40%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.5), transparent);
  animation: jd-shine 3.6s ease-in-out infinite;
}
.jd-hero__cta:hover {
  background: #1a1a1a;
  color: #fff;
  transform: translateY(-3px);
}
.jd-hero__stat {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}
.jd-hero__stat strong {
  font-family: var(--c-display, 'Bebas Neue', sans-serif);
  font-size: 34px;
  font-weight: 400;
  letter-spacing: 0.02em;
  color: #fff;
}
.jd-hero__stat span {
  margin-top: 6px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--c-on-stage-muted, rgba(247, 243, 236, 0.62));
}

/* 右侧产品图 */
.jd-hero__media {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 300px;
  animation: coral-hero-in 0.65s cubic-bezier(0.22, 1, 0.36, 1);
}
.jd-hero__ring {
  position: absolute;
  width: 330px;
  height: 330px;
  border-radius: 50%;
  border: 1px dashed rgba(255, 255, 255, 0.22);
  animation: jd-spin 30s linear infinite;
}
@keyframes jd-spin {
  to {
    transform: rotate(360deg);
  }
}
.jd-hero__img {
  position: relative;
  width: min(300px, 84%);
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow: 0 40px 70px -30px rgba(0, 0, 0, 0.8);
  animation: jd-float 7s ease-in-out infinite;
}
.jd-hero__float-tag {
  position: absolute;
  right: 2%;
  bottom: 10%;
  padding: 8px 16px;
  border-radius: 999px;
  background: #1a1a1a;
  color: #ffb703;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  box-shadow: 0 12px 26px -12px rgba(232, 93, 93, 0.9);
}

/* 控制条 */
.jd-hero__controls {
  position: absolute;
  left: 56px;
  bottom: 30px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.jd-hero__arrow {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.3);
  transition: background 0.3s ease, border-color 0.3s ease, transform 0.3s ease;
}
.jd-hero__arrow:hover {
  background: #fff;
  border-color: #fff;
  color: var(--c-accent-deep, #e63e5c);
  transform: scale(1.08);
}
.jd-hero__dots {
  display: flex;
  align-items: center;
  gap: 8px;
}
.jd-hero__dot {
  position: relative;
  width: 9px;
  height: 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.3);
  overflow: hidden;
  transition: width 0.35s ease, background 0.35s ease;
}
.jd-hero__dot.is-active {
  width: 38px;
  background: rgba(255, 255, 255, 0.24);
}
.jd-hero__dot-progress {
  position: absolute;
  inset: 0;
  background: #fff;
  transform-origin: left center;
  animation-name: jd-progress;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
}
@keyframes jd-progress {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}

/* 折线层在深色舞台上用浅色描边 */
.jd-hero__zigzag {
  height: 22px;
  opacity: 0.3;
}
.jd-hero__zigzag polyline {
  stroke: #f7f3ec;
  stroke-width: 2.5;
}

@media (max-width: 1024px) {
  .jd-hero {
    grid-template-columns: 1fr;
    min-height: 0;
    padding: 40px 28px 76px;
  }
  .jd-hero__media {
    display: none;
  }
  .jd-hero__numeral {
    font-size: 100px;
    right: 20px;
  }
  .jd-hero__controls {
    left: 28px;
  }
  .jd-hero__subtitle {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}
@media (max-width: 640px) {
  .jd-hero {
    padding: 32px 20px 72px;
    border-radius: 22px;
  }
  .jd-hero__title {
    font-size: clamp(30px, 8.6vw, 42px);
  }
  .jd-hero__actions {
    gap: 18px;
    margin-top: 26px;
  }
  .jd-hero__cta {
    height: 46px;
    padding: 0 24px;
    font-size: 14px;
  }
  .jd-hero__stat strong {
    font-size: 26px;
  }
  .jd-hero__controls {
    left: 20px;
    bottom: 24px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .jd-hero__img,
  .jd-hero__ring,
  .jd-hero__cta::after {
    animation: none;
  }
}
</style>
