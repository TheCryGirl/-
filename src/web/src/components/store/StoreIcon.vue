<script setup>
import { computed } from 'vue'

/**
 * 商城前台的线性图标集。
 *
 * 项目没有引入图标库（Element Plus 的图标包未安装），这里用内联 SVG 自绘一组
 * 线条图标，避免为几个图标新增依赖，也保证离线可用。路径基于 24×24 视口。
 */
const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 20 },
  strokeWidth: { type: [Number, String], default: 1.8 },
})

const ICONS = {
  search:
    '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.2-3.2"/>',
  cart:
    '<circle cx="9" cy="20" r="1.6"/><circle cx="18" cy="20" r="1.6"/><path d="M2 3h2.2l2.5 12.2a2 2 0 0 0 2 1.6h8.7a2 2 0 0 0 2-1.6L21 7H6"/>',
  user:
    '<path d="M20 21v-1.8a4.2 4.2 0 0 0-4.2-4.2H8.2A4.2 4.2 0 0 0 4 19.2V21"/><circle cx="12" cy="7.5" r="4"/>',
  heart:
    '<path d="M20.8 4.9a5.4 5.4 0 0 0-7.7 0L12 6l-1.1-1.1a5.4 5.4 0 1 0-7.7 7.6l8.8 8.7 8.8-8.7a5.4 5.4 0 0 0 0-7.6z"/>',
  'heart-filled':
    '<path d="M20.8 4.9a5.4 5.4 0 0 0-7.7 0L12 6l-1.1-1.1a5.4 5.4 0 1 0-7.7 7.6l8.8 8.7 8.8-8.7a5.4 5.4 0 0 0 0-7.6z" fill="currentColor"/>',
  star:
    '<path d="m12 3 2.9 5.9 6.5.9-4.7 4.6 1.1 6.4-5.8-3-5.8 3 1.1-6.4L3.6 9.8l6.5-.9z"/>',
  shield:
    '<path d="M12 21.5c4.6-1.9 7.5-5.6 7.5-10.4V5.6L12 2.9 4.5 5.6v5.5c0 4.8 2.9 8.5 7.5 10.4z"/><path d="m9 11.8 2.1 2.1 4.2-4.2"/>',
  truck:
    '<path d="M2.5 5.5h11v10h-11z"/><path d="M13.5 8.5H18l3 3.2v3.8h-7.5z"/><circle cx="6" cy="18" r="1.8"/><circle cx="17" cy="18" r="1.8"/>',
  refresh:
    '<path d="M20.5 12a8.5 8.5 0 1 1-2.6-6.1"/><path d="M20.5 3.5v5h-5"/>',
  headset:
    '<path d="M4 17v-5a8 8 0 0 1 16 0v5"/><path d="M4 15h2.2a1.8 1.8 0 0 1 1.8 1.8v2.4A1.8 1.8 0 0 1 6.2 21H4z"/><path d="M20 15h-2.2a1.8 1.8 0 0 0-1.8 1.8v2.4a1.8 1.8 0 0 0 1.8 1.8H20z"/>',
  'chevron-right': '<path d="m9.5 6 6 6-6 6"/>',
  'chevron-down': '<path d="m6 9.5 6 6 6-6"/>',
  'chevron-left': '<path d="m14.5 6-6 6 6 6"/>',
  'arrow-up': '<path d="M12 19V5"/><path d="m5.5 11.5 6.5-6.5 6.5 6.5"/>',
  fire:
    '<path d="M12 22c3.9 0 7-2.9 7-6.6 0-4.5-4.3-6.5-4.3-6.5.3 2.1-1 3.2-2 3.2-1.6 0-2.4-1.7-1.5-4.3C9.2 9 6.6 11.3 5.7 13.4A8 8 0 0 0 5 15.4C5 19.1 8.1 22 12 22z"/>',
  clock:
    '<circle cx="12" cy="12" r="8.6"/><path d="M12 7.4V12l3.2 1.9"/>',
  grid:
    '<rect x="3.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.6"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.6"/>',
  gift:
    '<path d="M3.5 8.5h17V12h-17z"/><path d="M12 8.5V21"/><path d="M4.5 12v8.5h15V12"/><path d="M7.8 8.5a2.4 2.4 0 1 1 2-3.8c1.5 1.4 2.2 3.8 2.2 3.8s.7-2.4 2.2-3.8a2.4 2.4 0 1 1 2 3.8"/>',
  coupon:
    '<path d="M3 8.5V6.5h18v2a2.5 2.5 0 0 0 0 5v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2.5 2.5 0 0 0 0-5z"/><path d="M9 8.5v7" stroke-dasharray="2 2.4"/>',
  camera:
    '<path d="M21 18.5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h3.2L10 4.6h4l1.8 2.4H19a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="3.6"/>',
  location:
    '<path d="M20 10.3c0 6.2-8 11.7-8 11.7s-8-5.5-8-11.7a8 8 0 0 1 16 0z"/><circle cx="12" cy="10.3" r="2.8"/>',
  check: '<path d="m20 6.5-10 10L5 12"/>',
  sparkles:
    '<path d="m12 3 1.9 4.7 4.7 1.9-4.7 1.9L12 16.2l-1.9-4.7L5.4 9.6l4.7-1.9z"/><path d="M18.6 15.4l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z"/>',
  plus: '<path d="M12 5v14"/><path d="M5 12h14"/>',
  close: '<path d="m6 6 12 12"/><path d="m18 6-12 12"/>',
  zap: '<path d="M13.5 2.5 4 13.8h6.8L10 21.5 20 9.8h-6.9z"/>',
  menu: '<path d="M3.5 7h17"/><path d="M3.5 12h17"/><path d="M3.5 17h17"/>',
  play: '<path d="M8 5.5v13l11-6.5z"/>',
  pause: '<path d="M9 5.5v13"/><path d="M15 5.5v13"/>',
  verified:
    '<path d="m12 2.8 2.6 1.9 3.2-.2.9 3.1 2.4 2.1-1.4 2.9.4 3.2-3 1.2-1.6 2.8-3.1-.6-2.9 1.3-1.9-2.6-3-1 .1-3.2L3 13.9l2.2-2.4.1-3.2 3.1-.7z"/><path d="m9 12.2 2.1 2.1 4-4"/>',
  tag:
    '<path d="M12.6 3.4H20v7.4l-8.4 8.4a1.6 1.6 0 0 1-2.3 0L4 13.9a1.6 1.6 0 0 1 0-2.3z"/><circle cx="16.4" cy="7.2" r="1.3"/>',
  book:
    '<path d="M4 5.5A2 2 0 0 1 6 3.5h13v15.2H6a2 2 0 0 0-2 2z"/><path d="M4 18.7A2 2 0 0 1 6 20.5h13"/>',
  smartphone:
    '<rect x="6.5" y="2.5" width="11" height="19" rx="2.4"/><path d="M10.5 5.5h3"/><circle cx="12" cy="18.4" r="0.9" fill="currentColor" stroke="none"/>',
  lamp:
    '<path d="M9 3.5h6l3.4 7.5H5.6z"/><path d="M12 11v6.5"/><path d="M8.5 20.5h7"/>',
  backpack:
    '<path d="M5.5 9.5a5 5 0 0 1 5-5h3a5 5 0 0 1 5 5V20h-13z"/><path d="M9 9.5V7.6a3 3 0 0 1 6 0v1.9"/><path d="M9 14h6"/>',
  sneakers:
    '<path d="M2.8 16.6h13.6c2.1 0 3.9-1 4.8-2.4l-4.6-2.2-2.4 1.1-2.1-2.6-3.4 1.3-1.5-.9-4.4 1z"/><path d="M2.8 16.6v2.1h17.6v-2.1"/>',
  shirt:
    '<path d="M8.5 3.5 12 6l3.5-2.5 4.5 2.4-2 4.1-2-.9v9.4H6V9.1l-2 .9-2-4.1z"/>',
}

const inner = computed(() => ICONS[props.name] || ICONS.sparkles)
const px = computed(() => `${props.size}px`)
</script>

<template>
  <svg
    :width="px"
    :height="px"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
    v-html="inner"
  />
</template>