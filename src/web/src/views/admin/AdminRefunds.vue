<script setup>
import { computed, onMounted, ref } from 'vue'
import { isOk, paymentApi, unwrapResult } from '../../api'

/**
 * 退款复核：管理员视角复核全平台退款单。
 *
 * 与商家「退款审核」的区别：卖家审自己店的退款，这里看全平台，
 * 用于发现异常（如金额异常、反复退款、卖家长期不处理）。
 */
const refunds = ref([])
const loading = ref(true)
const error = ref('')
const notice = ref('')
const status = ref('ALL')
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

const STATUS_CLASS = {
  PENDING: 'bg-orange-100 text-orange-600',
  SUCCESS: 'bg-green-100 text-green-700',
  REJECTED: 'bg-gray-100 text-gray-500',
  FAILED: 'bg-red-100 text-red-600',
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: 1, size: 100 }
    if (status.value !== 'ALL') params.status = status.value
    const { data } = await paymentApi.refunds(params)
    const payload = unwrapResult(data)
    refunds.value = Array.isArray(payload) ? payload : payload?.list || []
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载退款单失败'
  } finally {
    loading.value = false
  }
}

function switchTab(key) {
  if (status.value === key) return
  status.value = key
  load()
}

/** 管理员复核：对争议退款直接裁定 */
async function review(refundId, approved) {
  processing.value = refundId
  error.value = ''
  notice.value = ''
  try {
    const { data } = await paymentApi.auditRefund(refundId, {
      approved,
      remark: approved ? '管理员复核通过' : '管理员复核驳回',
    })
    if (!isOk(data)) {
      error.value = data?.message || '复核失败'
      return
    }
    notice.value = approved ? '已裁定支持退款' : '已驳回退款'
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '复核失败'
  } finally {
    processing.value = ''
  }
}

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

function formatPrice(cents) {
  return ((cents || 0) / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">退款复核</h1>

    <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
      <div class="bg-white rounded-lg border border-gray-200 p-3">
        <p class="text-xs text-gray-500">退款单总数</p>
        <p class="text-xl font-semibold text-gray-900">{{ summary.total }}</p>
      </div>
      <div class="bg-white rounded-lg border border-gray-200 p-3">
        <p class="text-xs text-gray-500">待审核</p>
        <p class="text-xl font-semibold text-orange-500">{{ summary.pending }}</p>
      </div>
      <div class="bg-white rounded-lg border border-gray-200 p-3">
        <p class="text-xs text-gray-500">已退款笔数</p>
        <p class="text-xl font-semibold text-green-600">{{ summary.success }}</p>
      </div>
      <div class="bg-white rounded-lg border border-gray-200 p-3">
        <p class="text-xs text-gray-500">已退金额（元）</p>
        <p class="text-xl font-semibold text-red-600">{{ formatPrice(summary.amount) }}</p>
      </div>
    </div>

    <div class="flex gap-2">
      <button
        v-for="t in STATUS_TABS"
        :key="t.key"
        class="px-3 py-1.5 text-sm rounded-md border"
        :class="
          status === t.key
            ? 'bg-blue-50 border-blue-300 text-blue-600'
            : 'bg-white border-gray-300 text-gray-600 hover:bg-gray-50'
        "
        @click="switchTab(t.key)"
      >
        {{ t.label }}
      </button>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p v-if="notice" class="text-sm text-green-600">{{ notice }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
    <div v-else-if="!refunds.length" class="py-16 text-center text-gray-400 text-sm">
      暂无退款单
    </div>

    <div v-else class="bg-white rounded-lg border border-gray-200 overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-gray-50 text-gray-600">
          <tr>
            <th class="text-left px-4 py-2.5 font-medium">退款单号</th>
            <th class="text-left px-4 py-2.5 font-medium">订单</th>
            <th class="text-right px-4 py-2.5 font-medium">金额</th>
            <th class="text-left px-4 py-2.5 font-medium">原因</th>
            <th class="text-left px-4 py-2.5 font-medium">状态</th>
            <th class="text-left px-4 py-2.5 font-medium">申请时间</th>
            <th class="text-right px-4 py-2.5 font-medium">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr v-for="r in refunds" :key="r.refundId">
            <td class="px-4 py-2.5 text-gray-700">{{ r.refundId }}</td>
            <td class="px-4 py-2.5 text-gray-500 text-xs">{{ r.orderId }}</td>
            <td class="px-4 py-2.5 text-right text-red-600 font-medium">
              ¥{{ formatPrice(r.amount) }}
            </td>
            <td class="px-4 py-2.5 text-gray-600">{{ r.reason || '—' }}</td>
            <td class="px-4 py-2.5">
              <span class="text-xs px-2 py-0.5 rounded" :class="STATUS_CLASS[r.status] || 'bg-gray-100'">
                {{ STATUS_TEXT[r.status] || r.status }}
              </span>
            </td>
            <td class="px-4 py-2.5 text-xs text-gray-500">{{ r.createdAt || '—' }}</td>
            <td class="px-4 py-2.5 text-right">
              <div v-if="r.status === 'PENDING'" class="flex justify-end gap-2">
                <button
                  class="px-3 py-1 rounded border border-gray-300 text-xs text-gray-600 hover:bg-gray-50"
                  :disabled="processing === r.refundId"
                  @click="review(r.refundId, false)"
                >
                  驳回
                </button>
                <button
                  class="px-3 py-1 rounded bg-green-600 text-white text-xs hover:bg-green-700"
                  :disabled="processing === r.refundId"
                  @click="review(r.refundId, true)"
                >
                  支持退款
                </button>
              </div>
              <span v-else class="text-xs text-gray-400">
                {{ r.auditedBy ? '已处理' : '—' }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <p class="text-xs text-gray-400">
      说明：卖家应在「退款审核」处理自己店的退款单；此页用于平台侧复核与争议裁定。
    </p>
  </div>
</template>
