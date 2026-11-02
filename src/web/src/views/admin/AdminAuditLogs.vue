<script setup>
import { onMounted, ref } from 'vue'
import { auditApi, isOk, unwrapResult } from '../../api'
import { resolveErrorMessage } from '../../components/ui/message'
import UiTable from '../../components/ui/UiTable.vue'
import { usePagination } from '../../composables/usePagination'
import { formatTotal } from '../../utils/format'

/* ---------- 1. 状态 ---------- */
const logs = ref([])
const loading = ref(false)
const loadError = ref('')
const operatorId = ref('')
const action = ref('')
/** 日期保持原接口要求的 'YYYY-MM-DD' 字符串，不做 Date 对象/时区转换 */
const from = ref('')
const to = ref('')

const pagination = usePagination({ defaultSize: 20, onChange: () => load() })
const { page, size, total, onPageChange, onSizeChange } = pagination

const actionOptions = [
  { value: '', label: '全部动作' },
  { value: 'PRODUCT_AUDIT', label: '商品审核' },
  { value: 'USER_STATUS', label: '用户冻结/解冻' },
  { value: 'USER_ROLE', label: '用户角色变更' },
  { value: 'REFUND_AUDIT', label: '退款审核' },
  { value: 'EVALUATION_HIDE', label: '隐藏评价' },
  { value: 'EVALUATION_SHOW', label: '恢复评价' },
  { value: 'CONFIG_UPDATE', label: '平台配置变更' },
]

/* ---------- 2. 计算属性 ---------- */
const columns = [
  { key: 'createdAt', label: '时间', width: 180, formatter: (row) => formatTime(row.createdAt) },
  { key: 'operatorId', label: '操作人', width: 140 },
  { key: 'operatorRole', label: '角色', width: 100 },
  { key: 'action', label: '动作', width: 140, formatter: (row) => actionLabel(row.action) },
  { key: 'targetType', label: '对象', width: 180, slot: 'target' },
  { key: 'detail', label: '详情', minWidth: 220, slot: 'detail', tooltip: false },
  { key: 'result', label: '结果', width: 90, align: 'center', slot: 'result' },
]

/* ---------- 3. 方法 ---------- */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: page.value, size: size.value }
    if (operatorId.value.trim()) params.operatorId = operatorId.value.trim()
    if (action.value) params.action = action.value
    // 原样传 'YYYY-MM-DD' 字符串，后端按该格式解析
    if (from.value) params.from = from.value
    if (to.value) params.to = to.value

    const { data } = await auditApi.list(params)
    if (!isOk(data)) {
      loadError.value = data?.message || '加载审计日志失败'
      logs.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    logs.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = payload?.total ?? logs.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载审计日志失败')
    logs.value = []
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
  operatorId.value = ''
  action.value = ''
  from.value = ''
  to.value = ''
  page.value = 1
  load()
}

function actionLabel(value) {
  if (!value) return '—'
  return actionOptions.find((item) => item.value === value)?.label || value
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '—'
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <el-form inline class="bg-white p-4 rounded-lg" @submit.prevent>
      <el-form-item label="操作人 ID">
        <el-input
          v-model="operatorId"
          placeholder="操作人 ID"
          clearable
          style="width: 180px"
          @keyup.enter="search"
          @clear="search"
        />
      </el-form-item>
      <el-form-item label="动作">
        <el-select v-model="action" style="width: 170px" @change="search">
          <el-option v-for="item in actionOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间">
        <el-date-picker
          v-model="from"
          type="date"
          placeholder="开始日期"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          style="width: 150px"
        />
        <span class="mx-2 text-gray-400">至</span>
        <el-date-picker
          v-model="to"
          type="date"
          placeholder="结束日期"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          style="width: 150px"
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
          <span class="text-base font-medium">审计记录</span>
          <span v-if="!loadError" class="text-xs text-gray-400">{{ formatTotal(total, '条') }}</span>
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
        :data="logs"
        :loading="loading"
        row-key="auditId"
        empty-text="暂无审计记录"
      >
        <template #target="{ row }">
          <span class="text-gray-500">
            {{ row.targetType || '—' }}<span v-if="row.targetId">：{{ row.targetId }}</span>
          </span>
        </template>

        <template #detail="{ row }">
          <div class="max-w-80 whitespace-pre-wrap break-all line-clamp-2">{{ row.detail || '—' }}</div>
        </template>

        <template #result="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </template>

        <template #pagination>
          <el-pagination
            :current-page="page"
            :page-size="size"
            :total="total"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </template>
      </UiTable>
    </el-card>

    <p class="text-xs text-gray-400">
      审计日志为只读记录，按时间倒序展示最近的操作；日期按服务器当地日期筛选。
    </p>
  </div>
</template>
