<script setup>
import { computed } from 'vue'
import StoreIcon from './StoreIcon.vue'

const props = defineProps({
  categories: { type: Array, default: () => [] },
  sortOptions: { type: Array, default: () => [] },
  filterError: { type: String, default: '' },
})

const emit = defineEmits(['apply', 'reset'])

const open = defineModel('open', { type: Boolean, default: false })
const keyword = defineModel('keyword', { type: String, default: '' })
const categoryId = defineModel('categoryId', { type: String, default: '' })
const sort = defineModel('sort', { type: String, default: 'default' })
const minPrice = defineModel('minPrice', { type: [String, Number], default: '' })
const maxPrice = defineModel('maxPrice', { type: [String, Number], default: '' })

const pricePresets = [
  { label: '100 以下', min: '', max: '100' },
  { label: '100 - 500', min: '100', max: '500' },
  { label: '500 - 2000', min: '500', max: '2000' },
  { label: '2000 以上', min: '2000', max: '' },
]

const topCategories = computed(() => props.categories.filter((c) => !c.parentId))

function childrenOf(id) {
  return props.categories.filter((c) => c.parentId === id)
}

function applyPreset(preset) {
  minPrice.value = preset.min
  maxPrice.value = preset.max
}

function clearCategory() {
  categoryId.value = ''
}

function onApply() {
  open.value = false
  emit('apply')
}

function onReset() {
  emit('reset')
}
</script>

<template>
  <el-drawer
    v-model="open"
    direction="rtl"
    size="392px"
    :with-header="false"
    class="jd-filter-drawer"
    append-to-body
  >
    <div class="jd-filter">
      <header class="jd-filter__head">
        <h2>筛选商品</h2>
        <button type="button" class="jd-filter__close" aria-label="关闭" @click="open = false">
          <StoreIcon name="close" :size="18" />
        </button>
      </header>

      <div class="jd-filter__body">
        <section class="jd-filter__section">
          <label class="jd-filter__label" for="jd-filter-keyword">关键词</label>
          <el-input
            id="jd-filter-keyword"
            v-model="keyword"
            placeholder="搜索商品名称"
            clearable
            size="large"
            data-testid="product-list-keyword"
            @keyup.enter="onApply"
          >
            <template #prefix>
              <StoreIcon name="search" :size="15" />
            </template>
          </el-input>
        </section>

        <section class="jd-filter__section">
          <div class="jd-filter__label-row">
            <span class="jd-filter__label">分类</span>
            <button type="button" class="jd-filter__clear" @click="clearCategory">不限</button>
          </div>
          <div class="jd-filter__chips">
            <button
              type="button"
              class="jd-filter__chip"
              :class="{ 'is-active': !categoryId }"
              @click="clearCategory"
            >
              全部
            </button>
            <button
              v-for="cat in topCategories"
              :key="cat.categoryId"
              type="button"
              class="jd-filter__chip"
              :class="{ 'is-active': categoryId === cat.categoryId }"
              @click="categoryId = cat.categoryId"
            >
              {{ cat.name }}
            </button>
          </div>
          <div v-if="topCategories.length" class="jd-filter__subchips">
            <template v-for="cat in topCategories" :key="`children-${cat.categoryId}`">
              <template v-if="categoryId === cat.categoryId">
                <button
                  v-for="child in childrenOf(cat.categoryId)"
                  :key="child.categoryId"
                  type="button"
                  class="jd-filter__subchip"
                  :class="{ 'is-active': categoryId === child.categoryId }"
                  @click="categoryId = child.categoryId"
                >
                  {{ child.name }}
                </button>
              </template>
            </template>
          </div>
          <!-- 兼容旧版下拉：保证按分类 id 精确筛选的能力不丢失 -->
          <el-select
            v-model="categoryId"
            placeholder="全部分类"
            clearable
            class="jd-filter__select"
            data-testid="product-list-category"
            @change="onApply"
          >
            <el-option label="全部分类" value="" />
            <el-option
              v-for="c in categories"
              :key="c.categoryId"
              :label="c.parentId ? `　└ ${c.name}` : c.name"
              :value="c.categoryId"
            />
          </el-select>
        </section>

        <section class="jd-filter__section">
          <div class="jd-filter__label-row">
            <span class="jd-filter__label">价格区间（元）</span>
            <button type="button" class="jd-filter__clear" @click="applyPreset({ min: '', max: '' })">
              清空
            </button>
          </div>
          <div class="jd-filter__price">
            <el-input
              v-model="minPrice"
              type="number"
              min="0"
              placeholder="最低"
              data-testid="product-list-min-price"
              @keyup.enter="onApply"
            />
            <span class="jd-filter__dash">—</span>
            <el-input
              v-model="maxPrice"
              type="number"
              min="0"
              placeholder="最高"
              data-testid="product-list-max-price"
              @keyup.enter="onApply"
            />
          </div>
          <div class="jd-filter__chips jd-filter__chips--preset">
            <button
              v-for="preset in pricePresets"
              :key="preset.label"
              type="button"
              class="jd-filter__chip"
              @click="applyPreset(preset)"
            >
              {{ preset.label }}
            </button>
          </div>
        </section>

        <section class="jd-filter__section">
          <label class="jd-filter__label" for="jd-filter-sort">排序方式</label>
          <el-select
            id="jd-filter-sort"
            v-model="sort"
            class="jd-filter__select"
            data-testid="product-list-sort"
            @change="onApply"
          >
            <el-option v-for="opt in sortOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </section>

        <p v-if="filterError" class="jd-filter__error">{{ filterError }}</p>
      </div>

      <footer class="jd-filter__footer">
        <el-button data-testid="product-list-reset" size="large" @click="onReset">重置</el-button>
        <el-button type="primary" size="large" data-testid="product-list-search" @click="onApply">
          查看结果
        </el-button>
      </footer>
    </div>
  </el-drawer>
</template>

<style scoped>
.jd-filter {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.jd-filter__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid var(--jd-line);
}
.jd-filter__head h2 {
  font-size: 16px;
  font-weight: 700;
  color: var(--jd-ink);
}
.jd-filter__close {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: var(--jd-ink-3);
  transition: background 0.2s ease;
}
.jd-filter__close:hover {
  background: #f4f5f7;
}
.jd-filter__body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.jd-filter__section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.jd-filter__label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.jd-filter__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--jd-ink);
}
.jd-filter__clear {
  font-size: 12px;
  color: var(--jd-red);
}
.jd-filter__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.jd-filter__chip {
  padding: 6px 14px;
  border-radius: 999px;
  background: #f4f5f7;
  font-size: 12px;
  color: var(--jd-ink-3);
  transition:
    background 0.2s ease,
    color 0.2s ease;
}
.jd-filter__chip:hover {
  background: #ebedf1;
}
.jd-filter__chip.is-active {
  background: var(--jd-red-soft);
  color: var(--jd-red);
  font-weight: 600;
}
.jd-filter__subchips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.jd-filter__subchip {
  padding: 5px 12px;
  border-radius: 8px;
  border: 1px solid var(--jd-line);
  font-size: 12px;
  color: var(--jd-ink-3);
}
.jd-filter__subchip.is-active {
  border-color: var(--jd-red);
  color: var(--jd-red);
}
.jd-filter__select {
  width: 100%;
}
.jd-filter__price {
  display: flex;
  align-items: center;
  gap: 8px;
}
.jd-filter__dash {
  color: var(--jd-ink-4);
}
.jd-filter__error {
  font-size: 12px;
  color: var(--jd-red);
}
.jd-filter__footer {
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid var(--jd-line);
}
.jd-filter__footer :deep(.el-button) {
  flex: 1;
  border-radius: 999px;
}
</style>