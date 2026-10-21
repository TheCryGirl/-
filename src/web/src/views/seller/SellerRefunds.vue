<script setup>
import { onMounted, ref } from 'vue'
import { paymentApi } from '../../api'

const refunds = ref([])
const loading = ref(true)
const error = ref('')
const message = ref('')
const processing = ref('')
const manualRefundId = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    // 接口契约暂未定义“卖家退款列表”，先保持空列表；有列表数据后按 refundId 渲染即可
    refunds.value = []
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function audit(refundId, approved, remark = '') {
  if (!refundId) {
    error.value = '请填写退款单号'
    return
  }
  processing.value = refundId
  error.value = ''
  message.value = ''
  try {
    const { data } = await paymentApi.auditRefund(refundId, { approved, remark })
    const refund = data?.data ?? data
    message.value = `退款单 ${refund.refundId} 已${approved ? '同意' : '驳回'}`
    if (refunds.value.some((r) => r.refundId === refundId)) {
      await load()
    }
    manualRefundId.value = ''
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '审核失败'
  } finally {
    processing.value = ''
  }
}

function formatPrice(cents) {
  return ((cents || 0) / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">退款审核</h1>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p v-if="message" class="text-sm text-green-600">{{ message }}</p>

    <!-- 按退款单号审核：契约目前只提供审核接口，没有卖家退款列表接口 -->
    <section class="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
      <h2 class="text-sm font-medium text-gray-700">按退款单号审核</h2>
      <div class="flex flex-wrap items-center gap-2">
        <input
          v-model="manualRefundId"
          class="px-3 py-2 border border-gray-300 rounded-md text-sm w-64"
          placeholder="请输入退款单号，例如 RF70001"
        />
        <button
          class="px-4 py-2 rounded-md border border-gray-300 text-gray-600 text-sm hover:bg-gray-50 disabled:opacity-50"
          :disabled="processing === manualRefundId"
          @click="audit(manualRefundId, false)"
        >
          驳回
        </button>
        <button
          class="px-4 py-2 rounded-md bg-green-600 text-white text-sm hover:bg-green-700 disabled:opacity-50"
          :disabled="processing === manualRefundId"
          @click="audit(manualRefundId, true)"
        >
          同意退款
        </button>
      </div>
      <p class="text-xs text-gray-400">
        卖家退款列表接口尚未在《05-接口契约》中定义，待后端补充后本页会自动展示待审核退款单。
      </p>
    </section>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
    <div v-else-if="!refunds.length" class="py-16 text-center text-gray-400 text-sm">
      暂无待审核退款
    </div>

    <div v-else class="bg-white rounded-lg border border-gray-200 overflow-hidden divide-y divide-gray-100">
      <div v-for="r in refunds" :key="r.refundId" class="p-4">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm text-gray-500">退款单：{{ r.refundId }}</span>
          <span class="text-xs px-2 py-0.5 rounded bg-yellow-100 text-yellow-700">待审核</span>
        </div>
        <p class="text-sm text-gray-900">退款金额：¥{{ formatPrice(r.amount) }}</p>
        <p class="text-sm text-gray-500">原因：{{ r.reason || '—' }}</p>
        <div class="flex justify-end gap-2 mt-3">
          <button
            class="px-3 py-1 rounded border border-gray-300 text-xs text-gray-600 hover:bg-gray-50"
            :disabled="processing === r.refundId"
            @click="audit(r.refundId, false)"
          >
            驳回
          </button>
          <button
            class="px-3 py-1 rounded bg-green-600 text-white text-xs hover:bg-green-700"
            :disabled="processing === r.refundId"
            @click="audit(r.refundId, true)"
          >
            同意
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
