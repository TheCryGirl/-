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
    <!-- 装饰光斑，给纯色渐变增加层次 -->
    <span class="jd-hero__glow jd-hero__glow--a" aria-hidden="true"></span>
    <span class="jd-hero__glow jd-hero__glow--b" aria-hidden="true"></span>

    <div class="jd-hero__copy" :key="active.id">
      <span class="jd-hero__badge">{{ active.badge }}</span>
      <h2 class="jd-hero__title">{{ active.title }}</h2>
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
  </section>
</template>

<style scoped>
.jd-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  align-items: center;
  min-height: 320px;
  padding: 34px 40px;
  border-radius: var(--jd-radius-lg);
  overflow: hidden;
  color: #fff;
  isolation: isolate;
  box-shadow: var(--jd-shadow-card);
}

/* 三套主题渐变 */
.jd-hero--red {
  background:
    radial-gradient(120% 120% at 88% 12%, #ff8a6d 0%, transparent 55%),
    linear-gradient(120deg, #d81e12 0%, #e8442b 46%, #ff7a4d 100%);
}
.jd-hero--dark {
  background:
    radial-gradient(110% 110% at 85% 15%, #4b5563 0%, transparent 60%),
    linear-gradient(120deg, #1c2333 0%, #2a3448 55%, #3f4d69 100%);
}
.jd-hero--amber {
  background:
    radial-gradient(120% 120% at 86% 16%, #ffd08a 0%, transparent 58%),
    linear-gradient(120deg, #b45309 0%, #d97706 48%, #f59e0b 100%);
}

/* 光斑 */
.jd-hero__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(6px);
  opacity: 0.35;
  pointer-events: none;
  z-index: -1;
}
.jd-hero__glow--a {
  width: 280px;
  height: 280px;
  top: -120px;
  left: -60px;
  background: rgba(255, 255, 255, 0.35);
}
.jd-hero__glow--b {
  width: 200px;
  height: 200px;
  bottom: -110px;
  right: 30%;
  background: rgba(255, 255, 255, 0.18);
}

.jd-hero__copy {
  max-width: 520px;
  animation: jd-fade-up 0.55s cubic-bezier(0.22, 1, 0.36, 1);
}
.jd-hero__badge {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.3);
  font-size: 12px;
  letter-spacing: 1px;
  backdrop-filter: blur(4px);
}
.jd-hero__title {
  margin-top: 14px;
  font-size: clamp(22px, 2.4vw, 34px);
  font-weight: 800;
  letter-spacing: 0.5px;
  line-height: 1.22;
  text-shadow: 0 2px 18px rgba(0, 0, 0, 0.16);
}
.jd-hero__subtitle {
  margin-top: 12px;
  max-width: 460px;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.86);
}
.jd-hero__actions {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-top: 24px;
}
.jd-hero__cta {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 42px;
  padding: 0 22px;
  border-radius: 999px;
  background: #fff;
  color: var(--jd-red);
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.jd-hero--dark .jd-hero__cta,
.jd-hero--amber .jd-hero__cta {
  color: #1f2937;
}
.jd-hero__cta::after {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 40%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.85), transparent);
  animation: jd-shine 3.6s ease-in-out infinite;
  opacity: 0.55;
}
.jd-hero__cta:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 30px -18px rgba(0, 0, 0, 0.55);
}
.jd-hero__stat {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.jd-hero__stat strong {
  font-size: 18px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.jd-hero__stat span {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.75);
}

/* 右侧产品图 */
.jd-hero__media {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 240px;
  animation: jd-fade-up 0.7s cubic-bezier(0.22, 1, 0.36, 1);
}
.jd-hero__ring {
  position: absolute;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  border: 1px dashed rgba(255, 255, 255, 0.35);
  animation: jd-spin 26s linear infinite;
}
@keyframes jd-spin {
  to {
    transform: rotate(360deg);
  }
}
.jd-hero__img {
  position: relative;
  width: min(260px, 80%);
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 24px;
  border: 6px solid rgba(255, 255, 255, 0.22);
  box-shadow: 0 30px 60px -28px rgba(0, 0, 0, 0.6);
  animation: jd-float 6s ease-in-out infinite;
}
.jd-hero__float-tag {
  position: absolute;
  right: 4%;
  bottom: 8%;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.62);
  font-size: 11px;
  letter-spacing: 1px;
  backdrop-filter: blur(6px);
}

/* 控制条 */
.jd-hero__controls {
  position: absolute;
  right: 24px;
  bottom: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.jd-hero__arrow {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.25);
  transition: background 0.2s ease, transform 0.2s ease;
}
.jd-hero__arrow:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: scale(1.06);
}
.jd-hero__dots {
  display: flex;
  align-items: center;
  gap: 6px;
}
.jd-hero__dot {
  position: relative;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.4);
  overflow: hidden;
  transition: width 0.28s ease, background 0.28s ease;
}
.jd-hero__dot.is-active {
  width: 30px;
  background: rgba(255, 255, 255, 0.32);
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

@media (max-width: 1024px) {
  .jd-hero {
    grid-template-columns: 1fr;
    padding: 26px 24px 56px;
  }
  .jd-hero__media {
    display: none;
  }
  .jd-hero__subtitle {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
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