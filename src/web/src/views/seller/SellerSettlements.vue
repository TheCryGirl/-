<script setup>
import PageHero from '../../components/store/PageHero.vue'
/**
 * 卖家结算单（#144 第二块）
 *
 * 卖家只读自己的结算单（后端按 X-User-Role: SELLER 强制只看本人，query 指定
 * 他人 sellerId 会被拒绝）；金额一律后端按分返回，前端只做元展示；
 * 生成与标记打款都在管理端，本页不提供任何写操作。
 */
import { onMounted, ref } from 'vue'
import UiTable from '../../components/ui/UiTable.vue'
import { isOk, settlementApi, unwrapResult } from '../../api'
import { resolveErrorMessage } from '../../components/ui/message'
import { usePagination } from '../../composables/usePagination'
import { formatPrice, formatTime } from '../../utils/format'
import {
  formatSettlementPeriod,
  settlementStatusTag,
  settlementStatusText,
} from '../../utils/settlement'

/* ---------- 1. 状态 ---------- */
const list = ref([])
const loadError = ref('')
/** 当前查看明细的结算单；null 表示弹窗关闭 */
const detail = ref(null)
const detailVisible = ref(false)

const { page, size, total, loading, onPageChange, onSizeChange } = usePagination({
  defaultSize: 10,
  onChange: () => load(),
})

/* ---------- 2. 列定义 ---------- */
const columns = [
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
    const { data } = await settlementApi.list({ page: page.value, size: size.value })
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

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="max-w-5xl mx-auto space-y-4">
    <PageHero
      title="结算单"
      subtitle="平台按自然月生成结算单：应结金额 = 订单实付 − 平台佣金，打款后状态变为「已打款」"
      icon="wallet"
      tone="amber"
    />

    <el-result v-if="loadError" status="error" title="加载失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="seller-settlements-retry" @click="load">
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
        actions-label="明细"
        :actions-width="90"
        empty-text="还没有结算单：订单完成并确认收货后，平台会按自然月生成"
        data-testid="seller-settlements-table"
      >
        <template #toolbar>
          <span class="text-xs text-gray-500">
            共 {{ total }} 张结算单；对金额有疑问请联系平台客服处理
          </span>
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
          <el-button size="small" @click="openDetail(row)">查看明细</el-button>
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

    <el-dialog v-model="detailVisible" title="结算单明细" width="640px">
      <template v-if="detail">
        <div class="flex flex-wrap gap-x-8 gap-y-2 text-sm mb-4">
          <span>结算周期：{{ formatSettlementPeriod(detail.periodStart) }}</span>
          <span>状态：{{ settlementStatusText(detail.status) }}</span>
        </div>
        <el-table :data="detail.items || []" size="small" data-testid="seller-settlements-items">
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
