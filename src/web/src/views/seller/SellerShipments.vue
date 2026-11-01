<script setup>
/**
 * 卖家待发货（#81 T03）
 *
 * 列表仍是 GET /orders?role=seller&status=PAID（原参数不变），
 * 发货仍是 POST /orders/{orderId}/ship + trackingNo。
 *
 * 原代码用浏览器 prompt 收集物流单号，这里换成 el-dialog + el-form：
 * 取消不发请求，确定才提交；提交按钮 loading，处理中忽略重复点击。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { isOk, orderApi, unwrapResult } from '../../api'
import { ORDER_STATUS_MAP, ORDER_STATUS_TAG, statusTag, statusText } from '../../constants/status'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import UiTable from '../../components/ui/UiTable.vue'

/* ---------- 1. 状态 ---------- */
const list = ref([])
const loading = ref(false)
const loadError = ref('')
const processing = ref('')

const shipVisible = ref(false)
const shipFormRef = ref(null)
const shipForm = reactive({ orderId: '', orderNo: '', trackingNo: '' })
const shipRules = {
  trackingNo: [{ required: true, message: '请输入物流单号', trigger: 'blur' }],
}

/* ---------- 2. 计算属性 ---------- */
const columns = computed(() => [
  { key: 'orderNo', label: '订单号', width: 190, formatter: (row) => row.orderNo || row.orderId || '—' },
  { key: 'product', label: '商品', minWidth: 220, formatter: (row) => firstProductTitle(row) },
  { key: 'receiver', label: '收货人', minWidth: 180, formatter: (row) => receiverText(row) },
  { key: 'payAmount', label: '实付金额', width: 120, align: 'right', formatter: (row) => formatMoney(row.payAmount) },
  {
    key: 'status',
    label: '状态',
    width: 110,
    align: 'center',
    slot: 'status',
  },
])

/* ---------- 3. 方法 ---------- */
async function fetchList() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await orderApi.list({ role: 'seller', status: 'PAID', page: 1, size: 50 })
    if (!isOk(data)) {
      loadError.value = data?.message || '加载待发货订单失败'
      list.value = []
      return
    }
    const payload = unwrapResult(data)
    list.value = Array.isArray(payload) ? payload : payload?.list ?? []
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载待发货订单失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function openShip(row) {
  shipForm.orderId = row.orderId
  shipForm.orderNo = row.orderNo || ''
  shipForm.trackingNo = ''
  shipVisible.value = true
}

async function handleShip() {
  if (processing.value) return
  try {
    await shipFormRef.value?.validate()
  } catch {
    return
  }

  const orderId = shipForm.orderId
  processing.value = orderId
  try {
    const { data } = await orderApi.ship(orderId, { trackingNo: shipForm.trackingNo.trim() })
    if (!isOk(data)) {
      showError(data?.message || '发货失败')
      return
    }
    showSuccess('发货成功')
    shipVisible.value = false
    await fetchList()
  } catch (err) {
    showError(resolveErrorMessage(err, '发货失败'))
  } finally {
    processing.value = ''
  }
}

function firstProductTitle(row) {
  const title = row.items?.[0]?.productTitle
  const count = row.items?.length ?? 0
  if (!title) return '—'
  return count > 1 ? `${title} 等 ${count} 件` : title
}

function receiverText(row) {
  const receiver = row.addressSnapshot?.receiver
  const phone = row.addressSnapshot?.phone
  if (!receiver && !phone) return '—'
  return [receiver, phone].filter(Boolean).join(' ')
}

/** 金额：后端单位是分 */
function formatMoney(cents) {
  const n = Number(cents)
  return Number.isFinite(n) ? `¥${(n / 100).toFixed(2)}` : '-'
}

/* ---------- 4. 生命周期 ---------- */
onMounted(fetchList)
</script>

<template>
  <div class="space-y-4">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="卖家订单列表依赖 GET /api/orders?role=seller，该参数尚未在《05-接口契约》中定义，待后端补充。"
    />

    <el-card shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-base font-medium">待发货订单</span>
          <span class="text-xs text-gray-400">共 {{ list.length }} 单</span>
        </div>
      </template>

      <el-result v-if="loadError" status="error" title="加载待发货订单失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" data-testid="seller-shipments-retry" @click="fetchList">重试</el-button>
        </template>
      </el-result>

      <UiTable
        v-else
        :columns="columns"
        :data="list"
        :loading="loading"
        row-key="orderId"
        empty-text="暂无待发货订单"
        show-actions
        actions-width="110"
      >
        <template #status="{ row }">
          <el-tag :type="statusTag(ORDER_STATUS_TAG, row.status)" size="small">
            {{ statusText(ORDER_STATUS_MAP, row.status) }}
          </el-tag>
        </template>

        <template #actions="{ row }">
          <el-button
            link
            type="primary"
            size="small"
            :loading="processing === row.orderId"
            :data-testid="`seller-shipments-ship-${row.orderId}`"
            @click="openShip(row)"
          >
            发货
          </el-button>
        </template>
      </UiTable>
    </el-card>

    <!-- 发货：原字段只有物流单号，取消不发请求 -->
    <el-dialog v-model="shipVisible" title="填写物流单号" width="420px" destroy-on-close>
      <el-form ref="shipFormRef" :model="shipForm" :rules="shipRules" label-width="80px">
        <el-form-item label="订单号">
          <span class="text-sm text-gray-600">{{ shipForm.orderNo || shipForm.orderId }}</span>
        </el-form-item>
        <el-form-item label="物流单号" prop="trackingNo">
          <el-input
            v-model="shipForm.trackingNo"
            maxlength="64"
            placeholder="请输入物流单号"
            data-testid="seller-shipments-tracking-no"
            @keyup.enter="handleShip"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button data-testid="seller-shipments-cancel" @click="shipVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="Boolean(processing)"
          data-testid="seller-shipments-submit"
          @click="handleShip"
        >
          确认发货
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
