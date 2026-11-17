<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isOk, orderApi, unwrapResult } from '../api'
import { isSeller } from '../stores/auth'
import PageHero from '../components/store/PageHero.vue'
import {
  ORDER_STATUS_MAP,
  ORDER_STATUS_TAG,
  statusTag,
  statusText,
} from '../constants/status'
import { resolveErrorMessage } from '../components/ui/message'
import { formatPrice, formatTime, formatTotal } from '../utils/format'
import { groupOrderRows } from '../utils/orderGroup'

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

/**
 * 跨店合并结算（#135）：买家视角把同一主订单的子订单聚合成一行，可展开查看。
 * 历史单店订单（mainOrderId 为 null）保持原样逐单展示。卖家视角不聚合（按店发货）。
 * 聚合逻辑在 utils/orderGroup.js，node --test 直接覆盖。
 */
const expandedMains = ref(new Set())

const displayRows = computed(() => groupOrderRows(orders.value, role.value))

function toggleMain(row) {
  const next = new Set(expandedMains.value)
  if (next.has(row.mainOrderId)) next.delete(row.mainOrderId)
  else next.add(row.mainOrderId)
  expandedMains.value = next
}

function goMain(row) {
  router.push({ name: 'main-order-detail', params: { id: row.mainOrderId } })
}

function payMain(row) {
  router.push({ name: 'main-order-pay', params: { id: row.mainOrderId } })
}

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
    <PageHero
      title="我的订单"
      subtitle="买家订单与卖家订单分开查看，状态变化实时同步"
      icon="truck"
      tone="indigo"
      image="/media/bg/page-band.jpg"
    >
      <template #meta>
        <span v-if="!loadError" class="text-xs text-[var(--jd-ink-4)]">{{ formatTotal(total, '单') }}</span>
      </template>
      <template #actions>
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
      </template>
    </PageHero>

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
          <template v-for="row in displayRows" :key="row.kind === 'main' ? row.mainOrderId : row.order.orderId">
            <!-- 跨店主订单聚合行（#135）：可展开看子订单 -->
            <div v-if="row.kind === 'main'" class="py-3 first:pt-0 last:pb-0">
              <div
                class="-mx-2 px-2 rounded cursor-pointer hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                tabindex="0"
                role="button"
                :aria-label="`查看合并订单 ${row.mainOrderNo}`"
                :data-testid="`order-list-main-${row.mainOrderId}`"
                @click="goMain(row)"
                @keyup.enter="goMain(row)"
              >
                <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
                  <span class="text-sm text-gray-500 break-all">
                    {{ row.mainOrderNo }}
                    <el-tag size="small" type="warning" class="ml-2">跨店合并</el-tag>
                  </span>
                  <el-tag :type="statusTag(ORDER_STATUS_TAG, row.status)" size="small">
                    {{ statusText(ORDER_STATUS_MAP, row.status) }}
                  </el-tag>
                </div>
                <div class="flex flex-wrap items-center gap-3">
                  <div class="flex-1 min-w-0">
                    <p class="text-sm text-gray-900">{{ row.subs.length }} 家店铺，共 {{ row.itemCount }} 件商品</p>
                    <p class="text-xs text-gray-500 mt-1">{{ formatTime(row.createdAt) }}</p>
                  </div>
                  <p class="text-sm text-red-600 font-medium whitespace-nowrap">¥{{ formatPrice(row.payAmount) }}</p>
                </div>
              </div>
              <div class="flex flex-wrap items-center justify-end gap-2 mt-2">
                <el-button
                  v-if="row.status === 'PENDING_PAYMENT'"
                  type="primary"
                  size="small"
                  :data-testid="`order-list-main-pay-${row.mainOrderId}`"
                  @click="payMain(row)"
                >
                  去支付
                </el-button>
                <el-button
                  size="small"
                  :data-testid="`order-list-main-toggle-${row.mainOrderId}`"
                  @click="toggleMain(row)"
                >
                  {{ expandedMains.has(row.mainOrderId) ? '收起子订单' : '展开子订单' }}
                </el-button>
              </div>
              <div v-if="expandedMains.has(row.mainOrderId)" class="mt-2 space-y-2">
                <div
                  v-for="sub in row.subs"
                  :key="sub.orderId"
                  class="flex flex-wrap items-center justify-between gap-2 rounded border border-gray-100 px-3 py-2 cursor-pointer hover:bg-gray-50"
                  tabindex="0"
                  role="button"
                  :data-testid="`order-list-sub-${sub.orderId}`"
                  @click="goDetail(sub)"
                  @keyup.enter="goDetail(sub)"
                >
                  <div class="min-w-0">
                    <p class="text-xs text-gray-500 break-all">{{ sub.orderNo }}</p>
                    <p class="text-xs text-gray-400 mt-0.5">共 {{ sub.items?.length || 0 }} 件商品</p>
                  </div>
                  <div class="flex items-center gap-2">
                    <el-tag :type="statusTag(ORDER_STATUS_TAG, sub.status)" size="small">
                      {{ statusText(ORDER_STATUS_MAP, sub.status) }}
                    </el-tag>
                    <span class="text-sm text-red-600">¥{{ formatPrice(sub.payAmount) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 单店订单行（含历史 main_order_id 为 null 的订单） -->
            <div
              v-else
              class="py-3 first:pt-0 last:pb-0 -mx-2 px-2 rounded cursor-pointer hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              tabindex="0"
              role="button"
              :aria-label="`查看订单 ${row.order.orderNo}`"
              :data-testid="`order-list-row-${row.order.orderId}`"
              @click="goDetail(row.order)"
              @keyup.enter="goDetail(row.order)"
            >
              <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
                <span class="text-sm text-gray-500 break-all">{{ row.order.orderNo }}</span>
                <el-tag :type="statusTag(ORDER_STATUS_TAG, row.order.status)" size="small">
                  {{ statusText(ORDER_STATUS_MAP, row.order.status) }}
                </el-tag>
              </div>
              <div class="flex flex-wrap items-center gap-3">
                <div class="flex-1 min-w-0">
                  <p class="text-sm text-gray-900">共 {{ row.order.items?.length || 1 }} 件商品</p>
                  <p class="text-xs text-gray-500 mt-1">{{ formatTime(row.order.createdAt) }}</p>
                </div>
                <p class="text-sm text-red-600 font-medium whitespace-nowrap">
                  ¥{{ formatPrice(row.order.payAmount) }}
                </p>
              </div>
            </div>
          </template>
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

