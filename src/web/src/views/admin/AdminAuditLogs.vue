<script setup>
import { onMounted, ref } from 'vue'
import { auditApi, unwrapResult } from '../../api'

const logs = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const operatorId = ref('')
const action = ref('')
const from = ref('')
const to = ref('')
const loading = ref(true)
const error = ref('')

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

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await auditApi.list({
      page: page.value,
      size: size.value,
      operatorId: operatorId.value.trim() || undefined,
      action: action.value || undefined,
      from: from.value || undefined,
      to: to.value || undefined,
    })
    const payload = unwrapResult(data)
    if (!payload) {
      error.value = data?.message || '加载失败'
      return
    }
    logs.value = payload.list || []
    total.value = payload.total || 0
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function actionLabel(value) {
  return actionOptions.find((item) => item.value === value)?.label || value || '—'
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '—'
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">操作审计日志</h1>

    <div class="flex flex-wrap items-center gap-2">
      <input
        v-model="operatorId"
        type="text"
        placeholder="操作人 ID"
        class="w-44 px-3 py-2 text-sm border border-gray-300 rounded-md"
        @keyup.enter="search"
      />
      <select
        v-model="action"
        class="px-3 py-2 text-sm border border-gray-300 rounded-md bg-white"
        @change="search"
      >
        <option v-for="item in actionOptions" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
      <label class="flex items-center gap-1 text-xs text-gray-500">
        从
        <input v-model="from" type="date" class="px-2 py-1.5 text-sm border border-gray-300 rounded-md" />
      </label>
      <label class="flex items-center gap-1 text-xs text-gray-500">
        到
        <input v-model="to" type="date" class="px-2 py-1.5 text-sm border border-gray-300 rounded-md" />
      </label>
      <button
        class="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700"
        @click="search"
      >
        查询
      </button>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

    <div v-else class="bg-white rounded-lg border border-gray-200 overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-gray-50 text-gray-600">
          <tr>
            <th class="text-left px-4 py-2.5 font-medium">时间</th>
            <th class="text-left px-4 py-2.5 font-medium">操作人</th>
            <th class="text-left px-4 py-2.5 font-medium">角色</th>
            <th class="text-left px-4 py-2.5 font-medium">动作</th>
            <th class="text-left px-4 py-2.5 font-medium">对象</th>
            <th class="text-left px-4 py-2.5 font-medium">详情</th>
            <th class="text-left px-4 py-2.5 font-medium">结果</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr v-for="log in logs" :key="log.auditId">
            <td class="px-4 py-2.5 text-gray-500 whitespace-nowrap">{{ formatTime(log.createdAt) }}</td>
            <td class="px-4 py-2.5 text-gray-900 whitespace-nowrap">{{ log.operatorId || '—' }}</td>
            <td class="px-4 py-2.5 text-gray-600 whitespace-nowrap">{{ log.operatorRole || '—' }}</td>
            <td class="px-4 py-2.5 text-gray-700 whitespace-nowrap">{{ actionLabel(log.action) }}</td>
            <td class="px-4 py-2.5 text-gray-500 whitespace-nowrap">
              {{ log.targetType }}<span v-if="log.targetId">：{{ log.targetId }}</span>
            </td>
            <td class="px-4 py-2.5 text-gray-700 max-w-80">
              <span class="line-clamp-2">{{ log.detail || '—' }}</span>
            </td>
            <td class="px-4 py-2.5 whitespace-nowrap">
              <span
                class="text-xs px-2 py-0.5 rounded"
                :class="log.result === 'SUCCESS' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'"
              >
                {{ log.result === 'SUCCESS' ? '成功' : '失败' }}
              </span>
            </td>
          </tr>
          <tr v-if="!logs.length">
            <td colspan="7" class="px-4 py-10 text-center text-gray-400">暂无审计记录</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total > size" class="flex items-center justify-center gap-2">
      <button
        class="px-3 py-1 text-sm border border-gray-300 rounded-md disabled:opacity-40"
        :disabled="page === 1"
        @click="page--; load()"
      >
        上一页
      </button>
      <span class="text-sm text-gray-500">{{ page }} / {{ Math.ceil(total / size) }}</span>
      <button
        class="px-3 py-1 text-sm border border-gray-300 rounded-md disabled:opacity-40"
        :disabled="page * size >= total"
        @click="page++; load()"
      >
        下一页
      </button>
    </div>
  </div>
</template>
