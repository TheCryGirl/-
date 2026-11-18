<script setup>
import PageHero from '../../components/store/PageHero.vue'
/**
 * 管理端结算管理（#144 第二块）
 *
 * 管理员按周期（默认当前自然月，格式 yyyy-MM）生成结算单：只结算已确认收货
 * （COMPLETED）且未退款的子订单，佣金按店铺>平台的费率回退计算；同一周期
 * 重复生成只追加新订单，已打款的结算单冻结。标记打款为终态操作。
 */
import { onMounted, ref } from 'vue'
import UiTable from '../../components/ui/UiTable.vue'
import { isOk, settlementApi, unwrapResult } from '../../api'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import { usePagination } from '../../composables/usePagination'
import { formatPrice, formatTime } from '../../utils/format'
import {
  currentMonthPeriod,
  formatSettlementPeriod,
  isSettlementPaid,
  isValidPeriod,
  settlementStatusTag,
  settlementStatusText,
} from '../../utils/settlement'

/* ---------- 1. 状态 ---------- */
const list = ref([])
const loadError = ref('')
const periodInput = ref(currentMonthPeriod())
const sellerIdInput = ref('')
const generating = ref(false)
const payingId = ref('')
const detail = ref(null)
const detailVisible = ref(false)

const { page, size, total, loading, onPageChange, onSizeChange } = usePagination({
  defaultSize: 10,
  onChange: () => load(),
})

/* ---------- 2. 列定义 ---------- */
const columns = [
  { key: 'settlementId', label: '结算单号', minWidth: 200, slot: 'settlementId' },
  { key: 'sellerId', label: '卖家ID', width: 120 },
  {
    key: 'period',
    label: '结算周期',
    width: 110,
    formatter: (row) => formatSettlementPeriod(row.periodStart),
  },
  {
    key: 'grossAmount',
    label: '订单实付（元）',
    align: 'right',
    formatter: (row) => formatPrice(row.grossAmount),
  },
  {
    key: 'commissionAmount',
    label: '佣金（元）',
    align: 'right',
    formatter: (row) => formatPrice(row.commissionAmount),
  },
  { key: 'netAmount', label: '应结（元）', align: 'right', slot: 'net' },
  { key: 'status', label: '状态', width: 100, align: 'center', slot: 'status' },
  {
    key: 'createdAt',
    label: '生成时间',
    width: 150,
    formatter: (row) => formatTime(row.createdAt),
  },
]

/* ---------- 3. 方法 ---------- */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: page.value, size: size.value }
    if (isValidPeriod(periodInput.value)) params.period = periodInput.value.trim()
    if (sellerIdInput.value.trim()) params.sellerId = sellerIdInput.value.trim()
    const { data } = await settlementApi.list(params)
    if (!isOk(data)) {
      loadError.value = data?.message || '加载结算单失败'
      list.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data) || {}
    list.value = payload.list || []
    total.value = Number(payload.total) || 0
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载结算单失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  load()
}

async function generate() {
  if (generating.value) return
  const period = periodInput.value.trim()
  if (!isValidPeriod(period)) {
    showError('周期格式应为 yyyy-MM，例如 2026-11')
    return
  }
  if (
    !(await confirmAction({
      message: `按 ${period} 生成结算单？只结算该周期内已确认收货且未退款的订单，同一周期重复生成只追加新订单。`,
      title: '生成结算单',
      confirmButtonText: '生成',
    }))
  ) {
    return
  }
  generating.value = true
  try {
    const { data } = await settlementApi.generate({ period })
    if (!isOk(data)) {
      showError(data?.message || '生成结算单失败')
      return
    }
    const result = unwrapResult(data) || {}
    showSuccess(
      `生成完成：新建 ${result.created || 0} 张、追加 ${result.updated || 0} 张、` +
        `跳过 ${result.skippedFrozen || 0} 张，涉及订单 ${result.orderCount || 0} 笔`,
    )
    page.value = 1
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, '生成结算单失败'))
  } finally {
    generating.value = false
  }
}

async function markPaid(row) {
  if (payingId.value) return
  if (
    !(await confirmAction({
      message: `确认已向卖家 ${row.sellerId} 打款？结算周期 ${formatSettlementPeriod(row.periodStart)}，` +
        `应结 ¥${formatPrice(row.netAmount)}。打款后结算单冻结，不能再追加订单。`,
      title: '标记已打款',
      confirmButtonText: '确认打款',
      danger: true,
    }))
  ) {
    return
  }
  payingId.value = row.settlementId
  try {
    const { data } = await settlementApi.updateStatus(row.settlementId, { status: 'PAID' })
    if (!isOk(data)) {
      showError(data?.message || '标记打款失败')
      return
    }
    showSuccess('已标记为已打款')
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, '标记打款失败'))
  } finally {
    payingId.value = ''
  }
}

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="max-w-6xl mx-auto space-y-4">
    <PageHero
      title="结算管理"
      subtitle="按自然月为各店铺生成结算单：佣金按 店铺 > 平台 费率回退，已退款订单剔除，打款后冻结"
      icon="money"
      tone="amber"
    />

    <el-result v-if="loadError" status="error" title="加载失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="admin-settlements-retry" @click="load">
          重试
        </el-button>
      </template>
    </el-result>

    <el-card v-else shadow="never">
      <UiTable
        :columns="columns"
        :data="list"
        :loading="loading"
        row-key="settlementId"
        show-actions
        actions-label="操作"
        :actions-width="200"
        empty-text="暂无结算单：选择周期后点「生成结算单」"
        data-testid="admin-settlements-table"
      >
        <template #toolbar>
          <el-input
            v-model="periodInput"
            placeholder="周期 yyyy-MM"
            class="!w-36"
            data-testid="admin-settlements-period"
          />
          <el-input
            v-model="sellerIdInput"
            placeholder="卖家ID（可选）"
            class="!w-44"
            data-testid="admin-settlements-seller"
          />
          <el-button data-testid="admin-settlements-query" @click="applyFilters">查询</el-button>
          <el-button
            type="primary"
            :loading="generating"
            data-testid="admin-settlements-generate"
            @click="generate"
          >
            {{ generating ? '生成中…' : '生成结算单' }}
          </el-button>
        </template>

        <template #settlementId="{ row }">
          <span class="font-mono text-xs text-gray-500">{{ row.settlementId }}</span>
        </template>

        <template #net="{ row }">
          <span class="font-semibold text-amber-600">¥{{ formatPrice(row.netAmount) }}</span>
        </template>

        <template #status="{ row }">
          <el-tag :type="settlementStatusTag(row.status)" size="small">
            {{ settlementStatusText(row.status) }}
          </el-tag>
        </template>

        <template #actions="{ row }">
          <el-button size="small" @click="openDetail(row)">明细</el-button>
          <el-button
            v-if="!isSettlementPaid(row.status)"
            size="small"
            type="warning"
            :loading="payingId === row.settlementId"
            @click="markPaid(row)"
          >
            标记打款
          </el-button>
        </template>

        <template #pagination>
          <el-pagination
            :current-page="page"
            :page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </template>
      </UiTable>
    </el-card>

    <el-dialog v-model="detailVisible" title="结算单明细" width="680px">
      <template v-if="detail">
        <div class="flex flex-wrap gap-x-8 gap-y-2 text-sm mb-4">
          <span>卖家：{{ detail.sellerId }}</span>
          <span>结算周期：{{ formatSettlementPeriod(detail.periodStart) }}</span>
          <span>状态：{{ settlementStatusText(detail.status) }}</span>
        </div>
        <el-table :data="detail.items || []" size="small" data-testid="admin-settlements-items">
          <el-table-column prop="orderId" label="订单号" min-width="220" />
          <el-table-column label="订单实付（元）" align="right" min-width="120">
            <template #default="{ row }">{{ formatPrice(row.grossAmount) }}</template>
          </el-table-column>
          <el-table-column label="佣金（元）" align="right" min-width="120">
            <template #default="{ row }">{{ formatPrice(row.commissionAmount) }}</template>
          </el-table-column>
        </el-table>
        <div class="text-right mt-3 text-sm">
          应结金额：
          <span class="font-semibold text-amber-600">¥{{ formatPrice(detail.netAmount) }}</span>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
