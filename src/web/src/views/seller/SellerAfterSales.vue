<script setup>
import PageHero from '../../components/store/PageHero.vue'
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
import { http, isOk, paymentApi, unwrapResult } from '../../api'
import { AFTER_SALE_STATUS_TAG, statusTag } from '../../constants/status'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess, showWarning } from '../../components/ui/message'
import { formatPrice, formatTime, formatTotal } from '../../utils/format'
import {
  afterSaleReasonLabel,
  afterSaleStatusLabel,
  afterSaleStatusOptions,
  afterSaleStepIndex,
  afterSaleSteps,
  afterSaleTypeLabel,
  uploadAfterSaleEvidence,
} from '../../utils/afterSale'

const EVIDENCE_LIMIT = 3
const EVIDENCE_MAX_SIZE = 5 * 1024 * 1024

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
/* #143 仲裁中补充材料：ticketId -> { remark, files } */
const supplements = reactive({})
const supplementSubmitting = ref('')

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
    list.value.forEach((ticket) => {
      if (!supplements[ticket.ticketId]) {
        supplements[ticket.ticketId] = { remark: '', files: [] }
      }
    })
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

/* ---------- #143 仲裁期间补充材料 ---------- */
function onSupplementFileChange(ticket, event) {
  const picked = Array.from(event.target.files || [])
  event.target.value = ''
  const state = supplements[ticket.ticketId] || { remark: '', files: [] }
  const invalidSize = picked.find((file) => file.size > EVIDENCE_MAX_SIZE)
  if (invalidSize) {
    showError(`${invalidSize.name} 超过 5MB，不能上传`)
    return
  }
  const merged = [...state.files, ...picked.map((file) => ({ file, url: URL.createObjectURL(file) }))]
  if (merged.length > EVIDENCE_LIMIT) {
    showWarning(`单次最多上传 ${EVIDENCE_LIMIT} 张凭证`)
    merged.slice(state.files.length).forEach((item) => URL.revokeObjectURL(item.url))
    return
  }
  supplements[ticket.ticketId] = { remark: state.remark || '', files: merged }
}

function removeSupplementFile(ticket, index) {
  const state = supplements[ticket.ticketId]
  if (!state) return
  const [removed] = state.files.splice(index, 1)
  if (removed?.url) URL.revokeObjectURL(removed.url)
}

async function submitSupplement(ticket) {
  if (supplementSubmitting.value) return
  const state = supplements[ticket.ticketId] || { remark: '', files: [] }
  const remark = (state.remark || '').trim()
  if (!remark && !state.files.length) {
    showWarning('请填写补充说明或上传凭证')
    return
  }

  supplementSubmitting.value = ticket.ticketId
  try {
    let attachmentUrls = []
    if (state.files.length) {
      const { data } = await uploadAfterSaleEvidence(state.files.map((item) => item.file))
      if (!isOk(data)) {
        showError(data?.message || '凭证上传失败')
        return
      }
      const urls = unwrapResult(data)
      if (!Array.isArray(urls) || urls.length !== state.files.length) {
        showError('凭证上传失败，返回结果异常')
        return
      }
      attachmentUrls = urls
    }
    const { data } = await http.post(`/payments/after-sales/${ticket.ticketId}/supplement`, {
      remark: remark || null,
      attachmentUrls,
    })
    if (!isOk(data)) {
      showError(data?.message || '补充材料失败')
      return
    }
    showSuccess(`售后单 ${ticket.ticketId} 已提交补充材料`)
    state.remark = ''
    state.files.forEach((item) => item.url && URL.revokeObjectURL(item.url))
    state.files = []
    await fetchList()
  } catch (err) {
    showError(resolveErrorMessage(err, '补充材料失败'))
  } finally {
    supplementSubmitting.value = ''
  }
}

/* ---------- 3. 生命周期 ---------- */
onMounted(fetchList)
</script>

<template>
  <div class="space-y-4">

    <PageHero
      title="售后处理"
      subtitle="跟踪退货物流、确认收货与重新发货"
      icon="headset"
      tone="amber"
    >
      <template #meta>
        <span v-if="!loadError" class="text-xs text-[var(--jd-ink-4)]">{{ formatTotal(total, '单', list.length) }}</span>
      </template>
    </PageHero>

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
        <p class="text-sm text-gray-500 mt-1">
          原因：{{ ticket.reasonCode ? afterSaleReasonLabel(ticket.reasonCode) : (ticket.reason || '—') }}
          <span v-if="ticket.reasonCode && ticket.reason" class="text-gray-400">（{{ ticket.reason }}）</span>
        </p>

        <el-alert
          v-if="ticket.status === 'ARBITRATING'"
          type="warning"
          :closable="false"
          show-icon
          class="mt-2"
          title="该单已进入平台仲裁，锁定状态下不能审核，请补充材料等待平台裁定"
        />

        <el-alert
          v-else-if="ticket.status === 'CANCELLED'"
          type="info"
          :closable="false"
          show-icon
          class="mt-2"
          :title="ticket.auditRemark ? `买家已撤销：${ticket.auditRemark}` : '买家已撤销该售后单'"
        />

        <el-steps
          v-if="!['REJECTED', 'CANCELLED', 'ARBITRATING'].includes(ticket.status)"
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

          <!-- #143 仲裁中：补充材料，等待平台裁定 -->
          <div v-if="ticket.status === 'ARBITRATING'" class="space-y-2">
            <div class="flex flex-wrap items-center gap-2">
              <el-input
                v-model="supplements[ticket.ticketId].remark"
                maxlength="255"
                placeholder="补充说明（选填）"
                style="max-width: 420px"
                :data-testid="`seller-after-sale-supplement-remark-${ticket.ticketId}`"
              />
              <label
                class="inline-flex items-center px-3 h-8 rounded border border-dashed border-gray-300 text-xs text-gray-500 cursor-pointer hover:border-blue-400 hover:text-blue-400"
              >
                上传凭证
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp,image/gif"
                  multiple
                  class="sr-only"
                  :data-testid="`seller-after-sale-supplement-file-${ticket.ticketId}`"
                  @change="onSupplementFileChange(ticket, $event)"
                />
              </label>
              <el-button
                type="primary"
                :loading="supplementSubmitting === ticket.ticketId"
                :data-testid="`seller-after-sale-supplement-submit-${ticket.ticketId}`"
                @click="submitSupplement(ticket)"
              >
                {{ supplementSubmitting === ticket.ticketId ? '提交中…' : '提交补充材料' }}
              </el-button>
            </div>
            <div v-if="supplements[ticket.ticketId].files.length" class="flex flex-wrap gap-2">
              <div
                v-for="(item, index) in supplements[ticket.ticketId].files"
                :key="item.url"
                class="relative w-16 h-16 rounded-lg overflow-hidden border border-gray-200"
              >
                <img :src="item.url" :alt="item.file.name" class="w-full h-full object-cover" />
                <button
                  type="button"
                  class="absolute top-0 right-0 w-4 h-4 text-xs leading-4 text-center text-white bg-black/60 hover:bg-black/80"
                  @click="removeSupplementFile(ticket, index)"
                >
                  ×
                </button>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>
