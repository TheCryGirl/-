<script setup>
import PageHero from '../../components/store/PageHero.vue'
import { onMounted, reactive, ref } from 'vue'
import { http, isOk, paymentApi, unwrapResult } from '../../api'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import { formatPrice, formatTime, formatTotal } from '../../utils/format'
import {
  afterSaleReasonLabel,
  afterSaleStatusLabel,
  afterSaleTypeLabel,
} from '../../utils/afterSale'

/* ---------- 1. 状态 ---------- */
const tickets = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
/** 裁定表单：ticketId -> { decision, remark } */
const rulings = reactive({})
const processing = ref('')

/* ---------- 2. 方法 ---------- */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await paymentApi.afterSales({ status: 'ARBITRATING', page: 1, size: 50 })
    if (!isOk(data)) {
      loadError.value = data?.message || '加载仲裁售后单失败'
      tickets.value = []
      total.value = 0
      return
    }
    const page = unwrapResult(data)
    const list = page?.list ?? []
    total.value = page?.total ?? list.length
    // 列表是轻量结构，逐单拉详情取凭证与时间线；单条失败不影响整体展示
    tickets.value = await Promise.all(
      list.map(async (ticket) => {
        try {
          const { data: detailData } = await paymentApi.afterSaleDetail(ticket.ticketId)
          if (isOk(detailData) && unwrapResult(detailData)) {
            return { ...ticket, ...unwrapResult(detailData) }
          }
        } catch {
          // 详情失败仍展示列表字段
        }
        return ticket
      }),
    )
    tickets.value.forEach((ticket) => {
      if (!rulings[ticket.ticketId]) {
        rulings[ticket.ticketId] = { decision: '', remark: '' }
      }
    })
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载仲裁售后单失败')
    tickets.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function submitRuling(ticket) {
  const state = rulings[ticket.ticketId] || { decision: '', remark: '' }
  const decision = state.decision
  const remark = (state.remark || '').trim()
  if (!decision) {
    showError('请选择裁定结果')
    return
  }
  if (!remark) {
    showError('裁定理由不能为空')
    return
  }
  if (processing.value) return

  const ok = await confirmAction({
    message:
      decision === 'REFUND'
        ? `确定对售后单「${ticket.ticketId}」裁定支持买家退款 ¥${formatPrice(ticket.amount)}？`
        : `确定驳回售后单「${ticket.ticketId}」的仲裁申请？`,
    title: '平台裁定',
    danger: decision === 'REFUND',
  })
  if (!ok) return

  processing.value = ticket.ticketId
  try {
    const { data } = await http.post(`/payments/after-sales/${ticket.ticketId}/arbitrate`, {
      decision,
      remark,
    })
    if (!isOk(data)) {
      showError(data?.message || '裁定失败')
      return
    }
    showSuccess(`售后单 ${ticket.ticketId} 已裁定：${decision === 'REFUND' ? '支持买家退款' : '驳回仲裁申请'}`)
    delete rulings[ticket.ticketId]
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, '裁定失败'))
  } finally {
    processing.value = ''
  }
}

function operatorLabel(ticket, operatorId) {
  if (!operatorId) return '—'
  if (operatorId === 'SYSTEM') return '系统'
  if (operatorId === ticket.buyerId) return '买家'
  if (operatorId === ticket.sellerId) return '卖家'
  return operatorId.startsWith('ADMIN') ? '平台' : operatorId
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">

    <PageHero
      title="售后仲裁"
      subtitle="对进入平台仲裁的售后单做出最终裁定，材料与时间线对双方可见"
      icon="headset"
      tone="indigo"
    />

    <el-result v-if="loadError" status="error" icon="error" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>

    <el-card v-else shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-base font-medium">仲裁中售后单</span>
          <span v-if="!loadError" class="text-xs text-gray-400">{{ formatTotal(total, '单', tickets.length) }}</span>
        </div>
      </template>

      <div v-loading="loading" class="min-h-32">
        <el-empty v-if="!loading && !tickets.length" description="暂无仲裁中售后单" />

        <div v-else class="divide-y divide-gray-100">
          <div
            v-for="ticket in tickets"
            :key="ticket.ticketId"
            class="py-4 first:pt-0 last:pb-0"
            :data-testid="`admin-arbitration-${ticket.ticketId}`"
          >
            <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
              <div class="flex flex-wrap items-center gap-2">
                <span class="text-sm font-medium text-gray-900">{{ afterSaleTypeLabel(ticket.type) }}</span>
                <span class="text-sm text-gray-500">售后单：{{ ticket.ticketId }}</span>
                <el-tag size="small" type="warning">{{ afterSaleStatusLabel(ticket.status) }}</el-tag>
              </div>
              <span class="text-xs text-gray-400">{{ formatTime(ticket.createdAt) }}</span>
            </div>
            <div class="grid gap-1 sm:grid-cols-2 text-sm">
              <p class="text-gray-900">订单：{{ ticket.orderId || '—' }}</p>
              <p class="text-gray-900">买家：{{ ticket.buyerId }} / 卖家：{{ ticket.sellerId }}</p>
              <p v-if="ticket.amount > 0" class="text-gray-700">
                申请退款：<span class="text-red-600 font-medium">¥{{ formatPrice(ticket.amount) }}</span>
              </p>
              <p class="text-gray-500">
                原因：{{ ticket.reasonCode ? afterSaleReasonLabel(ticket.reasonCode) : (ticket.reason || '—') }}
                <span v-if="ticket.reasonCode && ticket.reason">（{{ ticket.reason }}）</span>
              </p>
            </div>

            <!-- 凭证与时间线 -->
            <div class="mt-2 space-y-2">
              <div v-if="(ticket.attachments || []).length" class="flex flex-wrap gap-2">
                <img
                  v-for="att in ticket.attachments"
                  :key="att.id || att.url"
                  :src="att.url"
                  :alt="att.url"
                  class="w-16 h-16 rounded-lg object-cover border border-gray-200"
                />
              </div>
              <el-timeline v-if="(ticket.events || []).length" class="pt-1">
                <el-timeline-item
                  v-for="eventItem in ticket.events"
                  :key="eventItem.id || `${eventItem.createdAt}-${eventItem.toStatus}`"
                  :timestamp="formatTime(eventItem.createdAt)"
                  placement="top"
                >
                  <div class="text-xs text-gray-600 space-y-0.5">
                    <p>
                      状态：{{ afterSaleStatusLabel(eventItem.toStatus) }}
                      <span v-if="eventItem.fromStatus" class="text-gray-400">
                        （原 {{ afterSaleStatusLabel(eventItem.fromStatus) }}）
                      </span>
                    </p>
                    <p>操作人：{{ operatorLabel(ticket, eventItem.operatorId) }}</p>
                    <p v-if="eventItem.remark" class="break-words">备注：{{ eventItem.remark }}</p>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </div>

            <!-- 裁定表单 -->
            <div class="flex flex-wrap items-center gap-2 mt-3">
              <el-select
                v-model="rulings[ticket.ticketId].decision"
                placeholder="裁定结果"
                style="width: 140px"
                :data-testid="`admin-arbitration-decision-${ticket.ticketId}`"
              >
                <el-option
                  v-if="ticket.type !== 'EXCHANGE'"
                  label="支持退款"
                  value="REFUND"
                />
                <el-option label="驳回申请" value="REJECT" />
              </el-select>
              <el-input
                v-model="rulings[ticket.ticketId].remark"
                maxlength="255"
                placeholder="裁定理由（必填）"
                style="max-width: 420px"
                :data-testid="`admin-arbitration-remark-${ticket.ticketId}`"
              />
              <el-button
                type="danger"
                :disabled="Boolean(processing) && processing !== ticket.ticketId"
                :loading="processing === ticket.ticketId"
                :data-testid="`admin-arbitration-submit-${ticket.ticketId}`"
                @click="submitRuling(ticket)"
              >
                {{ processing === ticket.ticketId ? '裁定中…' : '提交裁定' }}
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>
