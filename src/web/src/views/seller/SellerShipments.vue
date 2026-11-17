<script setup>
import PageHero from '../../components/store/PageHero.vue'
/**
 * 卖家待发货（#81 T03，#138 补快递公司）
 *
 * 列表仍是 GET /orders?role=seller&status=PAID（原参数不变）；
 * 发货仍是 PUT /orders/{orderId}/ship，请求体在 #138 增加了 expressCompany，
 * 与 trackingNo 一样必填，后端缺任一项都返回 10001。
 *
 * 快递公司用下拉选择而不是自由文本：后端只认 ExpressCompany 枚举编码，
 * 让用户手打「顺丰」只会换来一个 10001。
 *
 * 原代码用浏览器 prompt 收集物流单号，这里换成 el-dialog + el-form：
 * 取消不发请求，确定才提交；提交按钮 loading，处理中忽略重复点击。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { isOk, orderApi, unwrapResult } from '../../api'
/* 单行导入：tests/integration-roles.test.mjs 用行正则剥离 import，多行会破坏该用例 */
import { EXPRESS_COMPANIES, EXPRESS_COMPANY_MAP, LOGISTICS_STATUS_MAP, LOGISTICS_STATUS_OPTIONS, ORDER_STATUS_MAP, ORDER_STATUS_TAG, statusTag, statusText } from '../../constants/status'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import UiTable from '../../components/ui/UiTable.vue'
import LogisticsTimeline from '../../components/store/LogisticsTimeline.vue'
import { formatTotal } from '../../utils/format'
import { autoConfirmText, validateShipForm } from '../../utils/logistics'

/* ---------- 1. 状态 ---------- */
const list = ref([])
const loading = ref(false)
const loadError = ref('')
/** 后端返回的总条数：本页一次取 50 条，超过时给出提示 */
const total = ref(0)
const processing = ref('')

/**
 * 物流补录（#138）。
 *
 * 本期不接第三方快递 API，轨迹全靠手工录入：卖家在「已发货」里选一笔自己的订单，
 * 逐条补 揽收/运输/派送/签收/异常。页面用同一个组件切两个 tab，
 * 而不是新开一个导航项 —— 发货与补轨迹本来就是同一件事的前后两步。
 */
const activeTab = ref('PAID')
const shippedList = ref([])
const shippedLoading = ref(false)
const shippedError = ref('')
const shippedTotal = ref(0)

const trackVisible = ref(false)
const trackFormRef = ref(null)
const trackForm = reactive({
  orderId: '',
  orderNo: '',
  expressCompanyLabel: '',
  trackingNo: '',
  status: 'IN_TRANSIT',
  description: '',
  occurredAt: '',
})
const trackRules = {
  status: [{ required: true, message: '请选择轨迹状态', trigger: 'change' }],
  description: [{ required: true, message: '请输入轨迹描述', trigger: 'blur' }],
}
const trackSubmitting = ref(false)
const trackError = ref('')

/** 补录抽屉里的当前订单物流详情 */
const currentTracks = ref([])
const currentTracksLoading = ref(false)
const currentTracksError = ref('')
const currentTracksMessage = ref('')
const currentCountdown = ref(null)

const shipVisible = ref(false)
const shipFormRef = ref(null)
const shipForm = reactive({ orderId: '', orderNo: '', expressCompany: '', trackingNo: '' })
const shipRules = {
  expressCompany: [{ required: true, message: '请选择快递公司', trigger: 'change' }],
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

/** 已发货列表比待发货多两列：物流单号与轨迹入口 */
const shippedColumns = computed(() => [
  { key: 'orderNo', label: '订单号', width: 190, formatter: (row) => row.orderNo || row.orderId || '—' },
  { key: 'product', label: '商品', minWidth: 200, formatter: (row) => firstProductTitle(row) },
  { key: 'receiver', label: '收货人', minWidth: 170, formatter: (row) => receiverText(row) },
  {
    key: 'logistics',
    label: '物流',
    minWidth: 180,
    formatter: (row) => expressText(row),
  },
  { key: 'payAmount', label: '实付金额', width: 110, align: 'right', formatter: (row) => formatMoney(row.payAmount) },
  {
    key: 'status',
    label: '状态',
    width: 100,
    align: 'center',
    slot: 'status',
  },
])

const trackStatusOptions = computed(() =>
  LOGISTICS_STATUS_OPTIONS.map((code) => ({
    code,
    label: LOGISTICS_STATUS_MAP[code],
  })),
)

/* ---------- 3. 方法 ---------- */
async function fetchList() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await orderApi.list({ role: 'seller', status: 'PAID', page: 1, size: 50 })
    if (!isOk(data)) {
      loadError.value = data?.message || '加载待发货订单失败'
      list.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    list.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = Array.isArray(payload) ? payload.length : payload?.total ?? list.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载待发货订单失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function openShip(row) {
  shipForm.orderId = row.orderId
  shipForm.orderNo = row.orderNo || ''
  // 每次打开都清空，避免把上一单的快递公司带过来发错
  shipForm.expressCompany = ''
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
  // 与后端同一套规则再校一次：非法编码/超长单号提前拦住，省一次 10001 往返
  const invalid = validateShipForm(shipForm, EXPRESS_COMPANIES.map((item) => item.code))
  if (invalid) {
    showError(invalid)
    return
  }

  const orderId = shipForm.orderId
  processing.value = orderId
  try {
    const { data } = await orderApi.ship(orderId, {
      expressCompany: shipForm.expressCompany,
      trackingNo: shipForm.trackingNo.trim(),
    })
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

async function fetchShipped() {
  shippedLoading.value = true
  shippedError.value = ''
  try {
    const { data } = await orderApi.list({ role: 'seller', status: 'SHIPPED', page: 1, size: 50 })
    if (!isOk(data)) {
      shippedError.value = data?.message || '加载已发货订单失败'
      shippedList.value = []
      shippedTotal.value = 0
      return
    }
    const payload = unwrapResult(data)
    shippedList.value = Array.isArray(payload) ? payload : payload?.list ?? []
    shippedTotal.value = Array.isArray(payload) ? payload.length : payload?.total ?? shippedList.value.length
  } catch (err) {
    shippedError.value = resolveErrorMessage(err, '加载已发货订单失败')
    shippedList.value = []
    shippedTotal.value = 0
  } finally {
    shippedLoading.value = false
  }
}

function reload() {
  if (activeTab.value === 'PAID') fetchList()
  else fetchShipped()
}

/* ---------- 物流补录 ---------- */
async function openTracks(row) {
  trackForm.orderId = row.orderId
  trackForm.orderNo = row.orderNo || ''
  trackForm.expressCompanyLabel = EXPRESS_COMPANY_MAP[row.expressCompany] || row.expressCompany || '快递'
  trackForm.trackingNo = row.trackingNo || ''
  trackForm.status = 'IN_TRANSIT'
  trackForm.description = ''
  trackForm.occurredAt = ''
  trackError.value = ''
  trackVisible.value = true
  await loadCurrentTracks()
}

async function loadCurrentTracks() {
  currentTracksLoading.value = true
  currentTracksError.value = ''
  try {
    const { data } = await orderApi.logistics(trackForm.orderId)
    if (!isOk(data)) {
      currentTracksError.value = data?.message || '加载物流信息失败'
      currentTracks.value = []
      return
    }
    const payload = unwrapResult(data)
    currentTracks.value = payload?.tracks || []
    currentTracksMessage.value = payload?.message || ''
    currentCountdown.value = payload?.autoConfirmRemainingMinutes ?? null
  } catch (err) {
    currentTracksError.value = resolveErrorMessage(err, '加载物流信息失败')
    currentTracks.value = []
  } finally {
    currentTracksLoading.value = false
  }
}

async function submitTrack() {
  if (trackSubmitting.value) return
  try {
    await trackFormRef.value?.validate()
  } catch {
    return
  }

  trackError.value = ''
  trackSubmitting.value = true
  try {
    const payload = {
      status: trackForm.status,
      description: trackForm.description.trim(),
    }
    // 留空表示「就是现在」，交给后端取当前时间；填了就按填写的时间排
    if (trackForm.occurredAt) payload.occurredAt = trackForm.occurredAt
    const { data } = await orderApi.appendLogisticsTrack(trackForm.orderId, payload)
    if (!isOk(data)) {
      trackError.value = data?.message || '补录失败'
      return
    }
    showSuccess('轨迹已补录')
    trackForm.description = ''
    await loadCurrentTracks()
    await fetchShipped()
  } catch (err) {
    trackError.value = resolveErrorMessage(err, '补录失败')
  } finally {
    trackSubmitting.value = false
  }
}

function expressText(row) {
  const company = EXPRESS_COMPANY_MAP[row.expressCompany] || row.expressCompany
  if (!company && !row.trackingNo) return '—'
  return [company, row.trackingNo].filter(Boolean).join(' ')
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

    <PageHero
      title="发货与物流"
      subtitle="待发货订单填快递公司与单号；已发货订单补录物流轨迹"
      icon="truck"
      tone="teal"
    >
      <template #meta>
        <span class="text-xs text-[var(--jd-ink-4)]">
          {{ activeTab === 'PAID' ? formatTotal(total, '单', list.length) : formatTotal(shippedTotal, '单', shippedList.length) }}
        </span>
      </template>
    </PageHero>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="仅展示当前卖家的订单；列表收货手机号已脱敏，完整地址请在订单详情核对。物流轨迹由卖家手工补录，本期不接快递公司接口。"
    />

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="reload">
        <el-tab-pane label="待发货" name="PAID">
          <el-alert
            v-if="total > list.length"
            class="mb-3"
            type="info"
            :closable="false"
            show-icon
            :title="`共 ${total} 单，当前仅显示前 ${list.length} 单`"
          />

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
                :data-testid="`seller-shipments-ship-${row.orderId || row.orderNo}`"
                @click="openShip(row)"
              >
                发货
              </el-button>
            </template>
          </UiTable>
        </el-tab-pane>

        <el-tab-pane label="已发货 · 补录轨迹" name="SHIPPED">
          <el-alert
            v-if="shippedTotal > shippedList.length"
            class="mb-3"
            type="info"
            :closable="false"
            show-icon
            :title="`共 ${shippedTotal} 单，当前仅显示前 ${shippedList.length} 单`"
          />

          <el-result
            v-if="shippedError"
            status="error"
            title="加载已发货订单失败"
            :sub-title="shippedError"
          >
            <template #extra>
              <el-button type="primary" data-testid="seller-shipments-shipped-retry" @click="fetchShipped">
                重试
              </el-button>
            </template>
          </el-result>

          <UiTable
            v-else
            :columns="shippedColumns"
            :data="shippedList"
            :loading="shippedLoading"
            row-key="orderId"
            empty-text="暂无已发货订单"
            show-actions
            actions-width="120"
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
                :data-testid="`seller-shipments-tracks-${row.orderId || row.orderNo}`"
                @click="openTracks(row)"
              >
                物流轨迹
              </el-button>
            </template>
          </UiTable>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 物流补录：上半部是已录入的时间线，下半部是新增表单 -->
    <el-drawer
      v-model="trackVisible"
      title="物流轨迹"
      size="540px"
      :close-on-click-modal="!trackSubmitting"
      data-testid="seller-shipments-tracks-drawer"
    >
      <div class="space-y-4">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="订单号">
            {{ trackForm.orderNo || trackForm.orderId }}
          </el-descriptions-item>
          <el-descriptions-item label="快递">
            {{ trackForm.expressCompanyLabel }}
            <span v-if="trackForm.trackingNo" class="ml-2 font-mono text-xs text-gray-500">
              {{ trackForm.trackingNo }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item v-if="autoConfirmText(currentCountdown)" label="自动确认">
            <span class="text-xs text-gray-600">
              预计 {{ autoConfirmText(currentCountdown) }} 后自动确认收货（买家手动确认会覆盖）
            </span>
          </el-descriptions-item>
        </el-descriptions>

        <div>
          <p class="mb-2 text-sm font-medium">已录入轨迹</p>
          <LogisticsTimeline
            :tracks="currentTracks"
            :loading="currentTracksLoading"
            :error="currentTracksError"
            :empty-message="currentTracksMessage || '已发货，物流更新中'"
            @retry="loadCurrentTracks"
          />
        </div>

        <el-divider />

        <el-form ref="trackFormRef" :model="trackForm" :rules="trackRules" label-width="88px">
          <el-form-item label="轨迹状态" prop="status">
            <el-select v-model="trackForm.status" class="w-full" data-testid="seller-tracks-status">
              <el-option
                v-for="item in trackStatusOptions"
                :key="item.code"
                :label="item.label"
                :value="item.code"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="发生时间">
            <el-date-picker
              v-model="trackForm.occurredAt"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="留空表示当前时间"
              class="w-full"
              data-testid="seller-tracks-occurred-at"
            />
          </el-form-item>
          <el-form-item label="轨迹描述" prop="description">
            <el-input
              v-model="trackForm.description"
              type="textarea"
              :rows="2"
              maxlength="255"
              show-word-limit
              placeholder="例如：快件已到达天津北辰分拨中心"
              data-testid="seller-tracks-description"
            />
          </el-form-item>
          <el-alert v-if="trackError" type="error" :closable="false" show-icon :title="trackError" />
        </el-form>
      </div>

      <template #footer>
        <div class="flex justify-end gap-2">
          <el-button :disabled="trackSubmitting" @click="trackVisible = false">关闭</el-button>
          <el-button
            type="primary"
            :loading="trackSubmitting"
            data-testid="seller-tracks-submit"
            @click="submitTrack"
          >
            补录轨迹
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 发货：快递公司与单号都必填，取消不发请求 -->
    <el-dialog v-model="shipVisible" title="填写发货信息" width="460px" destroy-on-close>
      <el-form ref="shipFormRef" :model="shipForm" :rules="shipRules" label-width="92px">
        <el-form-item label="订单号">
          <span class="text-sm text-gray-600">{{ shipForm.orderNo || shipForm.orderId }}</span>
        </el-form-item>
        <el-form-item label="快递公司" prop="expressCompany">
          <el-select
            v-model="shipForm.expressCompany"
            placeholder="请选择快递公司"
            class="w-full"
            data-testid="seller-shipments-express-company"
          >
            <el-option
              v-for="item in EXPRESS_COMPANIES"
              :key="item.code"
              :label="item.label"
              :value="item.code"
            />
          </el-select>
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
