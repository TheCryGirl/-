<script setup>
import { onMounted, ref } from 'vue'
import { evaluationApi, isOk, unwrapResult } from '../../api'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import UiTable from '../../components/ui/UiTable.vue'
import { usePagination } from '../../composables/usePagination'

/* ---------- 1. 状态 ---------- */
const list = ref([])
const loading = ref(false)
const loadError = ref('')
const status = ref('')
const productId = ref('')
/** 行级忙状态：正在隐藏/恢复的评价 ID */
const processing = ref('')

const pagination = usePagination({ defaultSize: 10, onChange: () => load() })
const { page, size, total, onPageChange, onSizeChange } = pagination

/* ---------- 2. 计算属性 ---------- */
const columns = [
  { key: 'evaluationId', label: '评价 ID', width: 150 },
  { key: 'orderId', label: '订单 ID', width: 150 },
  { key: 'buyerId', label: '买家', width: 160, slot: 'buyer', tooltip: false },
  { key: 'rating', label: '评分', width: 110, align: 'center', slot: 'rating' },
  { key: 'content', label: '内容', minWidth: 240, slot: 'content', tooltip: false },
  {
    key: 'images',
    label: '晒图',
    width: 90,
    align: 'center',
    formatter: (row) => `${row.images?.length || 0} 张`,
  },
  { key: 'status', label: '状态', width: 100, align: 'center', slot: 'status' },
  { key: 'createdAt', label: '评价时间', width: 180, formatter: (row) => formatTime(row.createdAt) },
]

/* ---------- 3. 方法 ---------- */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: page.value, size: size.value }
    if (status.value) params.status = status.value
    if (productId.value.trim()) params.productId = productId.value.trim()

    const { data } = await evaluationApi.adminList(params)
    if (!isOk(data)) {
      loadError.value = data?.message || '加载评价列表失败'
      list.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    list.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = payload?.total ?? list.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载评价列表失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  status.value = ''
  productId.value = ''
  page.value = 1
  load()
}

/** 评价状态只有 VISIBLE / HIDDEN，未知值保留原样 */
function evalStatusText(value) {
  if (value === 'VISIBLE') return '展示中'
  if (value === 'HIDDEN') return '已隐藏'
  return value || '-'
}

function evalStatusTag(value) {
  if (value === 'VISIBLE') return 'success'
  if (value === 'HIDDEN') return 'info'
  return 'info'
}

async function toggle(item) {
  const hide = item.status === 'VISIBLE'
  const actionText = hide ? '隐藏' : '恢复'
  const ok = await confirmAction({
    message: hide
      ? '确定隐藏该评价？隐藏后商品详情页与评分统计都不再展示。'
      : '确定恢复该评价的展示？',
    title: `${actionText}评价`,
    danger: hide,
  })
  if (!ok) return

  if (processing.value) return
  processing.value = item.evaluationId
  try {
    const { data } = hide
      ? await evaluationApi.hide(item.evaluationId)
      : await evaluationApi.show(item.evaluationId)
    if (!isOk(data)) {
      showError(data?.message || `${actionText}评价失败`)
      return
    }
    showSuccess(hide ? '该评价已隐藏' : '该评价已恢复展示')
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, `${actionText}评价失败`))
  } finally {
    processing.value = ''
  }
}

function stars(n) {
  const count = Math.max(0, Math.min(5, Number(n) || 0))
  return '★'.repeat(count) + '☆'.repeat(5 - count)
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-'
}

/* ---------- 4. 生命周期 ---------- */
onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <el-form inline class="bg-white p-4 rounded-lg" @submit.prevent>
      <el-form-item label="状态">
        <el-select v-model="status" style="width: 140px" @change="search">
          <el-option label="全部状态" value="" />
          <el-option label="展示中" value="VISIBLE" />
          <el-option label="已隐藏" value="HIDDEN" />
        </el-select>
      </el-form-item>
      <el-form-item label="商品 ID">
        <el-input
          v-model="productId"
          placeholder="按商品 ID 筛选"
          clearable
          style="width: 200px"
          @keyup.enter="search"
          @clear="search"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-base font-medium">评价列表</span>
          <span v-if="!loadError" class="text-xs text-gray-400">共 {{ total }} 条</span>
        </div>
      </template>

      <el-result v-if="loadError" status="error" icon="error" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>

      <UiTable
        v-else
        :columns="columns"
        :data="list"
        :loading="loading"
        row-key="evaluationId"
        empty-text="暂无评价"
        show-actions
        actions-width="90"
      >
        <template #buyer="{ row }">
          <span class="text-gray-900">{{ row.buyerId }}</span>
          <el-tag v-if="row.anonymous" size="small" type="info" class="ml-1">匿名</el-tag>
        </template>

        <template #rating="{ row }">
          <span class="text-amber-500 whitespace-nowrap">{{ stars(row.rating) }}</span>
        </template>

        <template #content="{ row }">
          <div class="max-w-64 whitespace-pre-wrap break-all line-clamp-2">{{ row.content || '—' }}</div>
        </template>

        <template #status="{ row }">
          <el-tag :type="evalStatusTag(row.status)" size="small">
            {{ evalStatusText(row.status) }}
          </el-tag>
        </template>

        <template #actions="{ row }">
          <el-button
            link
            size="small"
            :type="row.status === 'VISIBLE' ? 'danger' : 'primary'"
            :loading="processing === row.evaluationId"
            :disabled="!!processing && processing !== row.evaluationId"
            @click="toggle(row)"
          >
            {{ row.status === 'VISIBLE' ? '隐藏' : '恢复' }}
          </el-button>
        </template>

        <template #pagination>
          <el-pagination
            :current-page="page"
            :page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </template>
      </UiTable>
    </el-card>

    <p class="text-xs text-gray-400">
      隐藏后该评价从商品详情页消失，平均分统计同步剔除；恢复后重新展示。操作会写入审计日志。
    </p>
  </div>
</template>
