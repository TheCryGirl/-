<script setup>
import StoreIcon from './StoreIcon.vue'
import CountdownTimer from './CountdownTimer.vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
})

/** 点击交给父级：父级会解析成真实商品详情，解析不到再去搜索页 */
const emit = defineEmits(['select'])

function open(item) {
  emit('select', item)
}

function money(value) {
  return Number(value || 0).toFixed(0)
}
</script>

<template>
  <section class="jd-flash" v-reveal>
    <header class="jd-flash__head">
      <div class="jd-flash__title">
        <span class="jd-flash__badge">
          <StoreIcon name="zap" :size="16" />
        </span>
        <div>
          <h2>限时秒杀</h2>
          <p>整点开抢 · 手慢无</p>
        </div>
        <CountdownTimer />
      </div>
      <div class="jd-flash__more">
        <span>每场 6 件精选</span>
        <RouterLink :to="{ name: 'products', query: { sort: 'price_asc' } }" class="jd-flash__more-link">
          查看全部
          <StoreIcon name="chevron-right" :size="14" />
        </RouterLink>
      </div>
    </header>

    <div class="jd-flash__rail jd-scroll-x">
      <button
        v-for="(item, i) in items"
        :key="item.id"
        type="button"
        class="jd-flash__card"
        v-reveal="{ delay: i * 60 }"
        @click="open(item)"
      >
        <span class="jd-flash__thumb">
          <img :src="item.image" :alt="item.title" loading="lazy" />
          <span class="jd-flash__index">{{ String(i + 1).padStart(2, '0') }}</span>
        </span>
        <span class="jd-flash__name jd-clamp-2">{{ item.title }}</span>
        <span class="jd-flash__price">
          <span class="jd-price">
            <span class="jd-price__symbol">¥</span>
            <span class="jd-price__int">{{ money(item.price) }}</span>
          </span>
          <s class="jd-flash__origin">¥{{ money(item.origin) }}</s>
        </span>
        <span class="jd-flash__progress">
          <span class="jd-flash__bar"><i :style="{ width: `${item.progress}%` }"></i></span>
          <em>已抢 {{ item.progress }}% · 剩 {{ item.stock }} 件</em>
        </span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.jd-flash {
  border-radius: var(--jd-radius-lg);
  background: linear-gradient(180deg, #fff3f2 0%, #ffffff 46%);
  border: 1px solid #ffe2df;
  padding: 18px 20px 20px;
  box-shadow: var(--jd-shadow-card);
}
.jd-flash__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.jd-flash__title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.jd-flash__badge {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: linear-gradient(140deg, #ff6a3d, var(--jd-red));
  color: #fff;
  box-shadow: var(--jd-shadow-red);
}
.jd-flash__title h2 {
  font-size: 18px;
  font-weight: 800;
  color: var(--jd-ink);
  letter-spacing: 0.5px;
}
.jd-flash__title p {
  font-size: 11px;
  color: var(--jd-ink-4);
}
.jd-flash__more {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--jd-ink-4);
}
.jd-flash__more-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid #ffd5d0;
  color: var(--jd-red);
  font-weight: 600;
  transition: background 0.2s ease;
}
.jd-flash__more-link:hover {
  background: #fff0ee;
}

.jd-flash__rail {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(158px, 1fr);
  gap: 12px;
  margin-top: 16px;
  padding-bottom: 4px;
}
.jd-flash__card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border-radius: var(--jd-radius);
  background: #fff;
  border: 1px solid var(--jd-line);
  text-align: left;
  transition:
    transform 0.28s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.28s ease,
    border-color 0.28s ease;
}
.jd-flash__card:hover {
  transform: translateY(-4px);
  border-color: #ffd5d0;
  box-shadow: var(--jd-shadow-lift);
}
.jd-flash__thumb {
  position: relative;
  display: block;
  border-radius: 10px;
  overflow: hidden;
  background: #f4f5f7;
}
.jd-flash__thumb img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  transition: transform 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}
.jd-flash__card:hover .jd-flash__thumb img {
  transform: scale(1.07);
}
.jd-flash__index {
  position: absolute;
  top: 6px;
  left: 6px;
  padding: 1px 6px;
  border-radius: 6px;
  background: rgba(17, 24, 39, 0.55);
  color: #fff;
  font-size: 10px;
  letter-spacing: 1px;
  backdrop-filter: blur(4px);
}
.jd-flash__name {
  font-size: 13px;
  line-height: 1.45;
  color: var(--jd-ink-2);
  min-height: 34px;
}
.jd-flash__price {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.jd-flash__origin {
  font-size: 11px;
  color: #b8bcc4;
}
.jd-flash__progress {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.jd-flash__bar {
  display: block;
  height: 6px;
  border-radius: 999px;
  background: #ffe6e3;
  overflow: hidden;
}
.jd-flash__bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #ff7a4d, var(--jd-red));
  transition: width 0.8s cubic-bezier(0.22, 1, 0.36, 1);
}
.jd-flash__progress em {
  font-size: 11px;
  font-style: normal;
  color: var(--jd-ink-4);
}

@media (max-width: 640px) {
  .jd-flash {
    padding: 14px;
  }
  .jd-flash__rail {
    grid-auto-columns: 148px;
  }
  .jd-flash__more {
    display: none;
  }
}
</style>