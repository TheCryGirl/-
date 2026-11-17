<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { addressApi, isOk, orderApi, unwrapResult } from '../api'
import { auth } from '../stores/auth'
import { resolveErrorMessage, showError, showSuccess, showWarning } from '../components/ui/message'
import { formatPrice } from '../utils/format'
import { buildSummary, summaryRows } from '../utils/pricing'
import PageHero from '../components/store/PageHero.vue'
import { createIdempotencyKey } from '../utils/idempotency'

const router = useRouter()
const items = ref([])
const addresses = ref([])
const selectedAddressId = ref('')
const loading = ref(true)
const loadError = ref('')
const submitting = ref(false)

const total = computed(() => items.value.reduce((sum, i) => sum + i.price * i.quantity, 0))
const totalQuantity = computed(() => items.value.reduce((sum, i) => sum + i.quantity, 0))

/**
 * 跨店结算预览（#135）：按店铺分块展示，金额一律以预览接口为准，前端不自行计算应付。
 * 预览失败时退回本地商品清单展示，但提交仍走服务端下单校验。
 */
const preview = ref(null)
const previewLoading = ref(false)
const previewError = ref('')

const previewShops = computed(() => preview.value?.shops || [])
const isMultiShop = computed(() => previewShops.value.length > 1)
/** #136 计价链：商品金额 / 运费 / 优惠 / 应付合计，全部取后端预览结果 */
const summary = computed(() => buildSummary(preview.value, total.value))
const summaryLines = computed(() => summaryRows(summary.value))
let previewReady = false

async function loadPreview() {
  if (!items.value.length) return
  if (!addresses.value.length) {
    // 运费按收货地区算，没有地址就没有可用的运费；页面已提示先添加地址
    preview.value = null
    previewError.value = ''
    return
  }
  previewLoading.value = true
  previewError.value = ''
  try {
    const { data } = await orderApi.preview({
      cartItemIds: items.value.map((i) => i.cartItemId),
      addressId: selectedAddressId.value || undefined,
    })
    if (!isOk(data)) {
      previewError.value = data?.message || '结算预览加载失败'
      preview.value = null
      return
    }
    preview.value = unwrapResult(data)
  } catch (err) {
    previewError.value = resolveErrorMessage(err, '结算预览加载失败')
    preview.value = null
  } finally {
    previewLoading.value = false
  }
}

/** 切换收货地址后运费会变，重新调预览接口（前端不自己算） */
watch(selectedAddressId, (value, previous) => {
  if (!previewReady || !value || value === previous) return
  loadPreview()
})

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
    await loadPreview()
    previewReady = true
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
    const idempotencyKey = createIdempotencyKey()
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
    // 跨店结算（#135）：跳主订单支付页，一次支付覆盖全部子订单
    if (order.mainOrderId) {
      router.push({ name: 'main-order-pay', params: { id: order.mainOrderId } })
    } else {
      router.push({ name: 'order-pay', params: { id: order.orderId } })
    }
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
    <PageHero
      title="确认订单"
      subtitle="请核对收货地址与商品明细，提交后进入支付"
      icon="tag"
      tone="teal"
    />

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

      <!-- 商品清单：跨店时按店铺分块（#135），单店保持原清单 -->
      <template v-if="isMultiShop">
        <el-card
          v-for="shop in previewShops"
          :key="shop.sellerId"
          shadow="never"
          :data-testid="`order-confirm-shop-${shop.sellerId}`"
        >
          <template #header>
            <div class="flex flex-wrap items-center justify-between gap-2">
              <span class="text-base font-medium">{{ shop.shopName || shop.sellerId }}</span>
              <span class="text-xs text-gray-400">
                店铺小计 ¥{{ formatPrice(shop.totalAmount) }}
                <template v-if="shop.freightAmount"> · 运费 ¥{{ formatPrice(shop.freightAmount) }}</template>
                <template v-if="shop.discountAmount"> · 优惠 -¥{{ formatPrice(shop.discountAmount) }}</template>
              </span>
            </div>
          </template>
          <div class="divide-y divide-gray-100">
            <div
              v-for="item in shop.items"
              :key="item.cartItemId"
              class="flex items-center gap-3 py-3 first:pt-0 last:pb-0"
            >
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium text-gray-900 break-words">{{ item.productTitle }}</p>
                <p class="text-xs text-gray-500 mt-1">{{ item.specName || '默认规格' }} × {{ item.quantity }}</p>
              </div>
              <p class="text-sm text-red-600 font-medium whitespace-nowrap">¥{{ formatPrice(item.subtotal) }}</p>
            </div>
          </div>
        </el-card>
      </template>

      <el-card v-else shadow="never">
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

      <!-- 合计：#136 四行（商品金额 / 运费 / 优惠 / 应付合计），金额以后端预览为准 -->
      <el-card shadow="never">
        <div class="space-y-2">
          <div
            v-for="line in summaryLines"
            :key="line.key"
            class="flex items-center justify-between text-sm"
            :class="{ 'pt-2 border-t border-gray-100': line.key === 'pay' }"
          >
            <span class="text-gray-600">{{ line.label }}</span>
            <span
              :class="[
                line.key === 'pay' ? 'text-xl font-semibold text-red-600' : 'text-gray-900',
                line.key === 'discount' && line.amount ? 'text-emerald-600' : '',
              ]"
              :data-testid="`order-confirm-${line.key}`"
            >
              {{ line.key === 'discount' && line.amount ? '-' : '' }}¥{{ formatPrice(line.amount) }}
            </span>
          </div>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3 mt-3">
          <span class="text-sm text-gray-600" data-testid="order-confirm-summary">
            <template v-if="isMultiShop">共 {{ previewShops.length }} 家店铺，</template>{{ totalQuantity }} 件
            <template v-if="preview?.region"> · 收货地区 {{ preview.region }}</template>
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
        <p v-if="previewError" class="text-xs text-amber-600 mt-2" data-testid="order-confirm-preview-error">
          分店预览加载失败（{{ previewError }}），应付金额以提交订单时服务端计算为准
        </p>
        <p v-else-if="previewLoading" class="text-xs text-gray-400 mt-2">正在按收货地址重新计算运费…</p>
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

