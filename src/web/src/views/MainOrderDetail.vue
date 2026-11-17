<script setup>
/**
 * 主订单详情（#135 跨店合并结算）：
 * 聚合支付视角 + 按店铺展开子订单。子订单发货/收货/售后仍走各自的订单详情页。
 * 历史单店订单（main_order_id 为 null）不出现在这里，仍走原订单详情。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isOk, orderApi, unwrapResult } from '../api'
import { currentUserId } from '../stores/auth'
import {
  ORDER_STATUS_MAP,
  ORDER_STATUS_TAG,
  statusTag,
  statusText,
} from '../constants/status'
import { confirmAction } from '../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../components/ui/message'
import { formatPrice, formatTime } from '../utils/format'
import PageHero from '../components/store/PageHero.vue'

const route = useRoute()
const router = useRouter()
const mainOrderId = route.params.id

const mainOrder = ref(null)
const loading = ref(true)
const loadError = ref('')
const processing = ref(false)

const isMyPurchase = computed(
  () => Boolean(mainOrder.value?.buyerId) && mainOrder.value.buyerId === currentUserId.value,
)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await orderApi.mainDetail(mainOrderId)
    if (!isOk(data)) {
      loadError.value = data?.message || '订单不存在或无权查看'
      return
    }
    const data2 = unwrapResult(data)
    if (!data2) {
      loadError.value = data?.message || '订单不存在或无权查看'
      return
    }
    mainOrder.value = data2
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载订单失败')
  } finally {
    loading.value = false
  }
}

async function cancelMain() {
  const ok = await confirmAction({
    message: '确定取消该合并订单吗？所有未支付店铺子订单会一起取消并释放库存。',
    danger: true,
  })
  if (!ok) return
  if (processing.value) return

  processing.value = true
  try {
    const { data } = await orderApi.cancelMain(mainOrderId)
    if (!isOk(data)) {
      showError(data?.message || '取消失败')
      return
    }
    showSuccess('订单已取消')
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, '取消失败'))
  } finally {
    processing.value = false
  }
}

function pay() {
  router.push({ name: 'main-order-pay', params: { id: mainOrderId } })
}

function goSubOrder(sub) {
  router.push({ name: 'order-detail', params: { id: sub.orderId } })
}

function goOrders() {
  router.push({ name: 'orders' })
}

onMounted(load)
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-4">
    <PageHero
      title="合并订单详情"
      subtitle="一次支付的跨店订单，按店铺分别发货与售后"
      icon="truck"
      tone="indigo"
    >
      <template #actions>
        <el-button link type="primary" data-testid="main-detail-back" @click="goOrders">
          返回订单列表
        </el-button>
      </template>
    </PageHero>

    <el-result v-if="loadError" status="error" title="加载订单失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="main-detail-retry" @click="load">重试</el-button>
        <el-button data-testid="main-detail-back-error" @click="goOrders">返回订单列表</el-button>
      </template>
    </el-result>

    <div
      v-else-if="loading"
      v-loading="true"
      class="min-h-60 flex items-center justify-center text-sm text-gray-400"
    >
      正在加载订单详情…
    </div>

    <template v-else-if="mainOrder">
      <!-- 主订单信息 -->
      <el-card shadow="never">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div class="min-w-0">
            <p class="text-sm text-gray-500 break-all">主订单号：{{ mainOrder.mainOrderNo }}</p>
            <p class="text-xs text-gray-400 mt-1">下单时间：{{ formatTime(mainOrder.createdAt) }}</p>
          </div>
          <el-tag :type="statusTag(ORDER_STATUS_TAG, mainOrder.status)">
            {{ statusText(ORDER_STATUS_MAP, mainOrder.status) }}
          </el-tag>
        </div>
        <div class="flex items-center justify-between gap-3 mt-3">
          <span class="text-sm text-gray-600">应付合计（{{ mainOrder.subOrders?.length || 0 }} 家店铺）</span>
          <span class="text-xl font-bold text-red-600" data-testid="main-detail-pay-amount">
            ¥{{ formatPrice(mainOrder.payAmount) }}
          </span>
        </div>
      </el-card>

      <!-- 子订单按店铺展开 -->
      <el-card
        v-for="sub in mainOrder.subOrders || []"
        :key="sub.orderId"
        shadow="never"
        :data-testid="`main-detail-sub-${sub.orderId}`"
      >
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-sm font-medium break-all">{{ sub.orderNo }}</span>
            <el-tag :type="statusTag(ORDER_STATUS_TAG, sub.status)" size="small">
              {{ statusText(ORDER_STATUS_MAP, sub.status) }}
            </el-tag>
          </div>
        </template>

        <div class="divide-y divide-gray-100">
          <div
            v-for="item in sub.items || []"
            :key="item.skuId"
            class="flex items-center gap-3 py-2 first:pt-0 last:pb-0"
          >
            <div class="flex-1 min-w-0">
              <p class="text-sm text-gray-900 break-words">{{ item.productTitle }}</p>
              <p class="text-xs text-gray-500 mt-0.5">{{ item.specName }} × {{ item.quantity }}</p>
            </div>
            <p class="text-sm text-red-600 whitespace-nowrap">¥{{ formatPrice(item.subtotal) }}</p>
          </div>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-2 mt-3">
          <span class="text-sm text-gray-600">
            店铺应付 <span class="text-red-600 font-medium">¥{{ formatPrice(sub.payAmount) }}</span>
          </span>
          <el-button link type="primary" :data-testid="`main-detail-sub-open-${sub.orderId}`" @click="goSubOrder(sub)">
            查看子订单 / 发货与售后
          </el-button>
        </div>
      </el-card>

      <!-- 操作 -->
      <div v-if="isMyPurchase" class="flex flex-wrap justify-end gap-2">
        <el-button
          v-if="mainOrder.status === 'PENDING_PAYMENT'"
          type="primary"
          data-testid="main-detail-pay"
          @click="pay"
        >
          去支付
        </el-button>
        <el-button
          v-if="mainOrder.status === 'PENDING_PAYMENT'"
          :loading="processing"
          data-testid="main-detail-cancel"
          @click="cancelMain"
        >
          取消订单
        </el-button>
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