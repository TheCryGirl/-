<script setup>
import { computed, onMounted, ref } from 'vue'
import { orderApi, paymentApi, unwrapResult } from '../../api'

/**
 * 资金对账：按订单口径核对「应收 / 已收 / 已退」。
 *
 * 数据来源：
 * - 订单列表：拿到全平台订单与应付金额、状态
 * - 退款单列表：拿到已成功的退款金额
 *
 * 对账口径：
 * - 已收 = 状态为 PAID / SHIPPED / COMPLETED / REFUNDED 的订单金额之和
 * - 已退 = 退款单中状态 SUCCESS 的金额之和
 * - 净入账 = 已收 − 已退
 */
const orders = ref([])
const refunds = ref([])
const loading = ref(true)
const error = ref('')

/** 这些状态的订单代表钱已实际到账 */
const PAID_STATUS = ['PAID', 'SHIPPED', 'COMPLETED', 'REFUNDED']

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [orderRes, refundRes] = await Promise.all([
      orderApi.list({ page: 1, size: 200 }),
      paymentApi.refunds({ page: 1, size: 200 }),
    ])
    const o = unwrapResult(orderRes.data)
    const r = unwrapResult(refundRes.data)
    orders.value = Array.isArray(o) ? o : o?.list || []
    refunds.value = Array.isArray(r) ? r : r?.list || []
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载对账数据失败'
  } finally {
    loading.value = false
  }
}

const stats = computed(() => {
  let gmv = 0 // 全部下单金额
  let paidCount = 0
  let paidAmount = 0
  let cancelledCount = 0
  const byStatus = {}

  for (const o of orders.value) {
    const amount = o.payAmount || 0
    gmv += amount
    byStatus[o.status] = (byStatus[o.status] || 0) + 1
    if (PAID_STATUS.includes(o.status)) {
      paidCount++
      paidAmount += amount
    }
    if (o.status === 'CANCELLED') cancelledCount++
  }

  let refundedAmount = 0
  let refundedCount = 0
  let pendingRefundAmount = 0
  for (const r of refunds.value) {
    if (r.status === 'SUCCESS') {
      refundedCount++
      refundedAmount += r.amount || 0
    } else if (r.status === 'PENDING') {
      pendingRefundAmount += r.amount || 0
    }
  }

  return {
    gmv,
    orderCount: orders.value.length,
    paidCount,
    paidAmount,
    cancelledCount,
    refundedCount,
    refundedAmount,
    pendingRefundAmount,
    netAmount: paidAmount - refundedAmount,
    byStatus,
  }
})

/** 按天汇总净入账，用于发现某天资金异常 */
const daily = computed(() => {
  const map = new Map()
  for (const o of orders.value) {
    if (!PAID_STATUS.includes(o.status)) continue
    const day = String(o.createdAt || '').slice(0, 10)
    if (!day) continue
    const cur = map.get(day) || { day, count: 0, amount: 0, refund: 0 }
    cur.count++
    cur.amount += o.payAmount || 0
    map.set(day, cur)
  }
  for (const r of refunds.value) {
    if (r.status !== 'SUCCESS') continue
    const day = String(r.createdAt || '').slice(0, 10)
    if (!day) continue
    const cur = map.get(day) || { day, count: 0, amount: 0, refund: 0 }
    cur.refund += r.amount || 0
    map.set(day, cur)
  }
  return [...map.values()]
    .map((d) => ({ ...d, net: d.amount - d.refund }))
    .sort((a, b) => b.day.localeCompare(a.day))
})

function yuan(cents) {
  return ((cents || 0) / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-gray-900">资金对账</h1>
      <button
        class="px-4 py-2 text-sm rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50"
        @click="load"
      >
        刷新
      </button>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

    <template v-else>
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div class="bg-white rounded-lg border border-gray-200 p-3">
          <p class="text-xs text-gray-500">下单总额（元）</p>
          <p class="text-xl font-semibold text-gray-900">{{ yuan(stats.gmv) }}</p>
          <p class="text-xs text-gray-400 mt-1">{{ stats.orderCount }} 笔订单</p>
        </div>
        <div class="bg-white rounded-lg border border-gray-200 p-3">
          <p class="text-xs text-gray-500">已收金额（元）</p>
          <p class="text-xl font-semibold text-green-600">{{ yuan(stats.paidAmount) }}</p>
          <p class="text-xs text-gray-400 mt-1">{{ stats.paidCount }} 笔已付款</p>
        </div>
        <div class="bg-white rounded-lg border border-gray-200 p-3">
          <p class="text-xs text-gray-500">已退金额（元）</p>
          <p class="text-xl font-semibold text-red-600">{{ yuan(stats.refundedAmount) }}</p>
          <p class="text-xs text-gray-400 mt-1">{{ stats.refundedCount }} 笔退款</p>
        </div>
        <div class="bg-white rounded-lg border border-gray-200 p-3">
          <p class="text-xs text-gray-500">净入账（元）</p>
          <p class="text-xl font-semibold text-blue-600">{{ yuan(stats.netAmount) }}</p>
          <p class="text-xs text-gray-400 mt-1">已收 − 已退</p>
        </div>
      </div>

      <div class="grid sm:grid-cols-2 gap-3">
        <div class="bg-white rounded-lg border border-gray-200 p-4">
          <h2 class="text-sm font-medium text-gray-800 mb-3">订单状态分布</h2>
          <ul class="space-y-1.5 text-sm">
            <li
              v-for="(count, key) in stats.byStatus"
              :key="key"
              class="flex justify-between"
            >
              <span class="text-gray-600">{{ key }}</span>
              <span class="text-gray-900">{{ count }}</span>
            </li>
            <li v-if="!Object.keys(stats.byStatus).length" class="text-gray-400">暂无订单</li>
          </ul>
        </div>

        <div class="bg-white rounded-lg border border-gray-200 p-4">
          <h2 class="text-sm font-medium text-gray-800 mb-3">待处理事项</h2>
          <ul class="space-y-1.5 text-sm">
            <li class="flex justify-between">
              <span class="text-gray-600">已取消订单（未收款）</span>
              <span class="text-gray-900">{{ stats.cancelledCount }}</span>
            </li>
            <li class="flex justify-between">
              <span class="text-gray-600">待审核退款（未出账）</span>
              <span class="text-orange-600">¥{{ yuan(stats.pendingRefundAmount) }}</span>
            </li>
          </ul>
        </div>
      </div>

      <div class="bg-white rounded-lg border border-gray-200 overflow-x-auto">
        <div class="px-4 py-2.5 border-b border-gray-100">
          <h2 class="text-sm font-medium text-gray-800">按日对账</h2>
        </div>
        <table class="w-full text-sm">
          <thead class="bg-gray-50 text-gray-600">
            <tr>
              <th class="text-left px-4 py-2.5 font-medium">日期</th>
              <th class="text-right px-4 py-2.5 font-medium">付款笔数</th>
              <th class="text-right px-4 py-2.5 font-medium">收款（元）</th>
              <th class="text-right px-4 py-2.5 font-medium">退款（元）</th>
              <th class="text-right px-4 py-2.5 font-medium">净入账（元）</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-for="d in daily" :key="d.day">
              <td class="px-4 py-2.5 text-gray-700">{{ d.day }}</td>
              <td class="px-4 py-2.5 text-right text-gray-700">{{ d.count }}</td>
              <td class="px-4 py-2.5 text-right text-green-600">{{ yuan(d.amount) }}</td>
              <td class="px-4 py-2.5 text-right text-red-600">{{ yuan(d.refund) }}</td>
              <td class="px-4 py-2.5 text-right font-medium text-gray-900">{{ yuan(d.net) }}</td>
            </tr>
            <tr v-if="!daily.length">
              <td colspan="5" class="px-4 py-8 text-center text-gray-400">暂无对账数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <p class="text-xs text-gray-400">
      口径说明：已收金额按订单状态统计（已支付 / 已发货 / 已完成 / 已退款）；
      已退金额只统计审核通过（SUCCESS）的退款单，待审核退款单独列出。
    </p>
  </div>
</template>
