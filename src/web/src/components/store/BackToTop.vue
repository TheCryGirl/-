<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import StoreIcon from './StoreIcon.vue'

const visible = ref(false)
const progress = ref(0)

function onScroll() {
  const top = window.scrollY || 0
  const height = document.documentElement.scrollHeight - window.innerHeight
  visible.value = top > 480
  progress.value = height > 0 ? Math.min(1, top / height) : 0
}

function toTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))

const ring = computed(() => {
  const circumference = 2 * Math.PI * 16
  return {
    strokeDasharray: `${circumference}`,
    strokeDashoffset: `${circumference * (1 - progress.value)}`,
  }
})
</script>

<template>
  <transition name="jd-fade">
    <button
      v-if="visible"
      type="button"
      class="jd-backtop"
      aria-label="回到顶部"
      @click="toTop"
    >
      <svg class="jd-backtop__ring" viewBox="0 0 36 36" aria-hidden="true">
        <circle cx="18" cy="18" r="16" fill="none" stroke="rgba(17,24,39,0.08)" stroke-width="2" />
        <circle
          cx="18"
          cy="18"
          r="16"
          fill="none"
          stroke="var(--jd-red)"
          stroke-width="2"
          stroke-linecap="round"
          transform="rotate(-90 18 18)"
          :style="ring"
        />
      </svg>
      <StoreIcon name="arrow-up" :size="16" />
    </button>
  </transition>
</template>

<style scoped>
.jd-backtop {
  position: fixed;
  right: 26px;
  bottom: 34px;
  z-index: 45;
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: #fff;
  color: var(--jd-ink-2);
  box-shadow: 0 10px 24px -14px rgba(17, 24, 39, 0.6);
  transition:
    transform 0.25s ease,
    color 0.25s ease;
}
.jd-backtop:hover {
  transform: translateY(-3px);
  color: var(--jd-red);
}
.jd-backtop__ring {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  transition: stroke-dashoffset 0.2s linear;
}
.jd-fade-enter-active,
.jd-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.jd-fade-enter-from,
.jd-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>