<script setup>
import StoreIcon from './StoreIcon.vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
})

const emit = defineEmits(['select'])

function money(value) {
  return Number(value || 0).toFixed(0)
}

function stars(rating) {
  const full = Math.round(Number(rating) || 0)
  return '★★★★★'.slice(0, full) + '☆☆☆☆☆'.slice(0, 5 - full)
}
</script>

<template>
  <section class="jd-curated">
    <header class="jd-section-head">
      <div class="jd-section-head__main">
        <span class="jd-section-head__bar"></span>
        <div>
          <h2>精选好物</h2>
          <p>编辑团队按口碑与性价比挑选，每天更新</p>
        </div>
      </div>
      <RouterLink :to="{ name: 'products', query: { sort: 'newest' } }" class="jd-section-head__more">
        查看更多
        <StoreIcon name="chevron-right" :size="14" />
      </RouterLink>
    </header>

    <div class="jd-curated__grid">
      <button
        v-for="(item, i) in items"
        :key="item.id"
        type="button"
        class="jd-curated__card"
        v-reveal="{ delay: i * 70 }"
        @click="emit('select', item)"
      >
        <span class="jd-curated__media">
          <img :src="item.image" :alt="item.title" loading="lazy" />
          <span class="jd-curated__overlay">
            <span class="jd-curated__overlay-btn">
              <StoreIcon name="search" :size="14" />
              找同款
            </span>
          </span>
          <span v-if="item.tags?.[0]" class="jd-curated__flag">{{ item.tags[0] }}</span>
        </span>

        <span class="jd-curated__body">
          <span class="jd-curated__title jd-clamp-2">{{ item.title }}</span>
          <span class="jd-curated__subtitle jd-clamp-1">{{ item.subtitle }}</span>
          <span class="jd-curated__meta">
            <span class="jd-curated__stars">{{ stars(item.rating) }}</span>
            <em>{{ item.rating }}</em>
            <span class="jd-curated__comments">{{ item.comments }} 条评价</span>
          </span>
          <span class="jd-curated__price-row">
            <span class="jd-price">
              <span class="jd-price__symbol">¥</span>
              <span class="jd-price__int">{{ money(item.price) }}</span>
            </span>
            <s class="jd-curated__origin">¥{{ money(item.origin) }}</s>
            <span class="jd-curated__tag">{{ item.tags?.[1] || '包邮' }}</span>
          </span>
        </span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.jd-curated {
  margin-top: 26px;
}

/* 通用区块标题：左侧红色竖条 + 标题 + 副标题 + 右侧更多 */
.jd-section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.jd-section-head__main {
  display: flex;
  align-items: center;
  gap: 12px;
}
.jd-section-head__bar {
  width: 4px;
  height: 30px;
  border-radius: 999px;
  background: linear-gradient(180deg, #ff6a3d, var(--jd-red));
}
.jd-section-head__main h2 {
  font-size: 21px;
  font-weight: 800;
  color: var(--jd-ink);
  letter-spacing: 0.4px;
}
.jd-section-head__main p {
  margin-top: 2px;
  font-size: 12px;
  color: var(--jd-ink-4);
}
.jd-section-head__more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  color: var(--jd-ink-3);
  transition: color 0.2s ease;
}
.jd-section-head__more:hover {
  color: var(--jd-red);
}

.jd-curated__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.jd-curated__card {
  display: flex;
  flex-direction: column;
  text-align: left;
  border-radius: var(--jd-radius);
  background: #fff;
  border: 1px solid var(--jd-line);
  overflow: hidden;
  transition:
    transform 0.3s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.3s ease,
    border-color 0.3s ease;
}
.jd-curated__card:hover {
  transform: translateY(-6px);
  border-color: #f7d8d5;
  box-shadow: var(--jd-shadow-lift);
}

.jd-curated__media {
  position: relative;
  display: block;
  overflow: hidden;
  background: #f4f5f7;
}
.jd-curated__media img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  transition: transform 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}
.jd-curated__card:hover .jd-curated__media img {
  transform: scale(1.08);
}
.jd-curated__overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: end center;
  padding-bottom: 14px;
  background: linear-gradient(180deg, transparent 55%, rgba(17, 24, 39, 0.45));
  opacity: 0;
  transition: opacity 0.3s ease;
}
.jd-curated__card:hover .jd-curated__overlay {
  opacity: 1;
}
.jd-curated__overlay-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--jd-red);
  font-size: 12px;
  font-weight: 700;
  transform: translateY(8px);
  transition: transform 0.3s ease;
}
.jd-curated__card:hover .jd-curated__overlay-btn {
  transform: translateY(0);
}
.jd-curated__flag {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 9px;
  border-radius: 8px;
  background: rgba(225, 37, 27, 0.92);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.5px;
  backdrop-filter: blur(4px);
}

.jd-curated__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 12px 14px;
}
.jd-curated__title {
  font-size: 14.5px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--jd-ink);
  min-height: 42px;
}
.jd-curated__subtitle {
  font-size: 12px;
  color: var(--jd-ink-4);
}
.jd-curated__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--jd-ink-4);
}
.jd-curated__stars {
  color: #ff9f1c;
  letter-spacing: -1px;
}
.jd-curated__meta em {
  font-style: normal;
  color: var(--jd-amber);
  font-weight: 600;
}
.jd-curated__comments {
  margin-left: auto;
}
.jd-curated__price-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 2px;
}
.jd-curated__origin {
  font-size: 11px;
  color: #b8bcc4;
}
.jd-curated__tag {
  margin-left: auto;
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid #ffd8d4;
  color: var(--jd-red);
  font-size: 10px;
}

@media (max-width: 1024px) {
  .jd-curated__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .jd-curated__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .jd-curated__title {
    min-height: 36px;
  }
}
</style>