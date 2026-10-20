<script setup>
import { computed } from 'vue'

const props = defineProps({
  product: { type: Object, required: true },
  // 是否显示「我想要」按钮（列表页显示，我的商品页不显示）
  actionable: { type: Boolean, default: true },
  // 当前用户 id，用于判断是不是自己的商品
  currentUserId: { type: String, default: '' },
})

const emit = defineEmits(['want', 'click'])

const cover = computed(() => {
  const p = props.product
  const raw = p.image || p.imageUrl || p.picture
  if (!raw) return ''
  if (raw.startsWith('http') || raw.startsWith('data:')) return raw
  return `/images/${raw}`
})

const isMine = computed(
  () => props.currentUserId && String(props.product.userId) === String(props.currentUserId),
)

const priceText = computed(() => {
  const v = props.product.price
  const n = Number(v)
  return Number.isFinite(n) ? `¥${n.toFixed(2)}` : v ? `¥${v}` : '面议'
})

const statusText = computed(() => {
  const s = String(props.product.status ?? '')
  if (s === '1' || s === '在售' || s === 'ON_SALE') return '在售'
  if (s === '0' || s === '已下架' || s === 'OFF_SHELF') return '已下架'
  if (s === '2' || s === '已售出' || s === 'SOLD') return '已售出'
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
    <img v-if="cover" :src="cover" :alt="product.name" class="product-cover" loading="lazy" />
    <div v-else class="product-cover flex items-center justify-center text-gray-400 text-sm">
      暂无图片
    </div>

    <div class="p-3 flex flex-col gap-2 flex-1">
      <h3 class="text-sm font-medium text-gray-900 line-clamp-2 min-h-10">
        {{ product.name || product.title || '未命名商品' }}
      </h3>

      <p class="text-xs text-gray-500 line-clamp-2 min-h-8">
        {{ product.description || product.desc || '暂无描述' }}
      </p>

      <div class="mt-auto flex items-center justify-between gap-2">
        <span class="text-base font-semibold text-red-600">{{ priceText }}</span>
        <span class="text-[11px] px-2 py-0.5 rounded" :class="statusClass">{{ statusText }}</span>
      </div>

      <div class="flex items-center justify-between text-xs text-gray-400">
        <span>{{ product.classification || product.category || '未分类' }}</span>
        <span v-if="product.createTime || product.createdAt">
          {{ (product.createTime || product.createdAt).slice(5, 10) }}
        </span>
      </div>

      <button
        v-if="actionable && !isMine"
        class="mt-1 w-full py-1.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed"
        :disabled="statusText !== '在售'"
        @click.stop="emit('want', product)"
      >
        {{ statusText === '在售' ? '我想要' : '不可购买' }}
      </button>
    </div>
  </article>
</template>
