<script setup>
import { computed } from 'vue'
import StoreIcon from './store/StoreIcon.vue'

const props = defineProps({
  product: { type: Object, required: true },
  /** 已收藏状态由父级维护，卡片只负责展示与触发 */
  favorited: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  index: { type: Number, default: 0 },
})

const emit = defineEmits(['click', 'favorite', 'add'])

const cover = computed(() => {
  const p = props.product
  const raw = p.mainImage || p.image
  if (!raw) return ''
  if (raw.startsWith('http') || raw.startsWith('data:') || raw.startsWith('/')) return raw
  return `/images/${raw}`
})

const priceParts = computed(() => {
  const n = Number(props.product.price)
  if (!Number.isFinite(n)) return null
  const yuan = (n / 100).toFixed(2)
  const [int, dec] = yuan.split('.')
  return { int, dec }
})

const statusText = computed(() => {
  const s = String(props.product.status ?? '')
  if (s === 'ON_SALE') return '在售'
  if (s === 'OFF_SHELF') return '已下架'
  if (s === 'PENDING') return '待审核'
  if (s === 'REJECTED') return '已驳回'
  return s || '在售'
})

const statusType = computed(() => {
  switch (props.product.status) {
    case 'ON_SALE':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'REJECTED':
      return 'danger'
    case 'OFF_SHELF':
      return 'info'
    default:
      return 'info'
  }
})

const stock = computed(() => Number(props.product.stock))

const onSale = computed(() => (props.product.status ?? 'ON_SALE') === 'ON_SALE')
</script>

<template>
  <article
    class="jd-card"
    v-reveal="{ delay: Math.min(index, 11) * 45 }"
    @click="emit('click', product)"
  >
    <div class="jd-card__media">
      <img v-if="cover" :src="cover" :alt="product.title" class="jd-card__img" loading="lazy" />
      <div v-else class="jd-card__img jd-card__img--empty">
        <StoreIcon name="tag" :size="26" />
        <span>暂无图片</span>
      </div>

      <div class="jd-card__tags">
        <span class="jd-card__tag">包邮</span>
      </div>

      <div class="jd-card__quick">
        <button
          type="button"
          class="jd-card__quick-btn"
          :class="{ 'is-on': favorited }"
          :aria-pressed="favorited"
          :aria-label="favorited ? '取消收藏' : '收藏'"
          :disabled="busy"
          @click.stop="emit('favorite', product)"
        >
          <StoreIcon :name="favorited ? 'heart-filled' : 'heart'" :size="16" />
        </button>
        <button
          type="button"
          class="jd-card__quick-btn"
          aria-label="加入购物车"
          :disabled="busy || !onSale"
          @click.stop="emit('add', product, $event)"
        >
          <StoreIcon name="cart" :size="16" />
        </button>
      </div>

      <span v-if="!onSale" class="jd-card__status">{{ statusText }}</span>
    </div>

    <div class="jd-card__body">
      <h3 class="jd-card__title jd-clamp-2">{{ product.title || '未命名商品' }}</h3>
      <p class="jd-card__subtitle jd-clamp-1">{{ product.subtitle || '品质好物，放心选购' }}</p>

      <div class="jd-card__price-row">
        <span v-if="priceParts" class="jd-price">
          <span class="jd-price__symbol">¥</span>
          <span class="jd-price__int">{{ priceParts.int }}</span>
          <span class="jd-price__dec">.{{ priceParts.dec }}</span>
        </span>
        <span v-else class="jd-card__price-empty">价格待定</span>
        <span v-if="Number.isFinite(stock)" class="jd-card__stock">库存 {{ stock }}</span>
      </div>

      <div class="jd-card__meta">
        <el-tag :type="statusType" size="small" effect="plain" round>{{ statusText }}</el-tag>
        <span class="jd-card__meta-text">{{ onSale ? '当日发货' : '暂不可售' }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.jd-card {
  position: relative;
  display: flex;
  flex-direction: column;
  border-radius: var(--jd-radius);
  background: #fff;
  border: 1px solid var(--jd-line);
  overflow: hidden;
  cursor: pointer;
  transition:
    transform 0.3s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.3s ease,
    border-color 0.3s ease;
}
.jd-card:hover {
  transform: translateY(-5px);
  border-color: #f6d7d4;
  box-shadow: var(--jd-shadow-lift);
}

.jd-card__media {
  position: relative;
  overflow: hidden;
  background: #f4f5f7;
}
.jd-card__img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  transition: transform 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}
.jd-card:hover .jd-card__img {
  transform: scale(1.07);
}
.jd-card__img--empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #b8bcc4;
  font-size: 12px;
}

.jd-card__tags {
  position: absolute;
  top: 10px;
  left: 10px;
  display: flex;
  gap: 6px;
}
.jd-card__tag {
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--jd-ink-3);
  font-size: 10px;
  font-weight: 600;
  backdrop-filter: blur(4px);
}
.jd-card__tag--red {
  background: linear-gradient(120deg, #ff6a3d, var(--jd-red));
  color: #fff;
}

.jd-card__quick {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  /* 常驻显示（触屏没有 hover，隐藏会导致收藏/加购点不到） */
  opacity: 0.78;
  transition: opacity 0.24s ease;
}
.jd-card:hover .jd-card__quick,
.jd-card:focus-within .jd-card__quick {
  opacity: 1;
}
.jd-card__quick-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.94);
  color: var(--jd-ink-2);
  box-shadow: 0 6px 16px -10px rgba(17, 24, 39, 0.6);
  transition:
    transform 0.2s ease,
    color 0.2s ease,
    background 0.2s ease;
}
.jd-card__quick-btn:hover {
  transform: scale(1.08);
  color: var(--jd-red);
}
.jd-card__quick-btn.is-on {
  color: var(--jd-red);
  background: #fff;
  animation: jd-count-pop 0.4s ease;
}
.jd-card__quick-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.jd-card__status {
  position: absolute;
  inset: auto 0 0 0;
  padding: 6px 10px;
  background: rgba(17, 24, 39, 0.55);
  color: #fff;
  font-size: 11px;
  text-align: center;
  backdrop-filter: blur(2px);
}

.jd-card__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 12px 14px;
}
.jd-card__title {
  font-size: 14.5px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--jd-ink);
  min-height: 42px;
}
.jd-card__subtitle {
  font-size: 12px;
  color: var(--jd-ink-4);
}
.jd-card__price-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-top: 2px;
}
.jd-card__price-empty {
  font-size: 12px;
  color: var(--jd-ink-4);
}
.jd-card__stock {
  font-size: 12px;
  color: var(--jd-ink-4);
}
.jd-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.jd-card__meta-text {
  font-size: 12px;
  color: var(--jd-ink-4);
}
</style>