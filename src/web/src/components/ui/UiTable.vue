<script setup>
import { computed } from 'vue'

/**
 * 统一表格薄封装。
 *
 * 只解决四件重复的事：列渲染、加载态、空态、操作列与分页的摆放。
 * 不接管筛选栏，也不发起数据请求 —— 那些留在各页面，避免与不同页面的
 * 请求时机、参数命名绑死。
 *
 * 用法：
 *   <UiTable :columns="columns" :data="list" :loading="loading" row-key="userId">
 *     <template #toolbar> ...筛选或操作按钮... </template>
 *     <template #actions="{ row }">
 *       <el-button size="small" @click="edit(row)">编辑</el-button>
 *     </template>
 *   </UiTable>
 */
const props = defineProps({
  /** 列定义：{ key, label, width?, minWidth?, align?, formatter?(row), slot? } */
  columns: { type: Array, required: true },
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  /** 行唯一键字段名；用于稳定渲染 */
  rowKey: { type: String, default: 'id' },
  /** 无数据时的提示文案 */
  emptyText: { type: String, default: '暂无数据' },
  /** 是否显示操作列（需配合 actions 插槽） */
  showActions: { type: Boolean, default: false },
  actionsLabel: { type: String, default: '操作' },
  actionsWidth: { type: [String, Number], default: 160 },
  /** 外层是否加边框 */
  border: { type: Boolean, default: true },
  /** 内容超宽时在表格内部横向滚动，避免整页溢出 */
  scrollX: { type: Boolean, default: true },
})

defineSlots()

const isEmpty = computed(() => !props.loading && props.data.length === 0)

function cellText(row, col) {
  if (typeof col.formatter === 'function') return col.formatter(row)
  const v = row?.[col.key]
  return v === null || v === undefined || v === '' ? '—' : v
}
</script>

<template>
  <div class="ui-table">
    <!-- 工具栏：筛选、新增、批量操作等由页面自行提供 -->
    <div v-if="$slots.toolbar" class="ui-table__toolbar">
      <slot name="toolbar" />
    </div>

    <el-table
      v-loading="loading"
      :data="data"
      :row-key="rowKey"
      :border="border"
      :empty-text="loading ? ' ' : emptyText"
      style="width: 100%"
    >
      <el-table-column
        v-for="col in columns"
        :key="col.key"
        :prop="col.key"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align || 'left'"
        :show-overflow-tooltip="col.tooltip !== false"
      >
        <template #default="{ row, $index }">
          <!-- 自定义列内容优先走同名列插槽 -->
          <slot v-if="col.slot || $slots[col.key]" :name="col.slot || col.key" :row="row" :index="$index">
            {{ cellText(row, col) }}
          </slot>
          <template v-else>{{ cellText(row, col) }}</template>
        </template>
      </el-table-column>

      <el-table-column
        v-if="showActions"
        :label="actionsLabel"
        :width="actionsWidth"
        align="center"
        fixed="right"
      >
        <template #default="{ row, $index }">
          <slot name="actions" :row="row" :index="$index" />
        </template>
      </el-table-column>

      <!-- 空态自定义（如需要更丰富的空状态） -->
      <template v-if="$slots.empty" #empty>
        <slot name="empty" />
      </template>
    </el-table>

    <!-- 分页由页面通过 pagination 插槽挂载，避免本组件假设分页参数字段 -->
    <div v-if="$slots.pagination" class="ui-table__pagination">
      <slot name="pagination" />
    </div>
  </div>
</template>

<style scoped>
.ui-table {
  width: 100%;
}

.ui-table__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.ui-table__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 0.75rem;
}
</style>
