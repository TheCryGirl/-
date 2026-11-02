<script setup>
import { onMounted, ref } from 'vue'
import { isOk, paymentApi, unwrapResult } from '../../api'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import { formatTotal } from '../../utils/format'

/* ---------- 1. 状态 ---------- */
const tickets = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
/** 按退款单号裁定的输入值 */
const manualRefundId = ref('')
/** 正在处理的退款单号（列表行与手工输入共用，避免重复点击） */
const processing = ref('')

/* ---------- 2. 方法 ---------- */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await paymentApi.refunds({ status: 'PENDING', page: 1, size: 50 })
    if (!isOk(data)) {
      loadError.value = data?.message || '加载待仲裁售后单失败'
      tickets.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    tickets.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = payload?.total ?? tickets.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载待仲裁售后单失败')
    tickets.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 管理员基于退款单做出最终裁定，复用退款审核接口 */
async function arbitrate(refundId, approved) {
  if (!refundId) {
    showError('请填写退款单号')
    return
  }
  if (processing.value) return

  const ok = await confirmAction({
    message: approved
      ? `确定对退款单「${refundId}」裁定支持买家退款？`
      : `确定驳回退款单「${refundId}」的退款申请？`,
    title: '售后裁定',
    danger: !approved,
  })
  if (!ok) return

  processing.value = refundId
  try {
    const { data } = await paymentApi.auditRefund(refundId, {
      approved,
      remark: 'ADMIN_ARBITRATION',
    })
    if (!isOk(data)) {
      showError(data?.message || '裁定失败')
      return
    }
    showSuccess(`售后单 ${refundId} 已裁定：${approved ? '支持买家退款' : '驳回退款申请'}`)
    manualRefundId.value = ''
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, '裁定失败'))
  } finally {
    processing.value = ''
  }
}

function formatMoney(cents) {
  const n = Number(cents)
  return Number.isFinite(n) ? (n / 100).toFixed(2) : '0.00'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <el-card shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-base font-medium">按退款单号裁定</span>
        </div>
      </template>

      <div class="flex flex-wrap items-center gap-2">
        <el-input
          v-model="manualRefundId"
          placeholder="请输入争议退款单号"
          clearable
          style="width: 260px"
          @keyup.enter="arbitrate(manualRefundId, true)"
        />
        <el-button
          :disabled="!manualRefundId || Boolean(processing)"
          :loading="processing === manualRefundId && Boolean(manualRefundId)"
          @click="arbitrate(manualRefundId, false)"
        >
          驳回退款
        </el-button>
        <el-button
          type="success"
          :disabled="!manualRefundId || Boolean(processing)"
          :loading="processing === manualRefundId && Boolean(manualRefundId)"
          @click="arbitrate(manualRefundId, true)"
        >
          支持买家
        </el-button>
      </div>
      <p class="text-xs text-gray-400 mt-3">
        列表来自待审核退款单，管理员可直接对争议退款单做出最终裁定；也可以按退款单号操作。
      </p>
    </el-card>

    <el-result v-if="loadError" status="error" icon="error" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>

    <el-card v-else shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-base font-medium">待仲裁售后单</span>
          <span v-if="!loadError" class="text-xs text-gray-400">{{ formatTotal(total, '单', tickets.length) }}</span>
        </div>
      </template>

      <div v-loading="loading" class="min-h-32">
        <el-empty v-if="!loading && !tickets.length" description="暂无待仲裁售后单" />

        <div v-else class="divide-y divide-gray-100">
          <div v-for="t in tickets" :key="t.refundId" class="py-4 first:pt-0 last:pb-0">
            <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
              <span class="text-sm text-gray-500">退款单：{{ t.refundId }}</span>
              <div class="flex items-center gap-2">
                <el-tag v-if="t.type" size="small" type="info">
                  {{ t.type === 'RETURN_REFUND' ? '退货退款' : t.type === 'REFUND_ONLY' ? '仅退款' : t.type }}
                </el-tag>
                <el-tag size="small" type="warning">待审核</el-tag>
              </div>
            </div>
            <div class="grid gap-1 sm:grid-cols-2">
              <p class="text-sm text-gray-900">订单：{{ t.orderId || '—' }}</p>
              <p class="text-sm text-gray-700">退款金额：<span class="text-red-600 font-medium">¥{{ formatMoney(t.amount) }}</span></p>
              <p class="text-sm text-gray-500 sm:col-span-2 break-all">原因：{{ t.reason || '—' }}</p>
              <p class="text-xs text-gray-400 sm:col-span-2">申请时间：{{ formatTime(t.createdAt) }}</p>
            </div>
            <div class="flex justify-end gap-2 mt-3">
              <el-button
                size="small"
                :disabled="Boolean(processing) && processing !== t.refundId"
                :loading="processing === t.refundId"
                @click="arbitrate(t.refundId, false)"
              >
                驳回
              </el-button>
              <el-button
                size="small"
                type="success"
                :disabled="Boolean(processing) && processing !== t.refundId"
                :loading="processing === t.refundId"
                @click="arbitrate(t.refundId, true)"
              >
                支持买家
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>
