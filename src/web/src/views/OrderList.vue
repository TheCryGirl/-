<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isOk, orderApi, unwrapResult } from '../api'
import { isSeller } from '../stores/auth'
import {
  ORDER_STATUS_MAP,
  ORDER_STATUS_TAG,
  statusTag,
  statusText,
} from '../constants/status'
import { resolveErrorMessage } from '../components/ui/message'
import { formatPrice, formatTime, formatTotal } from '../utils/format'

const router = useRouter()
const role = ref('buyer')
const status = ref('')
const page = ref(1)
const size = 10
const total = ref(0)
const orders = ref([])
const loading = ref(true)
const loadError = ref('')

const FILTER_STATUSES = ['PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED']

const statusOptions = computed(() => [
  { value: '', label: '全部' },
  ...FILTER_STATUSES.map((value) => ({ value, label: statusText(ORDER_STATUS_MAP, value) })),
])

// 普通买家只看“我买到的”，卖家/管理员才显示“我卖出的”
const roleTabs = computed(() =>
  isSeller.value
    ? [
        { key: 'buyer', label: '我买到的' },
        { key: 'seller', label: '我卖出的' },
      ]
    : [{ key: 'buyer', label: '我买到的' }],
)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const params = { role: role.value, page: page.value, size }
    if (status.value) params.status = status.value
    const { data } = await orderApi.list(params)
    if (!isOk(data)) {
      loadError.value = data?.message || '加载订单失败'
      orders.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    orders.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = Array.isArray(payload) ? payload.length : payload?.total ?? orders.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载订单失败')
    orders.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function changePage(next) {
  if (next < 1 || next > totalPages.value) return
  page.value = next
  load()
}

function switchRole(next) {
  role.value = next
  page.value = 1
  load()
}

function changeStatus() {
  page.value = 1
  load()
}

function clearStatus() {
  status.value = ''
  page.value = 1
  load()
}

function goDetail(order) {
  router.push({ name: 'order-detail', params: { id: order.orderId } })
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-baseline gap-3">
        <h1 class="text-lg font-semibold text-gray-900">我的订单</h1>
        <span v-if="!loadError" class="text-xs text-gray-400">
          {{ formatTotal(total, '单') }}
        </span>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <el-radio-group
          :model-value="role"
          data-testid="order-list-role-tabs"
          @change="switchRole"
        >
          <el-radio-button v-for="r in roleTabs" :key="r.key" :value="r.key">
            {{ r.label }}
          </el-radio-button>
        </el-radio-group>
        <el-select
          v-model="status"
          placeholder="全部状态"
          style="width: 140px"
          data-testid="order-list-status"
          @change="changeStatus"
        >
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
    </div>

    <!-- 加载失败不能展示成「暂无订单」 -->
    <el-result v-if="loadError" status="error" title="加载订单失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="order-list-retry" @click="load">重试</el-button>
      </template>
    </el-result>

    <div
      v-else-if="loading"
      v-loading="true"
      class="min-h-60 flex items-center justify-center text-sm text-gray-400"
    >
      正在加载订单…
    </div>

    <el-empty
      v-else-if="!orders.length"
      :description="status ? `暂无${statusText(ORDER_STATUS_MAP, status)}订单` : '暂无订单记录'"
    >
      <el-button v-if="status" type="primary" plain data-testid="order-list-clear-status" @click="clearStatus">
        查看全部订单
      </el-button>
    </el-empty>

    <template v-else>
      <el-card shadow="never">
        <div class="divide-y divide-gray-100">
          <div
            v-for="o in orders"
            :key="o.orderId"
            class="py-3 first:pt-0 last:pb-0 -mx-2 px-2 rounded cursor-pointer hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
            tabindex="0"
            role="button"
            :aria-label="`查看订单 ${o.orderNo}`"
            :data-testid="`order-list-row-${o.orderId}`"
            @click="goDetail(o)"
            @keyup.enter="goDetail(o)"
          >
            <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
              <span class="text-sm text-gray-500 break-all">{{ o.orderNo }}</span>
              <el-tag :type="statusTag(ORDER_STATUS_TAG, o.status)" size="small">
                {{ statusText(ORDER_STATUS_MAP, o.status) }}
              </el-tag>
            </div>
            <div class="flex flex-wrap items-center gap-3">
              <div class="flex-1 min-w-0">
                <p class="text-sm text-gray-900">共 {{ o.items?.length || 1 }} 件商品</p>
                <p class="text-xs text-gray-500 mt-1">{{ formatTime(o.createdAt) }}</p>
              </div>
              <p class="text-sm text-red-600 font-medium whitespace-nowrap">
                ¥{{ formatPrice(o.payAmount) }}
              </p>
            </div>
          </div>
        </div>
      </el-card>

      <div v-if="totalPages > 1" class="flex justify-center">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          :pager-count="5"
          data-testid="order-list-pagination"
          @current-change="changePage"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
/* Element Plus 按钮默认不显示焦点环，键盘操作时需要可见反馈 */
.el-button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
