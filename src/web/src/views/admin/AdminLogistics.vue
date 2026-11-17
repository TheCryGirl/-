<script setup>
import PageHero from '../../components/store/PageHero.vue'
/**
 * 管理端物流补录（#138）。
 *
 * issue 要求轨迹支持「手工录入 + 管理端补录」，而管理端此前没有订单列表页，
 * 因此这里不新造一个订单检索中心，只做一个按订单号精确定位的工具：
 * 管理员从客诉工单里拿到订单号，在这里查物流、补轨迹。
 *
 * 权限：后端校验 X-User-Role=ADMIN，管理员可操作任意订单。
 */
import { computed, reactive, ref } from 'vue'
import { isOk, orderApi, unwrapResult } from '../../api'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import LogisticsTimeline from '../../components/store/LogisticsTimeline.vue'
import {
  EXPRESS_COMPANY_MAP,
  LOGISTICS_STATUS_MAP,
  LOGISTICS_STATUS_OPTIONS,
  ORDER_STATUS_MAP,
  statusText,
} from '../../constants/status'
import { autoConfirmText } from '../../utils/logistics'
import { formatTime } from '../../utils/format'

/* ---------- 1. 状态 ---------- */
const orderIdInput = ref('')
const searchedId = ref('')
const detail = ref(null)
const logistics = ref(null)
const loading = ref(false)
const loadError = ref('')

const trackFormRef = ref(null)
const trackForm = reactive({ status: 'IN_TRANSIT', description: '', occurredAt: '' })
const trackRules = {
  status: [{ required: true, message: '请选择轨迹状态', trigger: 'change' }],
  description: [{ required: true, message: '请输入轨迹描述', trigger: 'blur' }],
}
const submitting = ref(false)
const submitError = ref('')
const formError = ref('')

/* ---------- 2. 计算属性 ---------- */
const trackStatusOptions = computed(() =>
  LOGISTICS_STATUS_OPTIONS.map((code) => ({ code, label: LOGISTICS_STATUS_MAP[code] })),
)

const isEmpty = computed(() => !loading.value && !loadError.value && !detail.value)

const expressLabel = computed(
  () => EXPRESS_COMPANY_MAP[detail.value?.expressCompany] || detail.value?.expressCompanyLabel || '—',
)

/* ---------- 3. 方法 ---------- */
async function search() {
  const id = orderIdInput.value.trim()
  formError.value = ''
  if (!id) {
    formError.value = '请输入订单号'
    return
  }
  loading.value = true
  loadError.value = ''
  detail.value = null
  logistics.value = null
  searchedId.value = id
  try {
    const [detailRes, logisticsRes] = await Promise.all([
      orderApi.detail(id),
      orderApi.logistics(id),
    ])
    if (!isOk(detailRes.data)) {
      loadError.value = detailRes.data?.message || '订单不存在或无权查看'
      return
    }
    detail.value = unwrapResult(detailRes.data)
    // 物流接口失败不掩盖订单本身：订单查到了就先展示，物流区块单独报错
    if (!isOk(logisticsRes.data)) {
      loadError.value = ''
      logistics.value = { tracks: [], message: '物流信息加载失败' }
      return
    }
    logistics.value = unwrapResult(logisticsRes.data)
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '查询失败')
  } finally {
    loading.value = false
  }
}

async function reloadLogistics() {
  if (!searchedId.value) return
  try {
    const { data } = await orderApi.logistics(searchedId.value)
    if (!isOk(data)) {
      showError(data?.message || '物流信息加载失败')
      return
    }
    logistics.value = unwrapResult(data)
  } catch (err) {
    showError(resolveErrorMessage(err, '物流信息加载失败'))
  }
}

async function submitTrack() {
  if (submitting.value) return
  try {
    await trackFormRef.value?.validate()
  } catch {
    return
  }

  submitError.value = ''
  submitting.value = true
  try {
    const payload = { status: trackForm.status, description: trackForm.description.trim() }
    if (trackForm.occurredAt) payload.occurredAt = trackForm.occurredAt
    const { data } = await orderApi.appendLogisticsTrack(searchedId.value, payload)
    if (!isOk(data)) {
      submitError.value = data?.message || '补录失败'
      return
    }
    showSuccess('轨迹已补录')
    trackForm.description = ''
    await reloadLogistics()
  } catch (err) {
    submitError.value = resolveErrorMessage(err, '补录失败')
  } finally {
    submitting.value = false
  }
}

function reset() {
  orderIdInput.value = ''
  searchedId.value = ''
  detail.value = null
  logistics.value = null
  loadError.value = ''
  formError.value = ''
}
</script>

<template>
  <div class="space-y-4">
    <PageHero
      title="物流管理"
      subtitle="按订单号查物流、补录轨迹；轨迹本期不接快递公司接口"
      icon="truck"
      tone="indigo"
    />

    <el-card shadow="never">
      <template #header>
        <span class="text-base font-medium">查询订单</span>
      </template>

      <div class="flex flex-wrap items-center gap-2">
        <el-input
          v-model="orderIdInput"
          placeholder="输入订单 ID，例如 O20261117..."
          clearable
          style="width: 320px"
          data-testid="admin-logistics-order-id"
          @keyup.enter="search"
        />
        <el-button
          type="primary"
          :loading="loading"
          data-testid="admin-logistics-search"
          @click="search"
        >
          查询
        </el-button>
        <el-button v-if="detail || loadError" @click="reset">清空</el-button>
      </div>
      <p v-if="formError" class="mt-2 text-xs text-red-600">{{ formError }}</p>
      <p class="mt-2 text-xs text-gray-400">
        管理端没有订单检索列表，这里按订单号精确定位；客服工单里的订单号可直接粘贴。
      </p>
    </el-card>

    <el-card v-if="loadError" shadow="never">
      <el-result status="error" title="查询失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" data-testid="admin-logistics-retry" @click="search">重试</el-button>
        </template>
      </el-result>
    </el-card>

    <el-card v-else-if="loading" shadow="never">
      <div class="py-8 text-center text-sm text-gray-400">正在查询订单…</div>
    </el-card>

    <el-card v-else-if="isEmpty" shadow="never">
      <el-empty description="输入订单号后查询物流信息" :image-size="80" />
    </el-card>

    <template v-else-if="detail">
      <el-card shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">订单信息</span>
            <el-tag size="small">{{ statusText(ORDER_STATUS_MAP, detail.status) }}</el-tag>
          </div>
        </template>

        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="订单 ID">{{ detail.orderId }}</el-descriptions-item>
          <el-descriptions-item label="买家">{{ detail.buyerId }}</el-descriptions-item>
          <el-descriptions-item label="卖家">{{ detail.sellerId }}</el-descriptions-item>
          <el-descriptions-item label="快递公司">{{ expressLabel }}</el-descriptions-item>
          <el-descriptions-item label="物流单号">{{ detail.trackingNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发货时间">
            {{ detail.shippedAt ? formatTime(detail.shippedAt) : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="自动确认">
            <span v-if="autoConfirmText(detail.autoConfirmRemainingMinutes)">
              预计 {{ autoConfirmText(detail.autoConfirmRemainingMinutes) }} 后
            </span>
            <span v-else class="text-gray-400">—</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">物流轨迹</span>
            <el-button link type="primary" size="small" @click="reloadLogistics">刷新</el-button>
          </div>
        </template>

        <el-alert
          v-if="!logistics?.shipped"
          class="mb-3"
          type="warning"
          :closable="false"
          show-icon
          title="订单尚未发货，无法补录轨迹。请先由卖家发货。"
        />

        <LogisticsTimeline
          :tracks="logistics?.tracks || []"
          :empty-message="logistics?.message || '暂无物流信息'"
          @retry="reloadLogistics"
        />
      </el-card>

      <el-card v-if="logistics?.shipped" shadow="never">
        <template #header>
          <span class="text-base font-medium">补录一条轨迹</span>
        </template>

        <el-form ref="trackFormRef" :model="trackForm" :rules="trackRules" label-width="92px">
          <el-form-item label="轨迹状态" prop="status">
            <el-select v-model="trackForm.status" style="width: 220px" data-testid="admin-tracks-status">
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
              style="width: 220px"
              data-testid="admin-tracks-occurred-at"
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
              data-testid="admin-tracks-description"
            />
          </el-form-item>
          <el-alert v-if="submitError" type="error" :closable="false" show-icon :title="submitError" />
          <div class="mt-3">
            <el-button
              type="primary"
              :loading="submitting"
              data-testid="admin-tracks-submit"
              @click="submitTrack"
            >
              补录
            </el-button>
          </div>
        </el-form>
      </el-card>
    </template>
  </div>
</template>
