<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { evaluationApi, isOk, orderApi, productApi, unwrapResult } from '../api'
import { currentRole } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const orderId = route.params.id

const order = ref(null)
const loading = ref(true)
const error = ref('')
const processing = ref(false)

// 订单评价（#57）：一单一评，买家在订单完成后方可提交
const evaluations = ref([])
const evalOpen = ref(false)
const evalRating = ref(5)
const evalContent = ref('')
const evalImages = ref([])
const evalAnonymous = ref(false)
const evalUploading = ref(false)
const evalSubmitting = ref(false)
const evalError = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await orderApi.detail(orderId)
    const orderData = unwrapResult(data)
    if (!orderData) {
      error.value = data?.message || '订单不存在或无权查看'
      return
    }
    order.value = orderData
    await loadEvaluations()
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadEvaluations() {
  try {
    const { data } = await evaluationApi.byOrder(orderId)
    const payload = unwrapResult(data)
    evaluations.value = Array.isArray(payload) ? payload : []
  } catch {
    // 评价加载失败不影响订单详情展示
  }
}

async function cancel() {
  if (!confirm('确定取消该订单吗？')) return
  processing.value = true
  error.value = ''
  try {
    const { data } = await orderApi.cancel(orderId)
    if (!isOk(data)) {
      error.value = data?.message || '取消失败'
      return
    }
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '取消失败'
  } finally {
    processing.value = false
  }
}

async function confirmReceive() {
  if (!confirm('确认已收到商品吗？')) return
  processing.value = true
  error.value = ''
  try {
    const { data } = await orderApi.confirm(orderId)
    if (!isOk(data)) {
      error.value = data?.message || '确认收货失败'
      return
    }
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '确认收货失败'
  } finally {
    processing.value = false
  }
}

function pay() {
  router.push({ name: 'order-pay', params: { id: orderId } })
}

const canApplyAfterSale = computed(
  () =>
    currentRole.value === 'BUYER' &&
    ['PAID', 'SHIPPED', 'COMPLETED'].includes(order.value?.status),
)

const canEvaluate = computed(
  () =>
    currentRole.value === 'BUYER' &&
    order.value?.status === 'COMPLETED' &&
    evaluations.value.length === 0,
)

function stars(n) {
  return '★'.repeat(n) + '☆'.repeat(5 - n)
}

async function uploadEvaluationImage(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return
  evalError.value = ''
  if (evalImages.value.length + files.length > 3) {
    evalError.value = '最多上传 3 张图片'
    return
  }
  evalUploading.value = true
  try {
    for (const file of files) {
      if (file.size > 5 * 1024 * 1024) {
        evalError.value = '单张图片不能超过 5MB'
        continue
      }
      const formData = new FormData()
      formData.append('file', file)
      const { data } = await productApi.uploadImage(formData)
      const uploaded = unwrapResult(data)
      if (!uploaded?.url) {
        evalError.value = data?.message || '图片上传失败'
        continue
      }
      if (evalImages.value.length < 3) {
        evalImages.value.push(uploaded.url)
      }
    }
  } catch (err) {
    evalError.value = err.response?.data?.message || '图片上传失败'
  } finally {
    evalUploading.value = false
  }
}

function removeEvaluationImage(url) {
  evalImages.value = evalImages.value.filter((item) => item !== url)
}

async function submitEvaluation() {
  evalError.value = ''
  if (!evalContent.value.trim() && evalImages.value.length === 0) {
    evalError.value = '请填写评价内容或上传图片'
    return
  }
  evalSubmitting.value = true
  try {
    const { data } = await evaluationApi.create(orderId, {
      rating: evalRating.value,
      content: evalContent.value.trim() || undefined,
      images: evalImages.value,
      anonymous: evalAnonymous.value,
    })
    if (!isOk(data)) {
      evalError.value = data?.message || '评价失败'
      return
    }
    evalOpen.value = false
    evalContent.value = ''
    evalImages.value = []
    evalAnonymous.value = false
    await loadEvaluations()
  } catch (err) {
    evalError.value = err.response?.data?.message || '评价失败'
  } finally {
    evalSubmitting.value = false
  }
}

function applyAfterSale() {
  router.push({ name: 'after-sales', query: { orderId } })
}

function statusText(s) {
  const map = {
    PENDING_PAYMENT: '待支付',
    PAID: '待发货',
    SHIPPED: '待收货',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    REFUNDED: '已退款',
  }
  return map[s] || s || '未知'
}

function statusClass(s) {
  if (s === 'COMPLETED') return 'bg-green-100 text-green-700'
  if (s === 'CANCELLED' || s === 'REFUNDED') return 'bg-gray-100 text-gray-500'
  return 'bg-blue-50 text-blue-600'
}

function formatPrice(cents) {
  return (cents / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">订单详情</h1>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

    <div v-else-if="order" class="bg-white rounded-lg border border-gray-200 p-5 space-y-5">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-sm text-gray-500">订单号：{{ order.orderNo }}</p>
          <p class="text-xs text-gray-400 mt-1">下单时间：{{ order.createdAt || '—' }}</p>
        </div>
        <span class="text-sm px-3 py-1 rounded" :class="statusClass(order.status)">
          {{ statusText(order.status) }}
        </span>
      </div>

      <div class="border-t border-gray-100 pt-4">
        <h2 class="text-sm font-medium text-gray-700 mb-2">收货地址</h2>
        <p class="text-sm text-gray-900">
          {{ order.addressSnapshot?.receiver }} {{ order.addressSnapshot?.phone }}
        </p>
        <p class="text-sm text-gray-500">{{ order.addressSnapshot?.fullAddress }}</p>
      </div>

      <div class="border-t border-gray-100 pt-4">
        <h2 class="text-sm font-medium text-gray-700 mb-2">商品信息</h2>
        <div
          v-for="item in order.items"
          :key="item.skuId"
          class="flex items-center gap-3 py-2"
        >
          <img :src="item.mainImage || '/icons.svg'" class="w-14 h-14 object-cover rounded" alt="" />
          <div class="flex-1">
            <p class="text-sm font-medium text-gray-900">{{ item.productTitle }}</p>
            <p class="text-xs text-gray-500">{{ item.specName }} × {{ item.quantity }}</p>
          </div>
          <p class="text-sm text-gray-700">¥{{ formatPrice(item.priceSnapshot) }}</p>
        </div>
      </div>

      <div v-if="evaluations.length" class="border-t border-gray-100 pt-4">
        <h2 class="text-sm font-medium text-gray-700 mb-2">我的评价</h2>
        <div v-for="ev in evaluations" :key="ev.evaluationId" class="space-y-2">
          <div class="flex items-center gap-3 text-sm">
            <span class="text-amber-500 tracking-wide">{{ stars(ev.rating) }}</span>
            <span class="text-xs text-gray-400">{{ ev.createdAt || '—' }}</span>
            <span v-if="ev.anonymous" class="text-xs text-gray-400">匿名</span>
          </div>
          <p v-if="ev.content" class="text-sm text-gray-700 whitespace-pre-line">{{ ev.content }}</p>
          <div v-if="ev.images?.length" class="flex flex-wrap gap-2">
            <img
              v-for="(img, i) in ev.images"
              :key="i"
              :src="img"
              class="w-20 h-20 object-cover rounded border border-gray-200"
              alt="晒图"
            />
          </div>
        </div>
      </div>

      <div v-else-if="canEvaluate" class="border-t border-gray-100 pt-4">
        <button
          v-if="!evalOpen"
          class="px-5 py-2 rounded-md border border-amber-500 text-amber-600 text-sm hover:bg-amber-50"
          @click="evalOpen = true"
        >
          评价晒图
        </button>
        <div v-else class="space-y-3">
          <h2 class="text-sm font-medium text-gray-700">评价本次购物</h2>
          <div class="flex items-center gap-2">
            <span class="text-sm text-gray-600">评分</span>
            <button
              v-for="n in 5"
              :key="n"
              type="button"
              class="text-xl leading-none"
              :class="n <= evalRating ? 'text-amber-500' : 'text-gray-300'"
              @click="evalRating = n"
            >
              ★
            </button>
            <span class="text-xs text-gray-400">{{ evalRating }} 分</span>
          </div>
          <textarea
            v-model="evalContent"
            rows="3"
            maxlength="500"
            placeholder="说说这次购物体验（最多 500 字，图片与文字至少填一项）"
            class="w-full px-3 py-2 text-sm border border-gray-300 rounded-md"
          ></textarea>
          <div class="flex flex-wrap items-center gap-2">
            <div v-for="img in evalImages" :key="img" class="relative">
              <img :src="img" class="w-16 h-16 object-cover rounded border border-gray-200" alt="晒图" />
              <button
                type="button"
                class="absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full bg-black/60 text-white text-xs leading-5"
                @click="removeEvaluationImage(img)"
              >
                ×
              </button>
            </div>
            <label
              v-if="evalImages.length < 3"
              class="w-16 h-16 flex items-center justify-center rounded border border-dashed border-gray-300 text-xs text-gray-400 cursor-pointer hover:border-blue-400"
            >
              {{ evalUploading ? '上传中' : '加图片' }}
              <input
                type="file"
                accept="image/*"
                multiple
                class="hidden"
                :disabled="evalUploading"
                @change="uploadEvaluationImage"
              />
            </label>
            <span class="text-xs text-gray-400">最多 3 张，单张不超过 5MB</span>
          </div>
          <label class="flex items-center gap-2 text-sm text-gray-600">
            <input v-model="evalAnonymous" type="checkbox" />
            匿名评价（商品页显示“匿名用户”）
          </label>
          <p v-if="evalError" class="text-sm text-red-600">{{ evalError }}</p>
          <div class="flex gap-2">
            <button
              class="px-5 py-2 rounded-md bg-amber-500 text-white text-sm hover:bg-amber-600 disabled:opacity-50"
              :disabled="evalSubmitting || evalUploading"
              @click="submitEvaluation"
            >
              {{ evalSubmitting ? '提交中…' : '提交评价' }}
            </button>
            <button
              class="px-5 py-2 rounded-md border border-gray-300 text-gray-600 text-sm hover:bg-gray-50"
              @click="evalOpen = false"
            >
              取消
            </button>
          </div>
        </div>
      </div>

      <div class="border-t border-gray-100 pt-4 flex items-center justify-between">
        <span class="text-sm text-gray-600">应付总额</span>
        <span class="text-xl font-bold text-red-600">¥{{ formatPrice(order.payAmount) }}</span>
      </div>

      <div class="flex justify-end gap-2 pt-2">
        <button
          v-if="order.status === 'PENDING_PAYMENT'"
          class="px-5 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700"
          @click="pay"
        >
          去支付
        </button>
        <button
          v-if="order.status === 'PENDING_PAYMENT'"
          class="px-5 py-2 rounded-md border border-gray-300 text-gray-600 text-sm hover:bg-gray-50"
          :disabled="processing"
          @click="cancel"
        >
          取消订单
        </button>
        <button
          v-if="order.status === 'SHIPPED'"
          class="px-5 py-2 rounded-md bg-green-600 text-white text-sm hover:bg-green-700"
          :disabled="processing"
          @click="confirmReceive"
        >
          确认收货
        </button>
        <button
          v-if="canApplyAfterSale"
          class="px-5 py-2 rounded-md border border-red-500 text-red-600 text-sm hover:bg-red-50"
          :disabled="processing"
          @click="applyAfterSale"
        >
          申请售后
        </button>
      </div>
    </div>
  </div>
</template>
