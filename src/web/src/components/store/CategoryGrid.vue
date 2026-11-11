<script setup>
import { computed } from 'vue'
import { categoryIconFallback, categoryIcons } from '../../data/showcase'
import StoreIcon from './StoreIcon.vue'

const props = defineProps({
  categories: { type: Array, default: () => [] },
  activeId: { type: String, default: '' },
})

const emit = defineEmits(['select'])

/** 只展示顶级分类，最多 9 个入口，凑不满也不补假数据 */
const entries = computed(() => {
  const tops = props.categories.filter((c) => !c.parentId).slice(0, 9)
  return [{ categoryId: '', name: '全部商品', icon: 'grid' }].concat(
    tops.map((c) => ({
      categoryId: c.categoryId,
      name: c.name,
      icon: categoryIcons[c.name] || categoryIconFallback,
    })),
  )
})
</script>

<template>
  <nav class="jd-cats" aria-label="商品分类快捷入口">
    <button
      v-for="(entry, i) in entries"
      :key="entry.categoryId || 'all'"
      type="button"
      class="jd-cats__item"
      :class="{ 'is-active': (entry.categoryId || '') === (activeId || '') }"
      v-reveal="{ delay: i * 45 }"
      @click="emit('select', entry.categoryId)"
    >
      <span class="jd-cats__icon">
        <StoreIcon :name="entry.icon" :size="22" />
      </span>
      <span class="jd-cats__label">{{ entry.name }}</span>
    </button>
  </nav>
</template>

<style scoped>
.jd-cats {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
  padding: 14px 16px;
  border-radius: var(--jd-radius);
  background: #fff;
  border: 1px solid var(--jd-line);
  box-shadow: var(--jd-shadow-card);
}
.jd-cats__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 10px 4px;
  border-radius: 12px;
  transition: background 0.25s ease, transform 0.25s ease;
}
.jd-cats__item:hover {
  background: #fafafa;
  transform: translateY(-2px);
}
.jd-cats__icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 16px;
  background: linear-gradient(150deg, #fff3f1, #ffe7e3);
  color: var(--jd-red);
  transition:
    transform 0.3s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.3s ease;
}
.jd-cats__item:hover .jd-cats__icon {
  transform: translateY(-3px) scale(1.04);
  box-shadow: 0 12px 22px -14px rgba(225, 37, 27, 0.7);
}
.jd-cats__label {
  font-size: 12px;
  color: var(--jd-ink-2);
  font-weight: 500;
}
.jd-cats__item.is-active .jd-cats__label {
  color: var(--jd-red);
  font-weight: 700;
}
.jd-cats__item.is-active .jd-cats__icon {
  background: linear-gradient(150deg, #ff6a3d, var(--jd-red));
  color: #fff;
  box-shadow: 0 12px 22px -14px rgba(225, 37, 27, 0.8);
}

@media (max-width: 900px) {
  .jd-cats {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 480px) {
  .jd-cats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>