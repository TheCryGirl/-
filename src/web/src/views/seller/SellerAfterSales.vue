<script setup>
/**
 * 卖家售后处理（#81 T05）
 *
 * 流程与按钮条件完全沿用原代码：
 * - PENDING：同意 / 驳回（仅退款与退货退款文案不同）
 * - BUYER_SHIPPED + RETURN_REFUND：确认收货并退款
 * - BUYER_SHIPPED + EXCHANGE：回填换货物流后确认收货并重新发货
 * - APPROVED：只提示等待买家回填退货物流
 *
 * 状态文案继续用 utils/afterSale.js（买家页与卖家页共用，含换货 COMPLETED），
 * 标签颜色用 constants/status.js 的 AFTER_SALE_STATUS_TAG，未知状态回退 info。
 */
import { onMounted, reactive, ref } from 'vue'
import { isOk, paymentApi, unwrapResult } from '../../api'
import { AFTER_SALE_STATUS_TAG, statusTag } from '../../constants/status'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import { formatPrice, formatTime, formatTotal } from '../../utils/format'
import {
  afterSaleStatusLabel,
  afterSaleStatusOptions,
  afterSaleStepIndex,
  afterSaleSteps,
  afterSaleTypeLabel,
} from '../../utils/afterSale'

/* ---------- 1. 状态 ---------- */
const list = ref([])
const loading = ref(false)
const loadError = ref('')
/** 后端返回的总条数：本页一次取 50 条，超过时给出提示 */
const total = ref(0)
const statusFilter = ref('')
const processingId = ref('')
const remarks = reactive({})
const exchangeTracking = reactive({})

/* ---------- 2. 方法 ---------- */
async function fetchList() {
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: 1, size: 50 }
    if (statusFilter.value) params.status = statusFilter.value
    const { data } = await paymentApi.afterSales(params)
    if (!isOk(data)) {
      loadError.value = data?.message || '售后单加载失败'
      list.value = []
      total.value = 0
      return
    }
    const page = unwrapResult(data)
    list.value = page?.list ?? []
    total.value = page?.total ?? list.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '售后单加载失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function actionLabel(ticket, approved) {
  if (ticket.type === 'REFUND_ONLY') return approved ? '同意退款' : '驳回'
  return approved ? '同意售后' : '驳回'
}

async function handleAudit(ticket, approved) {
  if (processingId.value) return

  const ok = await confirmAction({
    message: `确定${actionLabel(ticket, approved)}售后单「${ticket.ticketId}」？`,
    danger: !approved,
  })
  if (!ok) return

  processingId.value = ticket.ticketId
  try {
    const { data } = await paymentApi.auditAfterSale(ticket.ticketId, {
      approved,
      remark: remarks[ticket.ticketId] || '',
    })
    if (!isOk(data)) {
      showError(data?.message || '处理失败')
      return
    }
    showSuccess(`售后单 ${ticket.ticketId} 已${approved ? '同意' : '驳回'}`)
    remarks[ticket.ticketId] = ''
    await fetchList()
  } catch (err) {
    showError(resolveErrorMessage(err, '处理失败'))
  } finally {
    processingId.value = ''
  }
}

async function handleReceive(ticket) {
  if (processingId.value) return

  const ok = await confirmAction({
    message: `确认已收到售后单「${ticket.ticketId}」的退货并退款？`,
    danger: true,
  })
  if (!ok) return

  processingId.value = ticket.ticketId
  try {
    const { data } = await paymentApi.receiveAfterSaleReturn(ticket.ticketId, {
      remark: remarks[ticket.ticketId] || '确认收货并退款',
    })
    if (!isOk(data)) {
      showError(data?.message || '确认收货失败')
      return
    }
    showSuccess(`售后单 ${ticket.ticketId} 已确认收货并完成退款`)
    remarks[ticket.ticketId] = ''
    await fetchList()
  } catch (err) {
    showError(resolveErrorMessage(err, '确认收货失败'))
  } finally {
    processingId.value = ''
  }
}

async function handleReship(ticket) {
  const trackingNo = (exchangeTracking[ticket.ticketId] || '').trim()
  if (!trackingNo) {
    showError('请填写换货物流单号')
    return
  }
  if (processingId.value) return

  const ok = await confirmAction({
    message: `确认按换货物流「${trackingNo}」重新发货？`,
  })
  if (!ok) return

  processingId.value = ticket.ticketId
  try {
    const { data } = await paymentApi.reshipAfterSale(ticket.ticketId, {
      trackingNo,
      remark: remarks[ticket.ticketId] || '确认收货并重新发货',
    })
    if (!isOk(data)) {
      showError(data?.message || '换货发货失败')
      return
    }
    showSuccess(`售后单 ${ticket.ticketId} 已完成换货发货`)
    exchangeTracking[ticket.ticketId] = ''
    remarks[ticket.ticketId] = ''
    await fetchList()
  } catch (err) {
    showError(resolveErrorMessage(err, '换货发货失败'))
  } finally {
    processingId.value = ''
  }
}

function handleFilterChange() {
  fetchList()
}

/* ---------- 3. 生命周期 ---------- */
onMounted(fetchList)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-gray-900">售后处理</h1>
      <span v-if="!loadError" class="text-xs text-gray-400">{{ formatTotal(total, '单', list.length) }}</span>
    </div>

    <el-alert
      v-if="total > list.length"
      type="info"
      :closable="false"
      show-icon
      :title="`共 ${total} 单，当前仅显示前 ${list.length} 单`"
    />

    <!-- 筛选区：不加卡片 -->
    <el-form inline class="bg-white p-4 rounded-lg">
      <el-form-item label="状态">
        <el-select
          v-model="statusFilter"
          placeholder="全部状态"
          clearable
          style="width: 170px"
          data-testid="seller-after-sales-filter-status"
          @change="handleFilterChange"
        >
          <el-option
            v-for="item in afterSaleStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <span class="text-xs text-gray-400">仅展示本店售后单；管理员进入时可见全量</span>
      </el-form-item>
    </el-form>

    <el-result v-if="loadError" status="error" title="售后单加载失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="seller-after-sales-retry" @click="fetchList">重试</el-button>
      </template>
    </el-result>

    <div v-else v-loading="loading">
      <el-empty v-if="!loading && !list.length" description="暂无售后单" />

      <el-card
        v-for="ticket in list"
        :key="ticket.ticketId"
        shadow="never"
        class="mb-3"
        :data-testid="`seller-after-sale-${ticket.ticketId}`"
      >
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium text-gray-800">{{ afterSaleTypeLabel(ticket.type) }}</span>
            <el-tag :type="statusTag(AFTER_SALE_STATUS_TAG, ticket.status)" size="small">
              {{ afterSaleStatusLabel(ticket.status) }}
            </el-tag>
          </div>
          <span class="text-xs text-gray-400">{{ formatTime(ticket.createdAt) }}</span>
        </div>

        <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-gray-600 mt-2">
          <span>售后单：{{ ticket.ticketId }}</span>
          <span>订单：{{ ticket.orderId }}</span>
          <span>买家：{{ ticket.buyerId }}</span>
          <span v-if="ticket.amount > 0">退款金额 ¥{{ formatPrice(ticket.amount) }}</span>
        </div>
        <p class="text-sm text-gray-500 mt-1">原因：{{ ticket.reason || '—' }}</p>

        <el-steps
          v-if="ticket.status !== 'REJECTED'"
          :active="afterSaleStepIndex(ticket.type, ticket.status) + 1"
          simple
          class="mt-3"
        >
          <el-step v-for="step in afterSaleSteps(ticket.type)" :key="step" :title="step" />
        </el-steps>

        <div class="text-xs text-gray-500 space-y-1 mt-3">
          <p v-if="ticket.refundId">退款单号：{{ ticket.refundId }}</p>
          <p v-if="ticket.returnTrackingNo">退货物流：{{ ticket.returnTrackingNo }}</p>
          <p v-if="ticket.exchangeTrackingNo">换货物流：{{ ticket.exchangeTrackingNo }}</p>
          <p v-if="ticket.auditRemark">处理备注：{{ ticket.auditRemark }}</p>
        </div>

        <el-divider />

        <div class="space-y-3">
          <el-input
            v-if="['PENDING', 'BUYER_SHIPPED'].includes(ticket.status)"
            v-model="remarks[ticket.ticketId]"
            maxlength="255"
            placeholder="处理备注（选填）"
            style="max-width: 420px"
            :data-testid="`seller-after-sale-remark-${ticket.ticketId}`"
          />

          <!-- PENDING：同意 / 驳回 -->
          <div v-if="ticket.status === 'PENDING'" class="flex justify-end gap-2">
            <el-button
              :disabled="processingId === ticket.ticketId"
              :data-testid="`seller-after-sale-reject-${ticket.ticketId}`"
              @click="handleAudit(ticket, false)"
            >
              {{ actionLabel(ticket, false) }}
            </el-button>
            <el-button
              type="success"
              :loading="processingId === ticket.ticketId"
              :data-testid="`seller-after-sale-approve-${ticket.ticketId}`"
              @click="handleAudit(ticket, true)"
            >
              {{ actionLabel(ticket, true) }}
            </el-button>
          </div>

          <!-- 退货退款：卖家收货 -->
          <div
            v-else-if="ticket.status === 'BUYER_SHIPPED' && ticket.type === 'RETURN_REFUND'"
            class="flex justify-end"
          >
            <el-button
              type="success"
              :loading="processingId === ticket.ticketId"
              :data-testid="`seller-after-sale-receive-${ticket.ticketId}`"
              @click="handleReceive(ticket)"
            >
              确认收货并退款
            </el-button>
          </div>

          <!-- 换货：回填物流并重新发货 -->
          <div
            v-else-if="ticket.status === 'BUYER_SHIPPED' && ticket.type === 'EXCHANGE'"
            class="flex flex-wrap items-center justify-end gap-2"
          >
            <el-input
              v-model="exchangeTracking[ticket.ticketId]"
              maxlength="64"
              placeholder="换货物流单号"
              style="width: 240px"
              :data-testid="`seller-after-sale-exchange-tracking-${ticket.ticketId}`"
            />
            <el-button
              type="primary"
              :loading="processingId === ticket.ticketId"
              :data-testid="`seller-after-sale-reship-${ticket.ticketId}`"
              @click="handleReship(ticket)"
            >
              确认收货并重新发货
            </el-button>
          </div>

          <p v-else-if="ticket.status === 'APPROVED'" class="text-xs text-gray-400 text-right">
            已同意，等待买家回填退货物流
          </p>
        </div>
      </el-card>
    </div>
  </div>
</template>
