<script setup>
import { computed, onMounted, ref } from 'vue'
import { configApi, isOk, unwrapResult } from '../../api'

/**
 * 系统配置：读写 Nacos 配置中心托管的平台参数（任务 #43）。
 *
 * 只展示后端真正在用的两个参数，避免出现「能改但不生效」的假配置项。
 * 保存后配置中心推送更新，order-service 无需重启即可生效。
 */
const values = ref({})
const labels = ref({})
const history = ref([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')

/** 每项的输入约束，与后端校验保持一致 */
const RULES = {
  'order.timeout.minutes': { min: 1, max: 1440, unit: '分钟', hint: '超时后系统自动取消订单并释放预占库存（BR-16）' },
  'order.timeout.batch-size': { min: 1, max: 500, unit: '条', hint: '每轮超时扫描最多处理的订单数' },
}

const keys = computed(() => Object.keys(labels.value))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await configApi.get()
    const payload = unwrapResult(data)
    if (!payload) {
      error.value = data?.message || '读取配置失败'
      return
    }
    values.value = { ...payload.values }
    labels.value = { ...payload.labels }
    await loadHistory()
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '读取配置失败'
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  try {
    const { data } = await configApi.history(20)
    const payload = unwrapResult(data)
    history.value = Array.isArray(payload) ? payload : []
  } catch {
    history.value = []
  }
}

async function save() {
  error.value = ''
  notice.value = ''
  // 前端先做一次范围校验，避免无谓请求
  for (const key of keys.value) {
    const rule = RULES[key]
    const v = Number(values.value[key])
    if (!rule) continue
    if (!Number.isInteger(v) || v < rule.min || v > rule.max) {
      error.value = `「${labels.value[key]}」需为 ${rule.min}~${rule.max} 之间的整数`
      return
    }
  }
  saving.value = true
  try {
    const payload = {}
    for (const key of keys.value) payload[key] = String(values.value[key])
    const { data } = await configApi.update(payload)
    if (!isOk(data)) {
      error.value = data?.message || '保存失败'
      return
    }
    const saved = unwrapResult(data)
    if (saved) values.value = { ...saved }
    notice.value = '配置已保存，服务无需重启即可生效'
    await loadHistory()
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '保存失败'
  } finally {
    saving.value = false
  }
}

function reload() {
  load()
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-gray-900">系统配置</h1>
      <div class="flex gap-2">
        <button
          class="px-4 py-2 text-sm rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50"
          @click="reload"
        >
          重新读取
        </button>
        <button
          class="px-4 py-2 text-sm rounded-md bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-60"
          :disabled="saving || loading"
          @click="save"
        >
          {{ saving ? '保存中…' : '保存配置' }}
        </button>
      </div>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p v-if="notice" class="text-sm text-green-600">{{ notice }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

    <template v-else>
      <section class="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
        <h2 class="text-sm font-medium text-gray-800">交易规则</h2>

        <div
          v-for="key in keys"
          :key="key"
          class="flex items-start justify-between gap-4 py-1.5 border-b border-gray-50 last:border-0"
        >
          <div class="flex-1">
            <p class="text-sm text-gray-800">{{ labels[key] }}</p>
            <p class="text-xs text-gray-400 mt-0.5">{{ RULES[key]?.hint }}</p>
            <p class="text-xs text-gray-300 mt-0.5 font-mono">{{ key }}</p>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <input
              v-model.number="values[key]"
              type="number"
              :min="RULES[key]?.min"
              :max="RULES[key]?.max"
              class="w-24 px-2 py-1 text-sm text-right border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <span class="text-xs text-gray-500 w-10">{{ RULES[key]?.unit }}</span>
          </div>
        </div>

        <p v-if="!keys.length" class="text-sm text-gray-400">配置中心暂无可用配置项</p>
      </section>

      <section class="bg-white rounded-lg border border-gray-200 overflow-x-auto">
        <div class="px-4 py-2.5 border-b border-gray-100">
          <h2 class="text-sm font-medium text-gray-800">最近变更记录</h2>
        </div>
        <table class="w-full text-sm">
          <thead class="bg-gray-50 text-gray-600">
            <tr>
              <th class="text-left px-4 py-2.5 font-medium">时间</th>
              <th class="text-left px-4 py-2.5 font-medium">操作人</th>
              <th class="text-left px-4 py-2.5 font-medium">配置项</th>
              <th class="text-left px-4 py-2.5 font-medium">原值</th>
              <th class="text-left px-4 py-2.5 font-medium">新值</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-for="h in history" :key="h.id">
              <td class="px-4 py-2.5 text-xs text-gray-500">{{ h.createdAt }}</td>
              <td class="px-4 py-2.5 text-xs text-gray-600">{{ h.operatorId }}</td>
              <td class="px-4 py-2.5 text-gray-700 font-mono text-xs">{{ h.configKey }}</td>
              <td class="px-4 py-2.5 text-gray-500">{{ h.oldValue ?? '—' }}</td>
              <td class="px-4 py-2.5 text-gray-900 font-medium">{{ h.newValue }}</td>
            </tr>
            <tr v-if="!history.length">
              <td colspan="5" class="px-4 py-8 text-center text-gray-400">暂无变更记录</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>

    <p class="text-xs text-gray-400">
      说明：参数托管在 Nacos 配置中心（dataId：mall-platform.yml），保存后推送更新，
      相关服务无需重启即可读取新值。只列出后端确有使用的参数，未接入的参数不在此展示。
    </p>
  </div>
</template>
