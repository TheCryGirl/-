<script setup>
/**
 * 卖家退款审核（#81 T04）
 *
 * 保持原有两种入口：列表卡片按行审核 + 按退款单号直接审核；
 * 金额、退款单号、订单号与卖家权限均沿用原接口 paymentApi.refunds / auditRefund。
 *
 * 与原代码的差异只有两处，都属于规范要求：
 * 1. 审核是写操作，成功判断从 unwrapResult 改为 isOk（Result 成功时 data 可能为空）；
 * 2. 同意 / 驳回前加 confirmAction，取消不发请求。
 */
import { onMounted, ref } from 'vue'
import { isOk, paymentApi, unwrapResult } from '../../api'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'

/* ---------- 1. 状态 ---------- */
const list = ref([])
const loading = ref(false)
const loadError = ref('')
const processing = ref('')
const manualRefundId = ref('')
const manualRef = ref(null)

/* ---------- 2. 方法 ---------- */
async function fetchList() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await paymentApi.refunds({ status: 'PENDING', page: 1, size: 50 })
    if (!isOk(data)) {
      loadError.value = data?.message || '加载待审核退款失败'
      list.value = []
      return
    }
    const payload = unwrapResult(data)
    list.value = Array.isArray(payload) ? payload : payload?.list ?? []
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载待审核退款失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

async function handleAudit(refundId, approved) {
  if (!refundId) {
    showError('请填写退款单号')
    return
  }
  if (processing.value) return

  const actionText = approved ? '同意退款' : '驳回'
  const ok = await confirmAction({
    message: `确定${actionText}退款单「${refundId}」？`,
    danger: !approved,
  })
  if (!ok) return

  processing.value = refundId
  try {
    const { data } = await paymentApi.auditRefund(refundId, { approved, remark: '' })
    if (!isOk(data)) {
      showError(data?.message || '审核失败')
      return
    }
    showSuccess(`退款单 ${refundId} 已${approved ? '同意' : '驳回'}`)
    const refund = unwrapResult(data)
    if (refund?.refundId) manualRefundId.value = ''
    // 列表里的单子才需要刷新；手填单号审核时保持列表不动
    if (list.value.some((item) => item.refundId === refundId)) {
      await fetchList()
    }
    manualRefundId.value = ''
  } catch (err) {
    showError(resolveErrorMessage(err, '审核失败'))
  } finally {
    processing.value = ''
  }
}

/** 金额：后端单位是分 */
function formatMoney(cents) {
  const n = Number(cents)
  return Number.isFinite(n) ? `¥${(n / 100).toFixed(2)}` : '-'
}

/** 时间：统一 YYYY-MM-DD HH:mm */
function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

/* ---------- 3. 生命周期 ---------- */
onMounted(fetchList)
</script>

<template>
  <div class="space-y-4">
    <!-- 按退款单号审核：原入口保留 -->
    <el-card shadow="never">
      <template #header>
        <span class="text-base font-medium">按退款单号审核</span>
      </template>
      <el-form ref="manualRef" inline @submit.prevent>
        <el-form-item label="退款单号">
          <el-input
            v-model="manualRefundId"
            placeholder="例如 RF70001"
            clearable
            style="width: 220px"
            data-testid="seller-refunds-manual-id"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            :disabled="!manualRefundId || Boolean(processing)"
            data-testid="seller-refunds-manual-reject"
            @click="handleAudit(manualRefundId, false)"
          >
            驳回
          </el-button>
          <el-button
            type="success"
            :disabled="!manualRefundId || Boolean(processing)"
            data-testid="seller-refunds-manual-approve"
            @click="handleAudit(manualRefundId, true)"
          >
            同意退款
          </el-button>
        </el-form-item>
      </el-form>
      <p class="text-xs text-gray-400">
        列表由后端按当前登录卖家过滤，仅展示本店退款单；也可以直接输入退款单号审核。
      </p>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-base font-medium">待审核退款</span>
          <span class="text-xs text-gray-400">共 {{ list.length }} 单</span>
        </div>
      </template>

      <el-result v-if="loadError" status="error" title="加载待审核退款失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" data-testid="seller-refunds-retry" @click="fetchList">重试</el-button>
        </template>
      </el-result>

      <div v-else v-loading="loading">
        <el-empty v-if="!loading && !list.length" description="暂无待审核退款" />

        <div v-for="r in list" :key="r.refundId" class="border border-gray-200 rounded-lg p-4 mb-3">
          <div class="flex items-center justify-between mb-2">
            <span class="text-sm text-gray-500">退款单：{{ r.refundId }}</span>
            <el-tag type="warning" size="small">待审核</el-tag>
          </div>
          <p class="text-sm text-gray-800">
            退款金额：<span class="text-red-600 font-medium">{{ formatMoney(r.amount) }}</span>
          </p>
          <p class="text-xs text-gray-400 mt-1">
            订单：{{ r.orderId || '—' }} · 申请时间：{{ formatDateTime(r.createdAt) }}
          </p>
          <p class="text-sm text-gray-600 mt-1">原因：{{ r.reason || '—' }}</p>
          <div class="flex justify-end gap-2 mt-3">
            <el-button
              size="small"
              :disabled="Boolean(processing)"
              :data-testid="`seller-refunds-reject-${r.refundId}`"
              @click="handleAudit(r.refundId, false)"
            >
              驳回
            </el-button>
            <el-button
              type="success"
              size="small"
              :loading="processing === r.refundId"
              :data-testid="`seller-refunds-approve-${r.refundId}`"
              @click="handleAudit(r.refundId, true)"
            >
              同意
            </el-button>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>
