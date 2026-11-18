<script setup>
import PageHero from '../../components/store/PageHero.vue'
/**
 * 管理端结算管理（#144 第二块）
 *
 * 管理员按自然月核算已确认收货的子订单，按退款后净实付计佣。
 * 已打款单冻结；后续退款的差额计入下一期。本页仅登记人工打款凭证。
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
const payoutTarget = ref(null)
const payoutVisible = ref(false)
const payoutReference = ref('')
const payoutRemark = ref('')
const detail = ref(null)
const detailVisible = ref(false)
const rulesVisible = ref(false)
const rulesLoading = ref(false)
const ruleSaving = ref(false)
const ruleError = ref('')
const rules = ref([])
const ruleForm = ref({ scope: 'PLATFORM', targetId: '', rate: null })

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
      message: `按 ${period} 核算已确认收货的订单？系统会扣除成功退款，重算未打款账单，并分配可冲减余额。`,
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

function openPayout(row) {
  if (!row.payable) return
  payoutTarget.value = row
  payoutReference.value = ''
  payoutRemark.value = ''
  payoutVisible.value = true
}

async function markPaid() {
  const row = payoutTarget.value
  const reference = payoutReference.value.trim()
  if (!row || !row.payable || payingId.value) return
  if (!reference) {
    showError('请填写打款凭证编号')
    return
  }
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
    const { data } = await settlementApi.updateStatus(row.settlementId, {
      status: 'PAID',
      expectedNetAmount: row.netAmount,
      payoutReference: reference,
      payoutRemark: payoutRemark.value.trim(),
    })
    if (!isOk(data)) {
      showError(data?.message || '登记打款失败')
      if (Number(data?.code) === 60017) {
        payoutVisible.value = false
        await load()
      }
      return
    }
    payoutVisible.value = false
    showSuccess('已登记打款凭证')
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

async function loadRules() {
  rulesVisible.value = true
  rulesLoading.value = true
  ruleError.value = ''
  try {
    const { data } = await settlementApi.commissionRules()
    if (!isOk(data)) throw new Error(data?.message || '加载佣金规则失败')
    rules.value = unwrapResult(data) || []
  } catch (err) {
    rules.value = []
    ruleError.value = resolveErrorMessage(err, '加载佣金规则失败')
  } finally {
    rulesLoading.value = false
  }
}

async function saveRule() {
  if (ruleSaving.value) return
  const { scope, targetId, rate } = ruleForm.value
  const target = scope === 'PLATFORM' ? '*' : targetId.trim()
  if (!target || !Number.isInteger(rate) || rate < 0 || rate > 10000) {
    showError('请填写规则目标和 0–10000 的整数万分比费率')
    return
  }
  ruleSaving.value = true
  try {
    const { data } = await settlementApi.saveCommissionRule({ scope, targetId: target, rate })
    if (!isOk(data)) throw new Error(data?.message || '保存佣金规则失败')
    showSuccess('佣金规则已保存，仅对未入账订单生效')
    await loadRules()
  } catch (err) {
    showError(resolveErrorMessage(err, '保存佣金规则失败'))
  } finally {
    ruleSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="max-w-6xl mx-auto space-y-4">
    <PageHero
      title="结算管理"
      subtitle="按自然月生成：计入退款净额、类目佣金和前期冲减；月结后可登记打款凭证"
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
          <el-button @click="loadRules">佣金规则</el-button>
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
            :disabled="!row.payable"
            :loading="payingId === row.settlementId"
            @click="openPayout(row)"
          >
            {{ row.payable ? '登记打款' : '周期未结束' }}
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

    <el-dialog v-model="payoutVisible" title="登记已完成的打款" width="500px">
      <p v-if="payoutTarget" class="mb-4 text-sm">
        确认金额 ¥{{ formatPrice(payoutTarget.netAmount) }}。这里只登记凭证，不发起转账。
      </p>
      <el-form label-position="top">
        <el-form-item label="打款凭证编号">
          <el-input v-model="payoutReference" maxlength="100" />
        </el-form-item>
        <el-form-item label="备注（可选）">
          <el-input v-model="payoutRemark" maxlength="255" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payoutVisible = false">取消</el-button>
        <el-button type="primary" :loading="!!payingId" @click="markPaid">登记凭证</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rulesVisible" title="佣金规则" width="680px">
      <el-alert v-if="ruleError" :title="ruleError" type="error" :closable="false" />
      <el-table v-else v-loading="rulesLoading" :data="rules" empty-text="暂无佣金规则" size="small">
        <el-table-column prop="scope" label="范围" />
        <el-table-column prop="targetId" label="目标 ID" />
        <el-table-column prop="rate" label="费率（万分比）" />
      </el-table>
      <el-form class="mt-4" label-position="top">
        <el-form-item label="规则范围">
          <el-select v-model="ruleForm.scope">
            <el-option label="平台" value="PLATFORM" />
            <el-option label="类目" value="CATEGORY" />
            <el-option label="店铺（卖家 ID）" value="SHOP" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="ruleForm.scope !== 'PLATFORM'" label="类目 ID 或卖家 ID">
          <el-input v-model="ruleForm.targetId" maxlength="32" />
        </el-form-item>
        <el-form-item label="费率（万分比，例如 300 表示 3%）">
          <el-input-number v-model="ruleForm.rate" :min="0" :max="10000" :precision="0" />
        </el-form-item>
        <el-button type="primary" :loading="ruleSaving" @click="saveRule">保存或更新规则</el-button>
        <el-button v-if="ruleError" @click="loadRules">重试加载</el-button>
      </el-form>
    </el-dialog>

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
        <div v-if="detail.adjustments?.length" class="mt-3 text-sm">
          <p v-for="adjustment in detail.adjustments" :key="`${adjustment.sourceSettlementId}-${adjustment.orderId}-${adjustment.amount}`">
            前期退款冲减：{{ adjustment.orderId }}（原单 {{ adjustment.sourceSettlementId }}）−¥{{ formatPrice(adjustment.amount) }}
          </p>
        </div>
        <p v-if="detail.payout" class="mt-3 text-sm">打款凭证：{{ detail.payout.reference }} · {{ formatTime(detail.payout.paidAt) }}</p>
        <div class="text-right mt-3 text-sm">
          应结金额：
          <span class="font-semibold text-amber-600">¥{{ formatPrice(detail.netAmount) }}</span>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
