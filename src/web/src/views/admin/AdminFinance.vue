<script setup>
import PageHero from '../../components/store/PageHero.vue'
import { computed, onMounted, ref } from 'vue'
import { http, isOk, orderApi, paymentApi, unwrapResult } from '../../api'
import { ORDER_STATUS_MAP, statusText } from '../../constants/status'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import { confirmAction } from '../../components/ui/confirm'
import { usePagination } from '../../composables/usePagination'
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

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

/* ---------- 渠道对账（#137） ---------- */
// 导入渠道账单 CSV，逐笔比对本地支付/退款，只出差异清单，不自动修账。
const RECONCILE_CHANNELS = [
  { value: 'ALIPAY', label: '支付宝（沙箱）' },
  { value: 'MOCK', label: '模拟支付' },
]
const DIFF_TYPE_TEXT = { LOCAL_ONLY: '本地有渠道无', CHANNEL_ONLY: '渠道有本地无', AMOUNT_MISMATCH: '金额不一致' }
const DIFF_TYPE_TAG = { LOCAL_ONLY: 'warning', CHANNEL_ONLY: 'danger', AMOUNT_MISMATCH: 'danger' }
const DIFF_STATUS_TEXT = { PENDING: '待处理', RESOLVED: '已处理' }
const DIFF_STATUS_TAG = { PENDING: 'warning', RESOLVED: 'success' }
const BILL_TYPE_TEXT = { PAYMENT: '支付', REFUND: '退款' }

const reconcileChannel = ref('ALIPAY')
const billInput = ref(null)
const selectedFile = ref(null)
const selectedFileName = ref('')
const importing = ref(false)

const batches = ref([])
const batchesLoading = ref(false)
const batchesError = ref('')
const selectedBatchId = ref('')

const differences = ref([])
const diffError = ref('')
const diffTypeFilter = ref('ALL')
const diffStatusFilter = ref('ALL')
const resolvingId = ref(null)

const {
  page: diffPage,
  size: diffSize,
  total: diffTotal,
  loading: diffLoading,
  onPageChange: onDiffPageChange,
  onSizeChange: onDiffSizeChange,
} = usePagination({ defaultSize: 20, onChange: loadDifferences })

function diffTypeText(v) { return DIFF_TYPE_TEXT[v] || v || '-' }
function diffTypeTag(v) { return DIFF_TYPE_TAG[v] || 'info' }
function diffStatusText(v) { return DIFF_STATUS_TEXT[v] || v || '-' }
function diffStatusTag(v) { return DIFF_STATUS_TAG[v] || 'info' }
function billTypeText(v) { return BILL_TYPE_TEXT[v] || v || '-' }

function pickBill() {
  billInput.value?.click()
}

function onBillChange(event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.csv')) {
    showError('请选择 CSV 账单文件')
    if (billInput.value) billInput.value.value = ''
    return
  }
  selectedFile.value = file
  selectedFileName.value = file.name
}

async function importBill() {
  if (!selectedFile.value) {
    showError('请先选择账单 CSV 文件')
    return
  }
  importing.value = true
  try {
    const formData = new FormData()
    formData.append('channel', reconcileChannel.value)
    formData.append('file', selectedFile.value)
    const { data } = await http.post('/payments/admin/reconcile/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    if (!isOk(data)) {
      showError(data?.message || '导入失败')
      return
    }
    const batch = unwrapResult(data)
    showSuccess(`对账完成：${batch?.totalRows ?? 0} 行，差异 ${batch?.diffCount ?? 0} 条`)
    selectedFile.value = null
    selectedFileName.value = ''
    if (billInput.value) billInput.value.value = ''
    await loadBatches()
    if (batch?.batchId) selectBatch({ batchId: batch.batchId })
  } catch (err) {
    showError(resolveErrorMessage(err, '导入失败'))
  } finally {
    importing.value = false
  }
}

async function loadBatches() {
  batchesLoading.value = true
  batchesError.value = ''
  try {
    const { data } = await http.get('/payments/admin/reconcile/batches', {
      params: { channel: reconcileChannel.value, page: 1, size: 20 },
    })
    if (!isOk(data)) {
      batchesError.value = data?.message || '加载对账批次失败'
      batches.value = []
      return
    }
    const payload = unwrapResult(data)
    batches.value = payload?.list ?? []
  } catch (err) {
    batchesError.value = resolveErrorMessage(err, '加载对账批次失败')
    batches.value = []
  } finally {
    batchesLoading.value = false
  }
}

function onChannelChange() {
  selectedBatchId.value = ''
  differences.value = []
  diffTotal.value = 0
  loadBatches()
}

function selectBatch(row) {
  if (!row?.batchId) return
  selectedBatchId.value = row.batchId
  diffPage.value = 1
  loadDifferences()
}

async function loadDifferences() {
  if (!selectedBatchId.value) {
    differences.value = []
    diffTotal.value = 0
    return
  }
  diffLoading.value = true
  diffError.value = ''
  try {
    const params = { batchId: selectedBatchId.value, page: diffPage.value, size: diffSize.value }
    if (diffTypeFilter.value !== 'ALL') params.diffType = diffTypeFilter.value
    if (diffStatusFilter.value !== 'ALL') params.status = diffStatusFilter.value
    const { data } = await http.get('/payments/admin/reconcile/differences', { params })
    if (!isOk(data)) {
      diffError.value = data?.message || '加载对账差异失败'
      differences.value = []
      diffTotal.value = 0
      return
    }
    const payload = unwrapResult(data)
    differences.value = payload?.list ?? []
    diffTotal.value = payload?.total ?? differences.value.length
  } catch (err) {
    diffError.value = resolveErrorMessage(err, '加载对账差异失败')
    differences.value = []
    diffTotal.value = 0
  } finally {
    diffLoading.value = false
  }
}

function onDiffFilterChange() {
  diffPage.value = 1
  loadDifferences()
}

async function resolveDiff(row) {
  const ok = await confirmAction({
    message: `确定将差异「${row.differenceId}」标记为已处理？只更新对账清单，不会修改支付/退款金额。`,
    title: '标记已处理',
  })
  if (!ok) return
  resolvingId.value = row.differenceId
  try {
    const { data } = await http.put(
      `/payments/admin/reconcile/differences/${row.differenceId}/resolve`,
      { remark: '管理员复核处理' },
    )
    if (!isOk(data)) {
      showError(data?.message || '标记失败')
      return
    }
    showSuccess('已标记为已处理')
    await loadDifferences()
  } catch (err) {
    showError(resolveErrorMessage(err, '标记失败'))
  } finally {
    resolvingId.value = null
  }
}

onMounted(() => {
  load()
  loadBatches()
})
</script>

<template>
  <div class="space-y-4">

    <PageHero
      title="资金对账"
      subtitle="订单、支付与退款金额的核对视图"
      icon="coupon"
      tone="amber"
    />
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

    <!-- 渠道对账（#137）：导入渠道账单，核对本地支付/退款差异，只出清单不自动修账 -->
    <el-card shadow="never">
      <template #header>
        <div class="flex flex-wrap items-center justify-between gap-2">
          <span class="text-base font-medium">渠道对账</span>
          <span class="text-xs text-gray-400">导入渠道账单 CSV，比对本地支付 / 退款</span>
        </div>
      </template>

      <div class="flex flex-wrap items-center gap-3">
        <el-select
          v-model="reconcileChannel"
          size="small"
          style="width: 170px"
          data-testid="reconcile-channel"
          @change="onChannelChange"
        >
          <el-option v-for="c in RECONCILE_CHANNELS" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
        <input
          ref="billInput"
          type="file"
          accept=".csv"
          class="hidden"
          data-testid="reconcile-file"
          @change="onBillChange"
        />
        <el-button size="small" @click="pickBill">选择账单 CSV</el-button>
        <span class="text-xs text-gray-500">{{ selectedFileName || '未选择文件' }}</span>
        <el-button
          type="primary"
          size="small"
          :loading="importing"
          :disabled="!selectedFile"
          data-testid="reconcile-import"
          @click="importBill"
        >
          导入并对账
        </el-button>
        <el-button size="small" :loading="batchesLoading" @click="loadBatches">刷新批次</el-button>
      </div>
      <p class="text-xs text-gray-400 mt-2">
        表头须为 trade_no,bill_type,out_trade_no,amount_fen,status,trade_time；只比对 status=SUCCESS 的行，金额单位为分。
      </p>

      <div class="mt-4">
        <p class="text-sm font-medium text-gray-800 mb-2">
          对账批次<span class="text-xs text-gray-400 ml-2">点击行查看差异</span>
        </p>
        <el-result v-if="batchesError" status="error" icon="error" :sub-title="batchesError">
          <template #extra>
            <el-button type="primary" size="small" @click="loadBatches">重试</el-button>
          </template>
        </el-result>
        <el-table
          v-else
          v-loading="batchesLoading"
          :data="batches"
          border
          highlight-current-row
          style="width: 100%"
          @current-change="selectBatch"
        >
          <el-table-column prop="batchId" label="批次号" min-width="170" />
          <el-table-column prop="channel" label="渠道" width="100" />
          <el-table-column prop="fileName" label="账单文件" min-width="160" show-overflow-tooltip />
          <el-table-column prop="totalRows" label="比对行数" width="100" align="right" />
          <el-table-column label="差异数" width="90" align="right">
            <template #default="{ row }">
              <span :class="row.diffCount > 0 ? 'text-red-600 font-medium' : 'text-gray-500'">{{ row.diffCount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="导入时间" width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无对账批次，请先导入账单" :image-size="80" />
          </template>
        </el-table>
      </div>

      <div class="mt-4">
        <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
          <p class="text-sm font-medium text-gray-800">
            对账差异
            <span v-if="selectedBatchId" class="text-xs text-gray-400 ml-2">
              批次 {{ selectedBatchId }} · {{ formatTotal(diffTotal, '条', differences.length) }}
            </span>
          </p>
          <div class="flex items-center gap-2">
            <el-select
              v-model="diffTypeFilter"
              size="small"
              style="width: 150px"
              :disabled="!selectedBatchId"
              @change="onDiffFilterChange"
            >
              <el-option label="全部类型" value="ALL" />
              <el-option label="本地有渠道无" value="LOCAL_ONLY" />
              <el-option label="渠道有本地无" value="CHANNEL_ONLY" />
              <el-option label="金额不一致" value="AMOUNT_MISMATCH" />
            </el-select>
            <el-select
              v-model="diffStatusFilter"
              size="small"
              style="width: 120px"
              :disabled="!selectedBatchId"
              @change="onDiffFilterChange"
            >
              <el-option label="全部状态" value="ALL" />
              <el-option label="待处理" value="PENDING" />
              <el-option label="已处理" value="RESOLVED" />
            </el-select>
          </div>
        </div>

        <el-result v-if="diffError" status="error" icon="error" :sub-title="diffError">
          <template #extra>
            <el-button type="primary" size="small" @click="loadDifferences">重试</el-button>
          </template>
        </el-result>
        <template v-else>
          <el-table v-loading="diffLoading" :data="differences" border style="width: 100%">
            <el-table-column prop="differenceId" label="ID" width="80" />
            <el-table-column label="类型" width="80">
              <template #default="{ row }">{{ billTypeText(row.billType) }}</template>
            </el-table-column>
            <el-table-column label="差异" width="130">
              <template #default="{ row }">
                <el-tag :type="diffTypeTag(row.diffType)" size="small">{{ diffTypeText(row.diffType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="billTradeNo" label="渠道流水号" min-width="160" show-overflow-tooltip />
            <el-table-column prop="localNo" label="本地单号" min-width="160" show-overflow-tooltip />
            <el-table-column label="本地金额（元）" width="130" align="right">
              <template #default="{ row }">{{ row.localAmount == null ? '—' : formatMoney(row.localAmount) }}</template>
            </el-table-column>
            <el-table-column label="渠道金额（元）" width="130" align="right">
              <template #default="{ row }">{{ row.channelAmount == null ? '—' : formatMoney(row.channelAmount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="diffStatusTag(row.status)" size="small">{{ diffStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'PENDING'"
                  link
                  type="primary"
                  size="small"
                  :loading="resolvingId === row.differenceId"
                  :disabled="!!resolvingId && resolvingId !== row.differenceId"
                  data-testid="reconcile-resolve"
                  @click="resolveDiff(row)"
                >
                  标记已处理
                </el-button>
                <span v-else class="text-xs text-gray-400">{{ row.resolvedBy ? '已处理' : '—' }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty :description="selectedBatchId ? '该批次无差异' : '请选择上方批次查看差异'" :image-size="80" />
            </template>
          </el-table>
          <div v-if="selectedBatchId && diffTotal > diffSize" class="flex justify-end mt-3">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="diffTotal"
              :page-size="diffSize"
              :current-page="diffPage"
              :page-sizes="[20, 50, 100]"
              @current-change="onDiffPageChange"
              @size-change="onDiffSizeChange"
            />
          </div>
        </template>
      </div>

      <p class="text-xs text-gray-400 mt-3">
        说明：渠道对账只生成差异清单供人工复核，标记已处理不会改动支付 / 退款金额；如需修账请走对应支付 / 退款流程。
      </p>
    </el-card>
  </div>
</template>
