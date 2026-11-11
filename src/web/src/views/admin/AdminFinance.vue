<script setup>
import { computed, onMounted, ref } from 'vue'
import { isOk, orderApi, paymentApi, unwrapResult } from '../../api'
import { ORDER_STATUS_MAP, statusText } from '../../constants/status'
import { resolveErrorMessage } from '../../components/ui/message'
import { formatTotal } from '../../utils/format'
import { combineFinanceSummaries } from '../../utils/adminFinance'

/**
 * 资金对账：按订单口径核对「应收 / 已收 / 已退」。
 *
 * 数据来源：
 * - 管理员SQL汇总：全部未删除订单，不按当前管理员的买家身份过滤
 * - 管理员退款SQL汇总：全部未删除退款，不受页面大小限制
 *
 * 对账口径：
 * - 已收 = 状态为 PAID / SHIPPED / COMPLETED / REFUNDED 的订单金额之和
 * - 已退 = 退款单中状态 SUCCESS 的金额之和
 * - 净入账 = 已收 − 已退
 */
const loading = ref(false)
const loadError = ref('')
const summary = ref(null)
const emptyStats = { gmv: 0, orderCount: 0, paidCount: 0, paidAmount: 0,
  cancelledCount: 0, refundedCount: 0, refundedAmount: 0, pendingRefundAmount: 0, netAmount: 0 }
const stats = computed(() => summary.value?.stats || emptyStats)
const daily = computed(() => summary.value?.daily || [])
const statusRows = computed(() => Object.entries(summary.value?.byStatus || {}).map(([code, count]) => ({
  code, label: statusText(ORDER_STATUS_MAP, code, code), count,
})))

/* ---------- 3. 方法 ---------- */
async function load() {
  if (loading.value) return
  loading.value = true
  loadError.value = ''
  try {
    const [orderRes, refundRes] = await Promise.all([
      orderApi.financeSummary(),
      paymentApi.financeSummary(),
    ])
    if (!isOk(orderRes.data) || !isOk(refundRes.data)) {
      loadError.value =
        orderRes.data?.message || refundRes.data?.message || '加载对账数据失败'
      summary.value = null
      return
    }
    const o = unwrapResult(orderRes.data)
    const r = unwrapResult(refundRes.data)
    summary.value = combineFinanceSummaries(o, r)
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载对账数据失败')
    summary.value = null
  } finally {
    loading.value = false
  }
}

/** 金额：后端为分 */
function formatMoney(cents) {
  const n = Number(cents)
  return (Number.isFinite(n) ? n / 100 : 0).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <el-result v-if="loadError" status="error" icon="error" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>

    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">资金总览</span>
            <el-button :loading="loading" @click="load">刷新</el-button>
          </div>
        </template>

        <div v-loading="loading" class="grid grid-cols-2 lg:grid-cols-4 gap-3 min-h-16">
          <div class="rounded-lg border border-gray-200 p-3">
            <p class="text-xs text-gray-500">下单总额（元）</p>
            <p class="text-xl font-semibold text-gray-900">{{ formatMoney(stats.gmv) }}</p>
            <p class="text-xs text-gray-400 mt-1">{{ stats.orderCount }} 笔订单</p>
          </div>
          <div class="rounded-lg border border-gray-200 p-3">
            <p class="text-xs text-gray-500">已收金额（元）</p>
            <p class="text-xl font-semibold text-green-600">{{ formatMoney(stats.paidAmount) }}</p>
            <p class="text-xs text-gray-400 mt-1">{{ stats.paidCount }} 笔已付款</p>
          </div>
          <div class="rounded-lg border border-gray-200 p-3">
            <p class="text-xs text-gray-500">已退金额（元）</p>
            <p class="text-xl font-semibold text-red-600">{{ formatMoney(stats.refundedAmount) }}</p>
            <p class="text-xs text-gray-400 mt-1">{{ stats.refundedCount }} 笔退款</p>
          </div>
          <div class="rounded-lg border border-gray-200 p-3">
            <p class="text-xs text-gray-500">净入账（元）</p>
            <p class="text-xl font-semibold text-blue-600">{{ formatMoney(stats.netAmount) }}</p>
            <p class="text-xs text-gray-400 mt-1">已收 − 已退</p>
          </div>
        </div>
      </el-card>

      <div class="grid gap-3 lg:grid-cols-2">
        <el-card shadow="never">
          <template #header>
            <span class="text-sm font-medium text-gray-800">订单状态分布</span>
          </template>
          <ul v-if="statusRows.length" class="space-y-1.5 text-sm">
            <li v-for="row in statusRows" :key="row.code" class="flex justify-between">
              <span class="text-gray-600">{{ row.label }}</span>
              <span class="text-gray-900">{{ row.count }}</span>
            </li>
          </ul>
          <el-empty v-else description="暂无订单" :image-size="80" />
        </el-card>

        <el-card shadow="never">
          <template #header>
            <span class="text-sm font-medium text-gray-800">待处理事项</span>
          </template>
          <ul class="space-y-1.5 text-sm">
            <li class="flex justify-between">
              <span class="text-gray-600">已取消订单（未收款）</span>
              <span class="text-gray-900">{{ stats.cancelledCount }}</span>
            </li>
            <li class="flex justify-between">
              <span class="text-gray-600">待审核退款（未出账）</span>
              <span class="text-orange-600">¥{{ formatMoney(stats.pendingRefundAmount) }}</span>
            </li>
          </ul>
        </el-card>
      </div>

      <el-card shadow="never">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-base font-medium">按日对账</span>
            <span class="text-xs text-gray-400">{{ formatTotal(daily.length, '天') }}</span>
          </div>
        </template>

        <el-table v-loading="loading" :data="daily" border style="width: 100%">
          <el-table-column prop="day" label="日期" width="130" />
          <el-table-column prop="count" label="付款笔数" width="110" align="right" />
          <el-table-column label="收款（元）" width="130" align="right">
            <template #default="{ row }">
              <span class="text-green-600">{{ formatMoney(row.amount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="退款（元）" width="130" align="right">
            <template #default="{ row }">
              <span class="text-red-600">{{ formatMoney(row.refund) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="净入账（元）" min-width="130" align="right">
            <template #default="{ row }">
              <span class="font-medium text-gray-900">{{ formatMoney(row.net) }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无对账数据" :image-size="80" />
          </template>
        </el-table>
      </el-card>
    </template>

    <p class="text-xs text-gray-400">
      口径说明：已收金额按订单状态统计（已支付 / 已发货 / 已完成 / 已退款）；
      已退金额只统计审核通过（SUCCESS）的退款单，待审核退款单独列出。
      全部数值由服务端对未删除记录汇总，不受100条分页限制；按日收款使用支付日期，退款使用审核日期。
      两服务分别读取，非跨库同一时刻快照；本页为订单口径核对，不代表真实支付渠道账单。
    </p>
  </div>
</template>
