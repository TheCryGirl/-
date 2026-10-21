<script setup>
import { onMounted, ref } from 'vue'
import { paymentApi } from '../../api'

const tickets = ref([])
const loading = ref(true)
const error = ref('')
const message = ref('')
const processing = ref('')
const manualRefundId = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    // 契约中尚无售后工单（after_sale_ticket）查询接口，先保持空列表
    tickets.value = []
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

/** 管理员基于退款单做出最终裁定，复用退款审核接口 */
async function arbitrate(refundId, approved) {
  if (!refundId) {
    error.value = '请填写退款单号'
    return
  }
  processing.value = refundId
  error.value = ''
  message.value = ''
  try {
    const { data } = await paymentApi.auditRefund(refundId, {
      approved,
      remark: 'ADMIN_ARBITRATION',
    })
    const refund = data?.data ?? data
    message.value = `售后单 ${refund?.refundId || refundId} 已裁定：${approved ? '支持买家退款' : '驳回退款申请'}`
    manualRefundId.value = ''
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '仲裁失败'
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
    <h1 class="text-lg font-semibold text-gray-900">售后仲裁</h1>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p v-if="message" class="text-sm text-green-600">{{ message }}</p>

    <section class="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
      <h2 class="text-sm font-medium text-gray-700">按退款单号裁定</h2>
      <div class="flex flex-wrap items-center gap-2">
        <input
          v-model="manualRefundId"
          class="px-3 py-2 border border-gray-300 rounded-md text-sm w-64"
          placeholder="请输入争议退款单号"
        />
        <button
          class="px-4 py-2 rounded-md border border-gray-300 text-gray-600 text-sm hover:bg-gray-50 disabled:opacity-50"
          :disabled="!manualRefundId || Boolean(processing)"
          @click="arbitrate(manualRefundId, false)"
        >
          驳回退款
        </button>
        <button
          class="px-4 py-2 rounded-md bg-green-600 text-white text-sm hover:bg-green-700 disabled:opacity-50"
          :disabled="!manualRefundId || Boolean(processing)"
          @click="arbitrate(manualRefundId, true)"
        >
          支持买家
        </button>
      </div>
      <p class="text-xs text-gray-400">
        售后工单列表接口尚未在《05-接口契约》中定义；当前管理员可直接对争议退款单做出最终裁定。
      </p>
    </section>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
    <div v-else-if="!tickets.length" class="py-16 text-center text-gray-400 text-sm">
      暂无待仲裁售后单
    </div>

    <div v-else class="bg-white rounded-lg border border-gray-200 overflow-hidden divide-y divide-gray-100">
      <div v-for="t in tickets" :key="t.ticketId" class="p-4">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm text-gray-500">售后单：{{ t.ticketId }}</span>
          <span class="text-xs px-2 py-0.5 rounded bg-yellow-100 text-yellow-700">待仲裁</span>
        </div>
        <p class="text-sm text-gray-900">订单：{{ t.orderNo }}</p>
        <p class="text-sm text-gray-700">退款金额：¥{{ formatPrice(t.amount) }}</p>
        <p class="text-sm text-gray-500">原因：{{ t.reason || '—' }}</p>
        <div class="flex justify-end gap-2 mt-3">
          <button
            class="px-3 py-1 rounded border border-gray-300 text-xs text-gray-600 hover:bg-gray-50"
            :disabled="Boolean(processing)"
            @click="arbitrate(t.refundId, false)"
          >
            驳回
          </button>
          <button
            class="px-3 py-1 rounded bg-green-600 text-white text-xs hover:bg-green-700"
            :disabled="Boolean(processing)"
            @click="arbitrate(t.refundId, true)"
          >
            支持买家
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
