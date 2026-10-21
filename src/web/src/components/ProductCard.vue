<script setup>
import { computed } from 'vue'

const props = defineProps({
  product: { type: Object, required: true },
})

const emit = defineEmits(['click'])

const cover = computed(() => {
  const p = props.product
  const raw = p.mainImage || p.image
  if (!raw) return ''
  if (raw.startsWith('http') || raw.startsWith('data:')) return raw
  return `/images/${raw}`
})

const priceText = computed(() => {
  const v = props.product.price
  const n = Number(v)
  return Number.isFinite(n) ? `¥${(n / 100).toFixed(2)}` : '—'
})

const statusText = computed(() => {
  const s = String(props.product.status ?? '')
  if (s === 'ON_SALE') return '在售'
  if (s === 'OFF_SHELF') return '已下架'
  if (s === 'PENDING') return '待审核'
  if (s === 'REJECTED') return '已驳回'
  return s || '在售'
})

const statusClass = computed(() =>
  statusText.value === '在售' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500',
)
</script>

<template>
  <article
    class="bg-white rounded-lg border border-gray-200 overflow-hidden hover:shadow-md transition-shadow cursor-pointer flex flex-col"
    @click="emit('click', product)"
  >
    <img v-if="cover" :src="cover" :alt="product.title" class="product-cover" loading="lazy" />
    <div v-else class="product-cover flex items-center justify-center text-gray-400 text-sm">
      暂无图片
    </div>

    <div class="p-3 flex flex-col gap-2 flex-1">
      <h3 class="text-sm font-medium text-gray-900 line-clamp-2 min-h-10">
        {{ product.title || '未命名商品' }}
      </h3>

      <p class="text-xs text-gray-500 line-clamp-2 min-h-8">
        {{ product.subtitle || '暂无描述' }}
      </p>

      <div class="mt-auto flex items-center justify-between gap-2">
        <span class="text-base font-semibold text-red-600">{{ priceText }}</span>
        <span class="text-[11px] px-2 py-0.5 rounded" :class="statusClass">{{ statusText }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.product-cover {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  background-color: #f3f4f6;
}
</style>
