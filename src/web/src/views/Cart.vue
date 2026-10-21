<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { cartApi } from '../api'

const router = useRouter()
const cart = ref({ items: [] })
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await cartApi.get()
    cart.value = data?.data ?? data ?? { items: [] }
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

const selectedItems = computed(() => cart.value.items.filter((i) => i.selected && !i.invalid))
const selectedTotal = computed(() => selectedItems.value.reduce((sum, i) => sum + i.price * i.quantity, 0))
const selectedCount = computed(() => selectedItems.value.reduce((sum, i) => sum + i.quantity, 0))

async function updateSelected(item) {
  try {
    await cartApi.updateSelected(item.cartItemId, !item.selected)
    item.selected = !item.selected
  } catch (err) {
    error.value = err.response?.data?.message || '操作失败'
  }
}

async function updateQuantity(item) {
  if (item.quantity < 1) {
    item.quantity = 1
  }
  const max = Math.min(999, item.stockAvailable ?? 999)
  if (item.quantity > max) {
    item.quantity = max
  }
  try {
    await cartApi.updateQuantity(item.cartItemId, item.quantity)
  } catch (err) {
    error.value = err.response?.data?.message || '修改数量失败'
    load()
  }
}

function changeQuantity(item, delta) {
  const next = item.quantity + delta
  const max = Math.min(999, item.stockAvailable ?? 999)
  if (next < 1 || next > max) return
  item.quantity = next
  updateQuantity(item)
}

async function removeItem(item) {
  try {
    await cartApi.remove(item.cartItemId)
    cart.value.items = cart.value.items.filter((i) => i.cartItemId !== item.cartItemId)
  } catch (err) {
    error.value = err.response?.data?.message || '删除失败'
  }
}

function checkout() {
  if (!selectedItems.value.length) {
    error.value = '请至少选择一件商品'
    return
  }
  sessionStorage.setItem('checkoutItems', JSON.stringify(selectedItems.value))
  router.push({ name: 'order-confirm' })
}

function formatPrice(cents) {
  return (cents / 100).toFixed(2)
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">购物车</h1>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
    <div v-else-if="!cart.items.length" class="py-16 text-center text-gray-400 text-sm">
      购物车是空的，去商品市场逛逛吧
    </div>

    <div v-else class="bg-white rounded-lg border border-gray-200 overflow-hidden">
      <div
        v-for="item in cart.items"
        :key="item.cartItemId"
        class="flex items-center gap-4 p-4 border-b border-gray-100 last:border-b-0"
        :class="item.invalid ? 'opacity-50' : ''"
      >
        <input
          type="checkbox"
          :checked="item.selected"
          :disabled="item.invalid"
          class="w-4 h-4 text-blue-600"
          @change="updateSelected(item)"
        />
        <img
          :src="item.mainImage || '/icons.svg'"
          class="w-20 h-20 object-cover rounded border border-gray-200"
          alt=""
        />
        <div class="flex-1 min-w-0">
          <p class="text-sm font-medium text-gray-900 truncate">{{ item.title }}</p>
          <p class="text-xs text-gray-500">{{ item.specName }}</p>
          <p v-if="item.invalid" class="text-xs text-red-500 mt-1">
            {{ item.invalidReason || '商品已失效' }}
          </p>
        </div>
        <div class="text-right">
          <p class="text-sm text-red-600 font-medium">¥{{ formatPrice(item.price) }}</p>
          <div class="flex items-center justify-end gap-2 mt-1">
            <button
              class="w-6 h-6 rounded border border-gray-300 text-gray-600 hover:bg-gray-100 disabled:opacity-40"
              :disabled="item.quantity <= 1"
              @click="changeQuantity(item, -1)"
            >
              −
            </button>
            <input
              v-model.number="item.quantity"
              type="number"
              min="1"
              class="w-12 text-center text-sm border border-gray-300 rounded"
              @blur="updateQuantity(item)"
            />
            <button
              class="w-6 h-6 rounded border border-gray-300 text-gray-600 hover:bg-gray-100 disabled:opacity-40"
              :disabled="item.quantity >= Math.min(999, item.stockAvailable ?? 999)"
              @click="changeQuantity(item, 1)"
            >
              +
            </button>
          </div>
        </div>
        <button
          class="text-xs text-gray-400 hover:text-red-600"
          @click="removeItem(item)"
        >
          删除
        </button>
      </div>

      <div class="flex items-center justify-between p-4 bg-gray-50">
        <span class="text-sm text-gray-600">
          已选 {{ selectedCount }} 件，合计
          <span class="text-lg font-semibold text-red-600">¥{{ formatPrice(selectedTotal) }}</span>
        </span>
        <button
          class="px-6 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
          :disabled="!selectedItems.length"
          @click="checkout"
        >
          去结算
        </button>
      </div>
    </div>
  </div>
</template>
