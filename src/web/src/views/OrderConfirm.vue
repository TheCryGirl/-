<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { addressApi, isOk, orderApi, unwrapResult } from '../api'
import { auth } from '../stores/auth'
import { resolveErrorMessage, showError, showSuccess, showWarning } from '../components/ui/message'
import { formatPrice } from '../utils/format'

const router = useRouter()
const items = ref([])
const addresses = ref([])
const selectedAddressId = ref('')
const loading = ref(true)
const loadError = ref('')
const submitting = ref(false)

const total = computed(() => items.value.reduce((sum, i) => sum + i.price * i.quantity, 0))
const totalQuantity = computed(() => items.value.reduce((sum, i) => sum + i.quantity, 0))

/** 后端同时输出 isDefault 与 defaultAddress，两个名字都兼容 */
function isDefaultAddress(addr) {
  return Boolean(addr.defaultAddress ?? addr.isDefault)
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const raw = sessionStorage.getItem('checkoutItems')
    if (!raw) {
      router.replace({ name: 'cart' })
      return
    }
    if (!auth.user?.userId) {
      loadError.value = '登录状态已失效，请重新登录'
      return
    }

    let parsed
    try {
      parsed = JSON.parse(raw)
    } catch {
      loadError.value = '结算信息已失效，请返回购物车重新选择'
      return
    }
    if (!Array.isArray(parsed) || !parsed.length) {
      loadError.value = '没有可结算的商品，请返回购物车重新选择'
      return
    }
    items.value = parsed
    selectedAddressId.value = ''

    const { data } = await addressApi.list(auth.user.userId)
    if (!isOk(data)) {
      loadError.value = data?.message || '加载收货地址失败'
      return
    }
    const payload = unwrapResult(data)
    addresses.value = Array.isArray(payload) ? payload : []
    // 默认地址自动选中；后端保证至少有一条默认，没有时再退回第一条（#51）
    const def = addresses.value.find((a) => isDefaultAddress(a))
    if (def) {
      selectedAddressId.value = def.addressId
    } else if (addresses.value.length) {
      selectedAddressId.value = addresses.value[0].addressId
    }
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载结算信息失败')
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (submitting.value) return
  if (!selectedAddressId.value) {
    showWarning('请选择收货地址')
    return
  }
  if (!items.value.length) {
    showWarning('没有可结算的商品')
    return
  }

  submitting.value = true
  try {
    const cartItemIds = items.value.map((i) => i.cartItemId)
    // 与原实现一致：每次提交尝试生成一个幂等键，失败后可安全重试
    const idempotencyKey = crypto.randomUUID()
    const { data } = await orderApi.create(
      { addressId: selectedAddressId.value, cartItemIds },
      idempotencyKey,
    )
    if (!isOk(data)) {
      showError(data?.message || '下单失败')
      return
    }
    const order = unwrapResult(data)
    if (!order?.orderId) {
      showError('下单失败：未返回订单号')
      return
    }
    sessionStorage.removeItem('checkoutItems')
    showSuccess('下单成功')
    router.push({ name: 'order-pay', params: { id: order.orderId } })
  } catch (err) {
    showError(resolveErrorMessage(err, '下单失败'))
  } finally {
    submitting.value = false
  }
}

function goProfile() {
  router.push({ name: 'profile' })
}

function goCart() {
  router.push({ name: 'cart' })
}

onMounted(load)
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h1 class="text-lg font-semibold text-gray-900">确认订单</h1>
      <span class="text-xs text-gray-400">请核对收货地址与商品明细</span>
    </div>

    <el-result v-if="loadError" status="error" title="无法结算" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="order-confirm-retry" @click="load">重试</el-button>
        <el-button data-testid="order-confirm-back-cart" @click="goCart">返回购物车</el-button>
      </template>
    </el-result>

    <div
      v-else-if="loading"
      v-loading="true"
      class="min-h-60 flex items-center justify-center text-sm text-gray-400"
    >
      正在加载结算信息…
    </div>

    <template v-else>
      <!-- 收货地址 -->
      <el-card shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">收货地址</span>
            <span v-if="addresses.length" class="text-xs text-gray-400">共 {{ addresses.length }} 个地址</span>
          </div>
        </template>

        <el-empty v-if="!addresses.length" description="暂无收货地址，请先添加">
          <el-button type="primary" data-testid="order-confirm-go-profile" @click="goProfile">
            去个人中心添加
          </el-button>
        </el-empty>

        <el-radio-group
          v-else
          v-model="selectedAddressId"
          class="w-full flex flex-col gap-2"
          data-testid="order-confirm-address-group"
        >
          <el-radio
            v-for="addr in addresses"
            :key="addr.addressId"
            :value="addr.addressId"
            class="address-radio"
            :data-testid="`order-confirm-address-${addr.addressId}`"
          >
            <div class="min-w-0 text-sm">
              <p class="font-medium text-gray-900 break-words">
                {{ addr.receiver }} {{ addr.phone }}
                <el-tag v-if="isDefaultAddress(addr)" type="primary" size="small" class="ml-2">默认</el-tag>
              </p>
              <p class="text-gray-500 break-words mt-1">
                {{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detail }}
              </p>
            </div>
          </el-radio>
        </el-radio-group>
      </el-card>

      <!-- 商品清单 -->
      <el-card shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">商品清单</span>
            <span class="text-xs text-gray-400">共 {{ items.length }} 种，{{ totalQuantity }} 件</span>
          </div>
        </template>

        <div class="divide-y divide-gray-100">
          <div
            v-for="item in items"
            :key="item.cartItemId"
            class="flex items-center gap-3 py-3 first:pt-0 last:pb-0"
            :data-testid="`order-confirm-item-${item.cartItemId}`"
          >
            <el-image
              v-if="item.mainImage"
              :src="item.mainImage"
              fit="cover"
              class="w-16 h-16 rounded border border-gray-200 shrink-0"
              :preview-src-list="[item.mainImage]"
              preview-teleported
            >
              <template #error>
                <div class="w-16 h-16 rounded bg-gray-100 text-xs text-gray-400 flex items-center justify-center">
                  无图
                </div>
              </template>
            </el-image>
            <div
              v-else
              class="w-16 h-16 rounded bg-gray-100 text-xs text-gray-400 flex items-center justify-center shrink-0"
            >
              无图
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-gray-900 break-words">{{ item.title }}</p>
              <p class="text-xs text-gray-500 mt-1">{{ item.specName || '默认规格' }} × {{ item.quantity }}</p>
            </div>
            <p class="text-sm text-red-600 font-medium whitespace-nowrap">
              ¥{{ formatPrice(item.price * item.quantity) }}
            </p>
          </div>
        </div>
      </el-card>

      <!-- 合计 -->
      <el-card shadow="never">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <span class="text-sm text-gray-600">共 {{ totalQuantity }} 件</span>
          <div class="flex flex-wrap items-center gap-4">
            <span class="text-sm text-gray-600">
              应付合计
              <span class="text-xl font-semibold text-red-600">¥{{ formatPrice(total) }}</span>
            </span>
            <el-button
              type="primary"
              :loading="submitting"
              :disabled="!selectedAddressId || !items.length"
              data-testid="order-confirm-submit"
              @click="submit"
            >
              {{ submitting ? '提交中…' : '提交订单' }}
            </el-button>
          </div>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
/* el-radio 默认是单行高度，地址需要两行并且整块可点 */
.address-radio {
  display: flex;
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 12px;
  align-items: flex-start;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.address-radio.is-checked {
  border-color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
}

/* Element 的 radio label 默认不换行，窄屏会把地址截断 */
.address-radio :deep(.el-radio__label) {
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
}

.address-radio :deep(.el-radio__input) {
  flex-shrink: 0;
}

/* Element Plus 按钮默认不显示焦点环，键盘操作时需要可见反馈 */
.el-button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
