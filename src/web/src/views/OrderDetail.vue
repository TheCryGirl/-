<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { evaluationApi, isOk, orderApi, productApi, unwrapResult } from '../api'
import { currentRole } from '../stores/auth'
import {
  ORDER_STATUS_MAP,
  ORDER_STATUS_TAG,
  statusTag,
  statusText,
} from '../constants/status'
import { confirmAction } from '../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../components/ui/message'
import { formatPrice, formatTime } from '../utils/format'

const route = useRoute()
const router = useRouter()
const orderId = route.params.id

const order = ref(null)
const loading = ref(true)
const loadError = ref('')
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
const evalFileInput = ref(null)

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

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await orderApi.detail(orderId)
    if (!isOk(data)) {
      loadError.value = data?.message || '订单不存在或无权查看'
      return
    }
    const orderData = unwrapResult(data)
    if (!orderData) {
      loadError.value = data?.message || '订单不存在或无权查看'
      return
    }
    order.value = orderData
    await loadEvaluations()
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载订单失败')
  } finally {
    loading.value = false
  }
}

async function loadEvaluations() {
  try {
    const { data } = await evaluationApi.byOrder(orderId)
    if (!isOk(data)) {
      evaluations.value = []
      return
    }
    const payload = unwrapResult(data)
    evaluations.value = Array.isArray(payload) ? payload : []
  } catch {
    // 评价加载失败不影响订单详情展示
    evaluations.value = []
  }
}

async function cancel() {
  const ok = await confirmAction({
    message: '确定取消该订单吗？取消后库存会释放。',
    danger: true,
  })
  if (!ok) return
  if (processing.value) return

  processing.value = true
  try {
    const { data } = await orderApi.cancel(orderId)
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

async function confirmReceive() {
  const ok = await confirmAction({
    message: '确认已经收到商品吗？确认后订单将完成。',
  })
  if (!ok) return
  if (processing.value) return

  processing.value = true
  try {
    const { data } = await orderApi.confirm(orderId)
    if (!isOk(data)) {
      showError(data?.message || '确认收货失败')
      return
    }
    showSuccess('已确认收货')
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, '确认收货失败'))
  } finally {
    processing.value = false
  }
}

function pay() {
  router.push({ name: 'order-pay', params: { id: orderId } })
}

function applyAfterSale() {
  router.push({ name: 'after-sales', query: { orderId } })
}

function goOrders() {
  router.push({ name: 'orders' })
}

function openEvaluation() {
  if (!canEvaluate.value) return
  evalError.value = ''
  evalOpen.value = true
}

function triggerEvalUpload() {
  evalFileInput.value?.click()
}

async function handleEvalFiles(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length || evalUploading.value) return
  if (evalImages.value.length + files.length > 3) {
    evalError.value = '最多上传 3 张图片'
    return
  }

  evalError.value = ''
  evalUploading.value = true
  try {
    for (const file of files) {
      if (evalImages.value.length >= 3) {
        evalError.value = '最多上传 3 张图片'
        break
      }
      if (file.size > 5 * 1024 * 1024) {
        evalError.value = '单张图片不能超过 5MB'
        continue
      }
      // 与原有上传一致：一次选择每个文件只发一次请求，字段名固定为 file
      const formData = new FormData()
      formData.append('file', file)
      const { data } = await productApi.uploadImage(formData)
      if (!isOk(data)) {
        evalError.value = data?.message || '图片上传失败'
        continue
      }
      const uploaded = unwrapResult(data)
      if (!uploaded?.url) {
        evalError.value = '图片上传失败，未返回图片地址'
        continue
      }
      if (evalImages.value.length < 3) {
        evalImages.value.push(uploaded.url)
      }
    }
  } catch (err) {
    evalError.value = resolveErrorMessage(err, '图片上传失败')
  } finally {
    evalUploading.value = false
  }
}

function removeEvaluationImage(url) {
  evalImages.value = evalImages.value.filter((item) => item !== url)
}

async function submitEvaluation() {
  if (evalSubmitting.value) return
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
    showSuccess('评价已提交')
    await loadEvaluations()
  } catch (err) {
    evalError.value = resolveErrorMessage(err, '评价失败')
  } finally {
    evalSubmitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h1 class="text-lg font-semibold text-gray-900">订单详情</h1>
      <el-button link type="primary" data-testid="order-detail-back" @click="goOrders">
        返回订单列表
      </el-button>
    </div>

    <el-result v-if="loadError" status="error" title="加载订单失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="order-detail-retry" @click="load">重试</el-button>
        <el-button data-testid="order-detail-back-error" @click="goOrders">返回订单列表</el-button>
      </template>
    </el-result>

    <div
      v-else-if="loading"
      v-loading="true"
      class="min-h-60 flex items-center justify-center text-sm text-gray-400"
    >
      正在加载订单详情…
    </div>

    <template v-else-if="order">
      <!-- 订单信息 -->
      <el-card shadow="never">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div class="min-w-0">
            <p class="text-sm text-gray-500 break-all">订单号：{{ order.orderNo }}</p>
            <p class="text-xs text-gray-400 mt-1">下单时间：{{ formatTime(order.createdAt) }}</p>
          </div>
          <el-tag :type="statusTag(ORDER_STATUS_TAG, order.status)">
            {{ statusText(ORDER_STATUS_MAP, order.status) }}
          </el-tag>
        </div>
      </el-card>

      <!-- 收货地址 -->
      <el-card shadow="never">
        <template #header>
          <span class="text-base font-medium">收货地址</span>
        </template>
        <p class="text-sm text-gray-900 break-words">
          {{ order.addressSnapshot?.receiver || '-' }} {{ order.addressSnapshot?.phone || '' }}
        </p>
        <p class="text-sm text-gray-500 mt-1 break-words">
          {{ order.addressSnapshot?.fullAddress || '-' }}
        </p>
      </el-card>

      <!-- 商品信息 -->
      <el-card shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">商品信息</span>
            <span class="text-xs text-gray-400">共 {{ order.items?.length || 0 }} 种</span>
          </div>
        </template>

        <el-empty v-if="!order.items?.length" description="暂无商品" :image-size="72" />
        <div v-else class="divide-y divide-gray-100">
          <div
            v-for="item in order.items"
            :key="item.skuId"
            class="flex items-center gap-3 py-3 first:pt-0 last:pb-0"
            :data-testid="`order-detail-item-${item.skuId}`"
          >
            <el-image
              v-if="item.mainImage"
              :src="item.mainImage"
              fit="cover"
              class="w-14 h-14 rounded border border-gray-200 shrink-0"
              :preview-src-list="[item.mainImage]"
              preview-teleported
            >
              <template #error>
                <div class="w-14 h-14 rounded bg-gray-100 text-xs text-gray-400 flex items-center justify-center">
                  无图
                </div>
              </template>
            </el-image>
            <div
              v-else
              class="w-14 h-14 rounded bg-gray-100 text-xs text-gray-400 flex items-center justify-center shrink-0"
            >
              无图
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-gray-900 break-words">{{ item.productTitle }}</p>
              <p class="text-xs text-gray-500 mt-1">{{ item.specName || '默认规格' }} × {{ item.quantity }}</p>
            </div>
            <p class="text-sm text-gray-700 whitespace-nowrap">¥{{ formatPrice(item.priceSnapshot) }}</p>
          </div>
        </div>
      </el-card>

      <!-- 我的评价 -->
      <el-card v-if="evaluations.length" shadow="never">
        <template #header>
          <span class="text-base font-medium">我的评价</span>
        </template>
        <div class="space-y-4">
          <div v-for="ev in evaluations" :key="ev.evaluationId" class="space-y-2">
            <div class="flex flex-wrap items-center gap-3 text-sm">
              <el-rate :model-value="ev.rating" disabled size="small" />
              <span class="text-xs text-gray-400">{{ formatTime(ev.createdAt) }}</span>
              <el-tag v-if="ev.anonymous" size="small" type="info">匿名</el-tag>
            </div>
            <p v-if="ev.content" class="text-sm text-gray-700 whitespace-pre-line break-words">{{ ev.content }}</p>
            <div v-if="ev.images?.length" class="flex flex-wrap gap-2">
              <el-image
                v-for="(img, index) in ev.images"
                :key="index"
                :src="img"
                fit="cover"
                class="w-20 h-20 rounded border border-gray-200"
                :preview-src-list="ev.images"
                :initial-index="index"
                preview-teleported
              >
                <template #error>
                  <div class="w-20 h-20 rounded bg-gray-100 text-xs text-gray-400 flex items-center justify-center">
                    加载失败
                  </div>
                </template>
              </el-image>
            </div>
          </div>
        </div>
      </el-card>

      <el-card v-else-if="canEvaluate" shadow="never">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <span class="text-sm text-gray-600">本次购物已完成，可以发表评价。</span>
          <el-button type="warning" plain data-testid="order-detail-evaluate" @click="openEvaluation">
            评价晒图
          </el-button>
        </div>
      </el-card>

      <!-- 金额 -->
      <el-card shadow="never">
        <div class="flex items-center justify-between gap-3">
          <span class="text-sm text-gray-600">应付总额</span>
          <span class="text-xl font-bold text-red-600">¥{{ formatPrice(order.payAmount) }}</span>
        </div>
      </el-card>

      <!-- 操作 -->
      <div class="flex flex-wrap justify-end gap-2">
        <el-button
          v-if="order.status === 'PENDING_PAYMENT'"
          type="primary"
          data-testid="order-detail-pay"
          @click="pay"
        >
          去支付
        </el-button>
        <el-button
          v-if="order.status === 'PENDING_PAYMENT'"
          :loading="processing"
          data-testid="order-detail-cancel"
          @click="cancel"
        >
          取消订单
        </el-button>
        <el-button
          v-if="order.status === 'SHIPPED'"
          type="success"
          :loading="processing"
          data-testid="order-detail-confirm-receive"
          @click="confirmReceive"
        >
          确认收货
        </el-button>
        <el-button
          v-if="canApplyAfterSale"
          type="danger"
          plain
          :disabled="processing"
          data-testid="order-detail-after-sale"
          @click="applyAfterSale"
        >
          申请售后
        </el-button>
      </div>

      <!-- 评价弹窗：取消不提交，关闭后保留草稿 -->
      <el-dialog
        v-model="evalOpen"
        title="评价本次购物"
        width="560px"
        :close-on-click-modal="!evalSubmitting"
      >
        <div class="space-y-4">
          <div class="flex items-center gap-3">
            <span class="text-sm text-gray-600">评分</span>
            <el-rate v-model="evalRating" data-testid="order-detail-rate" />
            <span class="text-xs text-gray-400">{{ evalRating }} 分</span>
          </div>

          <el-input
            v-model="evalContent"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="说说这次购物体验（最多 500 字，图片与文字至少填一项）"
            data-testid="order-detail-eval-content"
          />

          <div class="flex flex-wrap items-start gap-3">
            <div v-for="img in evalImages" :key="img" class="flex flex-col items-center gap-1">
              <el-image
                :src="img"
                fit="cover"
                class="w-16 h-16 rounded border border-gray-200"
                :preview-src-list="evalImages"
                preview-teleported
              >
                <template #error>
                  <div class="w-16 h-16 rounded bg-gray-100 text-xs text-gray-400 flex items-center justify-center">
                    加载失败
                  </div>
                </template>
              </el-image>
              <el-button link type="danger" size="small" @click="removeEvaluationImage(img)">移除</el-button>
            </div>

            <div v-if="evalImages.length < 3" class="flex flex-col items-start gap-1">
              <el-button
                :loading="evalUploading"
                data-testid="order-detail-eval-upload"
                @click="triggerEvalUpload"
              >
                {{ evalUploading ? '上传中…' : '加图片' }}
              </el-button>
              <span class="text-xs text-gray-400">最多 3 张，单张不超过 5MB</span>
            </div>
            <input
              ref="evalFileInput"
              type="file"
              accept="image/*"
              multiple
              class="hidden"
              :disabled="evalUploading"
              data-testid="order-detail-eval-file"
              @change="handleEvalFiles"
            />
          </div>

          <el-checkbox v-model="evalAnonymous">匿名评价（商品页显示“匿名用户”）</el-checkbox>

          <el-alert v-if="evalError" type="error" :closable="false" show-icon :title="evalError" />
        </div>

        <template #footer>
          <el-button :disabled="evalSubmitting" data-testid="order-detail-eval-cancel" @click="evalOpen = false">
            取消
          </el-button>
          <el-button
            type="warning"
            :loading="evalSubmitting"
            :disabled="evalUploading"
            data-testid="order-detail-eval-submit"
            @click="submitEvaluation"
          >
            提交评价
          </el-button>
        </template>
      </el-dialog>
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
