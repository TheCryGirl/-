<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { cartApi, isOk, unwrapResult } from '../api'
import { confirmAction } from '../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess, showWarning } from '../components/ui/message'
import UiTable from '../components/ui/UiTable.vue'
import { formatPrice } from '../utils/format'

const router = useRouter()
const cart = ref({ items: [] })
const loading = ref(true)
const loadError = ref('')
const updatingSelection = ref(new Set())
const updatingQuantity = ref(new Set())
const deletingId = ref('')

const selectedItems = computed(() =>
  cart.value.items.filter((i) => i.selected && !i.invalid),
)
const selectedTotal = computed(() =>
  selectedItems.value.reduce((sum, i) => sum + i.price * i.quantity, 0),
)
const selectedCount = computed(() =>
  selectedItems.value.reduce((sum, i) => sum + i.quantity, 0),
)

const columns = computed(() => [
  { key: 'select', label: '', width: 54, align: 'center', slot: 'select' },
  { key: 'product', label: '商品', minWidth: 260, slot: 'product' },
  { key: 'price', label: '单价', width: 110, align: 'right', formatter: (row) => `¥${formatPrice(row.price)}` },
  { key: 'quantity', label: '数量', width: 160, align: 'center', slot: 'quantity' },
  {
    key: 'subtotal',
    label: '小计',
    width: 120,
    align: 'right',
    formatter: (row) => `¥${formatPrice(row.price * row.quantity)}`,
  },
])

function mark(setRef, cartItemId, active) {
  const next = new Set(setRef.value)
  if (active) next.add(cartItemId)
  else next.delete(cartItemId)
  setRef.value = next
}

function maxQuantity(item) {
  const stock = Number(item.stockAvailable)
  if (!Number.isFinite(stock)) return 999
  return Math.max(1, Math.min(999, stock))
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await cartApi.get()
    if (!isOk(data)) {
      loadError.value = data?.message || '购物车加载失败'
      cart.value = { items: [] }
      return
    }
    const payload = unwrapResult(data)
    if (!payload || !Array.isArray(payload.items)) {
      loadError.value = data?.message || '购物车加载失败'
      cart.value = { items: [] }
      return
    }
    cart.value = { items: payload.items }
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '购物车加载失败')
    cart.value = { items: [] }
  } finally {
    loading.value = false
  }
}

async function updateSelected(item, selected) {
  if (updatingSelection.value.has(item.cartItemId)) return
  mark(updatingSelection, item.cartItemId, true)
  try {
    const { data } = await cartApi.updateSelected(item.cartItemId, Boolean(selected))
    if (!isOk(data)) {
      showError(data?.message || '修改勾选状态失败')
      await load()
      return
    }
    item.selected = Boolean(selected)
  } catch (err) {
    showError(resolveErrorMessage(err, '修改勾选状态失败'))
    await load()
  } finally {
    mark(updatingSelection, item.cartItemId, false)
  }
}

async function updateQuantity(item, value) {
  if (updatingQuantity.value.has(item.cartItemId)) return
  const max = maxQuantity(item)
  let next = Math.round(Number(value))
  if (!Number.isFinite(next) || next < 1) next = 1
  if (next > max) next = max

  mark(updatingQuantity, item.cartItemId, true)
  try {
    const { data } = await cartApi.updateQuantity(item.cartItemId, next)
    if (!isOk(data)) {
      showError(data?.message || '修改数量失败')
      await load()
      return
    }
    // 成功后才更新本地数量，失败时不会出现金额先变后回滚的假象
    item.quantity = next
  } catch (err) {
    showError(resolveErrorMessage(err, '修改数量失败'))
    await load()
  } finally {
    mark(updatingQuantity, item.cartItemId, false)
  }
}

async function removeItem(item) {
  if (deletingId.value) return
  const ok = await confirmAction({
    message: `确定从购物车删除「${item.title}」？`,
    danger: true,
  })
  if (!ok) return

  deletingId.value = item.cartItemId
  try {
    const { data } = await cartApi.remove(item.cartItemId)
    if (!isOk(data)) {
      showError(data?.message || '删除失败')
      return
    }
    cart.value.items = cart.value.items.filter((i) => i.cartItemId !== item.cartItemId)
    showSuccess('已从购物车删除')
  } catch (err) {
    showError(resolveErrorMessage(err, '删除失败'))
  } finally {
    deletingId.value = ''
  }
}

function checkout() {
  if (!selectedItems.value.length) {
    showWarning('请至少选择一件商品')
    return
  }
  sessionStorage.setItem('checkoutItems', JSON.stringify(selectedItems.value))
  router.push({ name: 'order-confirm' })
}

function goShopping() {
  router.push({ name: 'products' })
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h1 class="text-lg font-semibold text-gray-900">购物车</h1>
      <span v-if="!loadError" class="text-xs text-gray-400">共 {{ cart.items.length }} 件商品</span>
    </div>

    <!-- 加载失败不与空购物车混淆 -->
    <el-result v-if="loadError" status="error" title="加载购物车失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="cart-retry" @click="load">重试</el-button>
      </template>
    </el-result>

    <div
      v-else-if="loading"
      v-loading="true"
      class="min-h-60 flex items-center justify-center text-sm text-gray-400"
    >
      正在加载购物车…
    </div>

    <el-empty v-else-if="!cart.items.length" description="购物车是空的，去商品市场逛逛吧">
      <el-button type="primary" data-testid="cart-go-shopping" @click="goShopping">去逛商品</el-button>
    </el-empty>

    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">购物车商品</span>
            <span class="text-xs text-gray-400">失效商品不会参与结算</span>
          </div>
        </template>

        <UiTable
          :columns="columns"
          :data="cart.items"
          row-key="cartItemId"
          empty-text="购物车是空的"
          show-actions
          actions-label="操作"
          actions-width="90"
          data-testid="cart-table"
        >
          <template #select="{ row }">
            <el-checkbox
              :model-value="row.selected"
              :disabled="row.invalid || updatingSelection.has(row.cartItemId)"
              :data-testid="`cart-select-${row.cartItemId}`"
              @change="(value) => updateSelected(row, value)"
            />
          </template>

          <template #product="{ row }">
            <div class="flex items-center gap-3 min-w-0">
              <el-image
                v-if="row.mainImage"
                :src="row.mainImage"
                fit="cover"
                class="w-16 h-16 rounded border border-gray-200 shrink-0"
                :preview-src-list="[row.mainImage]"
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
              <div class="min-w-0">
                <p class="truncate text-sm font-medium text-gray-900" :title="row.title">{{ row.title }}</p>
                <p class="text-xs text-gray-500">{{ row.specName || '默认规格' }}</p>
                <el-tag v-if="row.invalid" type="danger" size="small" class="mt-1">
                  {{ row.invalidReason || '商品已失效' }}
                </el-tag>
              </div>
            </div>
          </template>

          <template #quantity="{ row }">
            <el-input-number
              :model-value="row.quantity"
              :min="1"
              :max="maxQuantity(row)"
              :step="1"
              :precision="0"
              size="small"
              :disabled="updatingQuantity.has(row.cartItemId)"
              :data-testid="`cart-quantity-${row.cartItemId}`"
              @change="(value) => updateQuantity(row, value)"
            />
          </template>

          <template #actions="{ row }">
            <el-button
              link
              type="danger"
              size="small"
              :loading="deletingId === row.cartItemId"
              :data-testid="`cart-remove-${row.cartItemId}`"
              @click="removeItem(row)"
            >
              删除
            </el-button>
          </template>
        </UiTable>
      </el-card>

      <div class="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-white p-4">
        <span class="text-sm text-gray-600">
          已选 {{ selectedCount }} 件，合计
          <span class="text-lg font-semibold text-red-600">¥{{ formatPrice(selectedTotal) }}</span>
        </span>
        <el-button
          type="primary"
          :disabled="!selectedItems.length"
          data-testid="cart-checkout"
          @click="checkout"
        >
          去结算
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
