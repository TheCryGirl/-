<script setup>
import { computed, onMounted, ref } from 'vue'
import { isOk, paymentApi, unwrapResult } from '../../api'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import UiTable from '../../components/ui/UiTable.vue'
import { formatTotal } from '../../utils/format'

/**
 * 退款复核：管理员视角复核全平台退款单。
 *
 * 与商家「退款审核」的区别：卖家审自己店的退款，这里看全平台，
 * 用于发现异常（如金额异常、反复退款、卖家长期不处理）。
 */
/* ---------- 1. 状态 ---------- */
const refunds = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const status = ref('ALL')
/** 行级忙状态：正在复核的退款单号 */
const processing = ref('')

const STATUS_TABS = [
  { key: 'ALL', label: '全部' },
  { key: 'PENDING', label: '待审核' },
  { key: 'SUCCESS', label: '已退款' },
  { key: 'REJECTED', label: '已驳回' },
  { key: 'FAILED', label: '失败' },
]

const STATUS_TEXT = {
  PENDING: '待审核',
  SUCCESS: '已退款',
  REJECTED: '已驳回',
  FAILED: '失败',
}

const STATUS_TAG = {
  PENDING: 'warning',
  SUCCESS: 'success',
  REJECTED: 'info',
  FAILED: 'danger',
}

/* ---------- 2. 计算属性 ---------- */
const columns = [
  { key: 'refundId', label: '退款单号', minWidth: 170 },
  { key: 'orderId', label: '订单', minWidth: 170 },
  { key: 'amount', label: '金额', width: 130, align: 'right', slot: 'amount' },
  { key: 'reason', label: '原因', minWidth: 160, slot: 'reason', tooltip: false },
  { key: 'status', label: '状态', width: 110, align: 'center', slot: 'status' },
  { key: 'createdAt', label: '申请时间', width: 180, formatter: (row) => formatTime(row.createdAt) },
]

const summary = computed(() => {
  const c = { total: refunds.value.length, pending: 0, success: 0, amount: 0 }
  for (const r of refunds.value) {
    if (r.status === 'PENDING') c.pending++
    if (r.status === 'SUCCESS') {
      c.success++
      c.amount += r.amount || 0
    }
  }
  return c
})

/* ---------- 3. 方法 ---------- */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    // 保持改造前的请求参数：一次取 100 条，不做前端分页
    const params = { page: 1, size: 100 }
    if (status.value !== 'ALL') params.status = status.value
    const { data } = await paymentApi.refunds(params)
    if (!isOk(data)) {
      loadError.value = data?.message || '加载退款单失败'
      refunds.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    refunds.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = payload?.total ?? refunds.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载退款单失败')
    refunds.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function switchTab(key) {
  if (status.value === key) return
  status.value = key
  load()
}

/** 退款状态文案：未知值保留原样 */
function refundStatusText(value) {
  return STATUS_TEXT[value] || value || '-'
}

function refundStatusTag(value) {
  return STATUS_TAG[value] || 'info'
}

/** 管理员复核：对争议退款直接裁定 */
async function review(refund, approved) {
  const actionText = approved ? '支持退款' : '驳回退款'
  const ok = await confirmAction({
    message: approved
      ? `确定对退款单「${refund.refundId}」裁定支持退款？金额 ${formatMoney(refund.amount)} 将退回买家。`
      : `确定驳回退款单「${refund.refundId}」？驳回后买家无法再就本单申请退款。`,
    title: approved ? '支持退款' : '驳回退款',
    danger: !approved,
  })
  if (!ok) return

  if (processing.value) return
  processing.value = refund.refundId
  try {
    const { data } = await paymentApi.auditRefund(refund.refundId, {
      approved,
      remark: approved ? '管理员复核通过' : '管理员复核驳回',
    })
    if (!isOk(data)) {
      showError(data?.message || '复核失败')
      return
    }
    showSuccess(approved ? '已裁定支持退款' : '已驳回退款')
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, '复核失败'))
  } finally {
    processing.value = ''
  }
}

/** 金额：后端为分 */
function formatMoney(cents) {
  const n = Number(cents)
  return Number.isFinite(n) ? `¥${(n / 100).toFixed(2)}` : '-'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

/* ---------- 4. 生命周期 ---------- */
onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div v-if="!loadError" class="grid grid-cols-2 lg:grid-cols-4 gap-3">
      <el-card shadow="never">
        <p class="text-xs text-gray-500">退款单总数</p>
        <p class="text-xl font-semibold text-gray-900 mt-1">{{ summary.total }}</p>
      </el-card>
      <el-card shadow="never">
        <p class="text-xs text-gray-500">待审核</p>
        <p class="text-xl font-semibold text-orange-500 mt-1">{{ summary.pending }}</p>
      </el-card>
      <el-card shadow="never">
        <p class="text-xs text-gray-500">已退款笔数</p>
        <p class="text-xl font-semibold text-green-600 mt-1">{{ summary.success }}</p>
      </el-card>
      <el-card shadow="never">
        <p class="text-xs text-gray-500">已退金额（元）</p>
        <p class="text-xl font-semibold text-red-600 mt-1">{{ formatMoney(summary.amount) }}</p>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="flex flex-wrap items-center justify-between gap-2">
          <span class="text-base font-medium">退款单</span>
          <div class="flex items-center gap-3">
            <span v-if="!loadError" class="text-xs text-gray-400">{{ formatTotal(total, '笔', refunds.length) }}</span>
            <el-radio-group v-model="status" size="small" @change="switchTab">
              <el-radio-button v-for="t in STATUS_TABS" :key="t.key" :value="t.key">
                {{ t.label }}
              </el-radio-button>
            </el-radio-group>
          </div>
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
        :data="refunds"
        :loading="loading"
        row-key="refundId"
        empty-text="暂无退款单"
        show-actions
        actions-width="170"
      >
        <template #amount="{ row }">
          <span class="text-red-600 font-medium">{{ formatMoney(row.amount) }}</span>
        </template>

        <template #reason="{ row }">
          <div class="max-w-64 whitespace-pre-wrap break-all line-clamp-2">{{ row.reason || '—' }}</div>
        </template>

        <template #status="{ row }">
          <el-tag :type="refundStatusTag(row.status)" size="small">
            {{ refundStatusText(row.status) }}
          </el-tag>
        </template>

        <template #actions="{ row }">
          <div v-if="row.status === 'PENDING'" class="flex items-center justify-end gap-2">
            <el-button
              link
              size="small"
              :disabled="!!processing && processing !== row.refundId"
              :loading="processing === row.refundId"
              @click="review(row, false)"
            >
              驳回
            </el-button>
            <el-button
              link
              size="small"
              type="success"
              :disabled="!!processing && processing !== row.refundId"
              :loading="processing === row.refundId"
              @click="review(row, true)"
            >
              支持退款
            </el-button>
          </div>
          <span v-else class="text-xs text-gray-400">{{ row.auditedBy ? '已处理' : '—' }}</span>
        </template>
      </UiTable>
    </el-card>

    <p class="text-xs text-gray-400">
      说明：卖家应在「退款审核」处理自己店的退款单；此页用于平台侧复核与争议裁定。
    </p>
  </div>
</template>
