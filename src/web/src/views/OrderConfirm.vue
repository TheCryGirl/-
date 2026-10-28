<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { addressApi, orderApi, unwrapResult } from '../api'
import { auth } from '../stores/auth'

const router = useRouter()
const items = ref([])
const addresses = ref([])
const selectedAddressId = ref('')
const loading = ref(false)
const error = ref('')

const total = computed(() => items.value.reduce((sum, i) => sum + i.price * i.quantity, 0))

async function load() {
  const raw = sessionStorage.getItem('checkoutItems')
  if (!raw) {
    router.replace({ name: 'cart' })
    return
  }
  try {
    items.value = JSON.parse(raw)
    if (!auth.user?.userId) {
      error.value = '登录状态已失效，请重新登录'
      return
    }
    const { data } = await addressApi.list(auth.user.userId)
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
    error.value = err.response?.data?.message || err.message || '加载失败'
  }
}

async function submit() {
  if (!selectedAddressId.value) {
    error.value = '请选择收货地址'
    return
  }
  if (!items.value.length) {
    error.value = '没有可结算的商品'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const cartItemIds = items.value.map((i) => i.cartItemId)
    const idempotencyKey = crypto.randomUUID()
    const { data } = await orderApi.create(
      { addressId: selectedAddressId.value, cartItemIds },
      idempotencyKey,
    )
    const order = unwrapResult(data)
    if (!order) {
      error.value = data?.message || '下单失败'
      return
    }
    sessionStorage.removeItem('checkoutItems')
    router.push({ name: 'order-pay', params: { id: order.orderId } })
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '下单失败'
  } finally {
    loading.value = false
  }
}

function formatPrice(cents) {
  return (cents / 100).toFixed(2)
}

/** 后端同时输出 isDefault 与 defaultAddress，两个名字都兼容 */
function isDefaultAddress(addr) {
  return Boolean(addr.defaultAddress ?? addr.isDefault)
}

onMounted(load)
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">确认订单</h1>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <!-- 收货地址 -->
    <section class="bg-white rounded-lg border border-gray-200 p-4">
      <h2 class="text-sm font-medium text-gray-700 mb-3">收货地址</h2>
      <div v-if="!addresses.length" class="text-sm text-gray-500">
        暂无收货地址，请先到
        <RouterLink :to="{ name: 'profile' }" class="text-blue-600 hover:underline">个人中心</RouterLink>
        添加
      </div>
      <div v-else class="space-y-2">
        <label
          v-for="addr in addresses"
          :key="addr.addressId"
          class="flex items-start gap-2 p-3 rounded border cursor-pointer"
          :class="selectedAddressId === addr.addressId ? 'border-blue-500 bg-blue-50' : 'border-gray-200'"
        >
          <input v-model="selectedAddressId" type="radio" :value="addr.addressId" class="mt-1" />
          <div class="text-sm">
            <p class="font-medium text-gray-900">
              {{ addr.receiver }} {{ addr.phone }}
              <span v-if="isDefaultAddress(addr)" class="ml-2 text-xs text-blue-600 border border-blue-600 px-1 rounded">默认</span>
            </p>
            <p class="text-gray-500">
              {{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detail }}
            </p>
          </div>
        </label>
      </div>
    </section>

    <!-- 商品清单 -->
    <section class="bg-white rounded-lg border border-gray-200 p-4">
      <h2 class="text-sm font-medium text-gray-700 mb-3">商品清单</h2>
      <div
        v-for="item in items"
        :key="item.cartItemId"
        class="flex items-center gap-4 py-3 border-b border-gray-100 last:border-b-0"
      >
        <img :src="item.mainImage || '/icons.svg'" class="w-16 h-16 object-cover rounded" alt="" />
        <div class="flex-1">
          <p class="text-sm font-medium text-gray-900">{{ item.title }}</p>
          <p class="text-xs text-gray-500">{{ item.specName }} × {{ item.quantity }}</p>
        </div>
        <p class="text-sm text-red-600 font-medium">
          ¥{{ formatPrice(item.price * item.quantity) }}
        </p>
      </div>
    </section>

    <!-- 合计 -->
    <section class="bg-white rounded-lg border border-gray-200 p-4 flex items-center justify-between">
      <span class="text-sm text-gray-600">
        共 {{ items.reduce((s, i) => s + i.quantity, 0) }} 件
      </span>
      <div class="flex items-center gap-4">
        <span class="text-sm text-gray-600">
          应付合计 <span class="text-xl font-semibold text-red-600">¥{{ formatPrice(total) }}</span>
        </span>
        <button
          class="px-6 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
          :disabled="loading"
          @click="submit"
        >
          {{ loading ? '提交中…' : '提交订单' }}
        </button>
      </div>
    </section>
  </div>
</template>
