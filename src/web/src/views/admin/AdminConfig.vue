<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { configApi, isOk, unwrapResult } from '../../api'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'

/**
 * 系统配置：读写 Nacos 配置中心托管的平台参数（任务 #43）。
 *
 * 只展示后端真正在用的两个参数，避免出现「能改但不生效」的假配置项。
 * 保存后配置中心推送更新，order-service 无需重启即可生效。
 */
/* ---------- 1. 状态 ---------- */
const values = ref({})
const labels = ref({})
const history = ref([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
/** 表单级提示：校验不通过时提示，不能把整页替换成失败态 */
const formError = ref('')
/** 逐项校验错误，键为配置项 key */
const fieldErrors = reactive({})

/** 每项的输入约束，与后端校验保持一致 */
const RULES = {
  'order.timeout.minutes': {
    min: 1,
    max: 1440,
    unit: '分钟',
    hint: '超时后系统自动取消订单并释放预占库存（BR-16）',
  },
  'order.timeout.batch-size': {
    min: 1,
    max: 500,
    unit: '条',
    hint: '每轮超时扫描最多处理的订单数',
  },
}

const keys = computed(() => Object.keys(labels.value))

/* ---------- 2. 方法 ---------- */
function clearFieldErrors() {
  for (const key of Object.keys(fieldErrors)) delete fieldErrors[key]
}

async function load() {
  loading.value = true
  loadError.value = ''
  clearFieldErrors()
  try {
    const { data } = await configApi.get()
    if (!isOk(data)) {
      loadError.value = data?.message || '读取配置失败'
      return
    }
    const payload = unwrapResult(data)
    if (!payload) {
      loadError.value = data?.message || '读取配置失败'
      return
    }
    values.value = { ...payload.values }
    labels.value = { ...payload.labels }
    await loadHistory()
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '读取配置失败')
  } finally {
    loading.value = false
  }
}

/** 历史只读最近 20 条，保持原样 */
async function loadHistory() {
  try {
    const { data } = await configApi.history(20)
    if (!isOk(data)) {
      history.value = []
      return
    }
    const payload = unwrapResult(data)
    history.value = Array.isArray(payload) ? payload : []
  } catch {
    history.value = []
  }
}

/** 逐项校验：必须是整数且落在范围内 */
function validate() {
  clearFieldErrors()
  let valid = true
  for (const key of keys.value) {
    const rule = RULES[key]
    if (!rule) continue
    const raw = values.value[key]
    const v = Number(raw)
    if (raw === '' || raw === null || raw === undefined || !Number.isInteger(v) || v < rule.min || v > rule.max) {
      fieldErrors[key] = `需为 ${rule.min}~${rule.max} 之间的整数`
      valid = false
    }
  }
  return valid
}

async function save() {
  formError.value = ''
  if (!validate()) {
    formError.value = '请先修正标红的配置项'
    return
  }
  if (saving.value) return

  saving.value = true
  try {
    // 后端按字符串读取配置，提交时统一转 String
    const payload = {}
    for (const key of keys.value) payload[key] = String(values.value[key])

    const { data } = await configApi.update(payload)
    if (!isOk(data)) {
      showError(data?.message || '保存失败')
      return
    }
    const saved = unwrapResult(data)
    if (saved && saved.values) values.value = { ...saved.values }
    else if (saved) values.value = { ...saved }
    showSuccess('配置已保存，服务无需重启即可生效')
    await loadHistory()
  } catch (err) {
    showError(resolveErrorMessage(err, '保存失败'))
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
    <el-result v-if="loadError && !loading" status="error" icon="error" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>

    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">交易规则</span>
            <div class="flex gap-2">
              <el-button :disabled="saving || loading" @click="reload">重新读取</el-button>
              <el-button type="primary" :loading="saving" :disabled="loading" @click="save">
                保存配置
              </el-button>
            </div>
          </div>
        </template>

        <el-alert
          v-if="formError"
          class="mb-3"
          type="error"
          :title="formError"
          :closable="false"
          show-icon
        />

        <el-form label-width="220px" @submit.prevent>
          <el-form-item
            v-for="key in keys"
            :key="key"
            :label="labels[key]"
            :error="fieldErrors[key]"
          >
            <div class="flex items-center gap-2">
              <el-input-number
                v-model="values[key]"
                :step="1"
                controls-position="right"
                style="width: 150px"
              />
              <span class="text-xs text-gray-500">{{ RULES[key]?.unit }}</span>
            </div>
            <p class="text-xs text-gray-400 mt-1 w-full">{{ RULES[key]?.hint }}</p>
            <p class="text-xs text-gray-300 font-mono w-full">{{ key }}</p>
          </el-form-item>

          <el-empty v-if="!keys.length" description="配置中心暂无可用配置项" :image-size="80" />
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-base font-medium">最近变更记录</span>
            <span class="text-xs text-gray-400">仅展示最近 20 条</span>
          </div>
        </template>

        <el-table v-loading="loading" :data="history" border style="width: 100%">
          <el-table-column prop="createdAt" label="时间" width="180" />
          <el-table-column prop="operatorId" label="操作人" width="150" />
          <el-table-column prop="configKey" label="配置项" min-width="220" />
          <el-table-column prop="oldValue" label="原值" width="140">
            <template #default="{ row }">{{ row.oldValue ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="newValue" label="新值" width="140" />
          <template #empty>
            <el-empty description="暂无变更记录" :image-size="80" />
          </template>
        </el-table>
      </el-card>
    </template>

    <p class="text-xs text-gray-400">
      说明：参数托管在 Nacos 配置中心（dataId：mall-platform.yml），保存后推送更新，
      相关服务无需重启即可读取新值。只列出后端确有使用的参数，未接入的参数不在此展示。
    </p>
  </div>
</template>
