<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * 秒杀倒计时。
 *
 * - 传入 endTime 时倒计时到指定时刻；
 * - 不传时按「下一个整点」滚动（电商常见的整点场），结束后自动接下一场；
 * - 每秒刷新，每次数字变化触发一次轻微弹跳，避免只剩静态文字。
 */
const props = defineProps({
  endTime: { type: [Date, String, Number], default: null },
  label: { type: String, default: '距本场结束' },
  loop: { type: Boolean, default: true },
})

const emit = defineEmits(['finished'])

const target = ref(resolveTarget())
const now = ref(Date.now())
let timer = null

function nextHourBoundary(from = new Date()) {
  const d = new Date(from)
  d.setMinutes(0, 0, 0)
  d.setHours(d.getHours() + 1)
  return d
}

function resolveTarget() {
  if (props.endTime) return new Date(props.endTime)
  return nextHourBoundary()
}

const remaining = computed(() => {
  const diff = Math.max(0, target.value.getTime() - now.value)
  return Math.floor(diff / 1000)
})

const parts = computed(() => {
  const total = remaining.value
  return {
    hours: String(Math.floor(total / 3600)).padStart(2, '0'),
    minutes: String(Math.floor((total % 3600) / 60)).padStart(2, '0'),
    seconds: String(total % 60).padStart(2, '0'),
  }
})

watch(remaining, (value) => {
  if (value > 0) return
  emit('finished')
  if (props.loop) {
    target.value = nextHourBoundary(new Date(Date.now() + 1000))
  }
})

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="jd-countdown">
    <span class="jd-countdown__label">{{ label }}</span>
    <span class="jd-countdown__cell">{{ parts.hours }}</span>
    <span class="jd-countdown__sep">:</span>
    <span class="jd-countdown__cell">{{ parts.minutes }}</span>
    <span class="jd-countdown__sep">:</span>
    <span class="jd-countdown__cell jd-countdown__cell--sec" :key="parts.seconds">{{ parts.seconds }}</span>
  </div>
</template>

<style scoped>
.jd-countdown {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-variant-numeric: tabular-nums;
}
.jd-countdown__label {
  margin-right: 6px;
  font-size: 12px;
  color: var(--jd-ink-3);
}
.jd-countdown__cell {
  min-width: 26px;
  height: 26px;
  padding: 0 4px;
  border-radius: 6px;
  background: #22242a;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  line-height: 26px;
  text-align: center;
  letter-spacing: 0.5px;
}
.jd-countdown__cell--sec {
  background: linear-gradient(140deg, #ff5b4a, var(--jd-red));
  animation: jd-count-pop 0.35s ease;
}
.jd-countdown__sep {
  color: var(--jd-ink-4);
  font-weight: 700;
}
</style>