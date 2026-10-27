<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isOk, productApi, unwrapResult } from '../../api'

const route = useRoute()
const router = useRouter()
const productId = computed(() => route.params.id || '')
const isEdit = computed(() => Boolean(productId.value))

const categories = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const message = ref('')

const form = reactive({
  title: '',
  subtitle: '',
  mainImage: '',
  categoryId: '',
})

// 新建商品时的 SKU 列表
const skus = ref([{ specName: '', price: '', stockAvailable: '' }])
// 编辑商品时已有的 SKU（只允许调整库存）
const existingSkus = ref([])
const newSku = reactive({ specName: '', price: '', stockAvailable: '' })
const stockDraft = reactive({})

async function loadCategories() {
  try {
    const { data } = await productApi.categories()
    const payload = unwrapResult(data)
    categories.value = Array.isArray(payload) ? payload : payload?.list || []
  } catch {
    categories.value = []
  }
}

async function loadProduct() {
  if (!isEdit.value) return
  loading.value = true
  error.value = ''
  try {
    const { data } = await productApi.detail(productId.value)
    const product = unwrapResult(data)
    if (!product) {
      error.value = data?.message || '商品不存在或无权编辑'
      return
    }
    form.title = product.title || ''
    form.subtitle = product.subtitle || ''
    form.mainImage = product.mainImage || ''
    form.categoryId = product.categoryId || ''
    existingSkus.value = product.skus || []
    existingSkus.value.forEach((sku) => {
      stockDraft[sku.skuId] = sku.stockAvailable ?? 0
    })
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载商品失败'
  } finally {
    loading.value = false
  }
}

function addSkuRow() {
  skus.value.push({ specName: '', price: '', stockAvailable: '' })
}

function removeSkuRow(index) {
  if (skus.value.length === 1) return
  skus.value.splice(index, 1)
}

function toCents(yuan) {
  const value = Math.round(Number(yuan) * 100)
  return Number.isFinite(value) && value > 0 ? value : 0
}

function buildSkus(rows) {
  const result = []
  for (const row of rows) {
    if (!row.specName?.trim()) continue
    const price = toCents(row.price)
    const stock = Math.max(0, Math.floor(Number(row.stockAvailable) || 0))
    if (price <= 0) return null
    result.push({ specName: row.specName.trim(), price, stockAvailable: stock })
  }
  return result.length ? result : null
}

async function submit() {
  error.value = ''
  message.value = ''
  if (!form.title.trim()) {
    error.value = '请填写商品标题'
    return
  }
  if (!form.categoryId) {
    error.value = '请选择商品分类'
    return
  }

  saving.value = true
  try {
    if (isEdit.value) {
      const { data } = await productApi.update(productId.value, { ...form })
      if (!isOk(data)) {
        error.value = data?.message || '保存失败'
        return
      }
      message.value = '商品信息已保存'
    } else {
      const skuPayload = buildSkus(skus.value)
      if (!skuPayload) {
        error.value = '请至少填写一个规格，且价格需大于 0'
        saving.value = false
        return
      }
      const { data } = await productApi.create({ ...form, skus: skuPayload })
      if (!isOk(data)) {
        error.value = data?.message || '发布失败'
        return
      }
      message.value = '商品已提交，等待管理员审核'
      setTimeout(() => router.push({ name: 'seller-products' }), 800)
      return
    }
    await loadProduct()
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function addSku() {
  error.value = ''
  message.value = ''
  if (!newSku.specName?.trim()) {
    error.value = '请填写规格名称'
    return
  }
  const price = toCents(newSku.price)
  if (price <= 0) {
    error.value = 'SKU 价格需大于 0'
    return
  }
  saving.value = true
  try {
    const { data } = await productApi.createSku(productId.value, {
      specName: newSku.specName.trim(),
      price,
      stockAvailable: Math.max(0, Math.floor(Number(newSku.stockAvailable) || 0)),
    })
    if (!isOk(data)) {
      error.value = data?.message || '新增规格失败'
      return
    }
    newSku.specName = ''
    newSku.price = ''
    newSku.stockAvailable = ''
    message.value = '已新增规格'
    await loadProduct()
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '新增规格失败'
  } finally {
    saving.value = false
  }
}

async function saveStock(sku) {
  error.value = ''
  message.value = ''
  const stock = Math.max(0, Math.floor(Number(stockDraft[sku.skuId]) || 0))
  saving.value = true
  try {
    const { data } = await productApi.updateStock(sku.skuId, stock)
    if (!isOk(data)) {
      error.value = data?.message || '调整库存失败'
      return
    }
    message.value = `规格 ${sku.specName} 库存已更新为 ${stock}`
    await loadProduct()
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '调整库存失败'
  } finally {
    saving.value = false
  }
}

function formatPrice(cents) {
  return ((cents || 0) / 100).toFixed(2)
}

onMounted(() => {
  loadCategories()
  loadProduct()
})
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-gray-900">
        {{ isEdit ? '编辑商品' : '发布商品' }}
      </h1>
      <RouterLink :to="{ name: 'seller-products' }" class="text-sm text-blue-600 hover:underline">
        返回商品管理
      </RouterLink>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p v-if="message" class="text-sm text-green-600">{{ message }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

    <template v-else>
      <section class="bg-white rounded-lg border border-gray-200 p-5 space-y-3">
        <h2 class="text-sm font-medium text-gray-700">基本信息</h2>
        <label class="block text-sm text-gray-600">
          标题
          <input
            v-model="form.title"
            maxlength="100"
            class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="商品标题"
          />
        </label>
        <label class="block text-sm text-gray-600">
          副标题
          <input
            v-model="form.subtitle"
            maxlength="200"
            class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="选填，一句话卖点"
          />
        </label>
        <div class="grid gap-3 sm:grid-cols-2">
          <label class="block text-sm text-gray-600">
            主图地址
            <input
              v-model="form.mainImage"
              maxlength="255"
              class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
              placeholder="选填，上传后可填 /images/xxx.jpg"
            />
          </label>
          <label class="block text-sm text-gray-600">
            分类
            <select
              v-model="form.categoryId"
              class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm bg-white"
            >
              <option value="">请选择分类</option>
              <option v-for="c in categories" :key="c.categoryId" :value="c.categoryId">
                {{ c.name }}
              </option>
            </select>
          </label>
        </div>
      </section>

      <!-- 新建时维护初始 SKU -->
      <section v-if="!isEdit" class="bg-white rounded-lg border border-gray-200 p-5 space-y-3">
        <div class="flex items-center justify-between">
          <h2 class="text-sm font-medium text-gray-700">商品规格</h2>
          <button
            class="text-sm text-blue-600 hover:underline"
            @click="addSkuRow"
          >
            + 添加规格
          </button>
        </div>
        <div
          v-for="(row, index) in skus"
          :key="index"
          class="grid gap-2 sm:grid-cols-[1fr_100px_100px_40px] items-center"
        >
          <input
            v-model="row.specName"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="规格名称，如 黑色"
          />
          <input
            v-model="row.price"
            type="number"
            min="0.01"
            step="0.01"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="价格（元）"
          />
          <input
            v-model="row.stockAvailable"
            type="number"
            min="0"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="库存"
          />
          <button
            class="text-xs text-gray-400 hover:text-red-600 disabled:opacity-40"
            :disabled="skus.length === 1"
            @click="removeSkuRow(index)"
          >
            删除
          </button>
        </div>
      </section>

      <!-- 编辑时维护已有 SKU 库存 -->
      <section v-if="isEdit" class="bg-white rounded-lg border border-gray-200 p-5 space-y-3">
        <h2 class="text-sm font-medium text-gray-700">已有规格与库存</h2>
        <p v-if="!existingSkus.length" class="text-sm text-gray-500">暂无规格，请在下方新增</p>
        <div
          v-for="sku in existingSkus"
          :key="sku.skuId"
          class="flex flex-wrap items-center gap-3 text-sm"
        >
          <span class="w-32 text-gray-900">{{ sku.specName }}</span>
          <span class="w-24 text-red-600">¥{{ formatPrice(sku.price) }}</span>
          <span class="text-xs text-gray-400">已锁定 {{ sku.stockLocked ?? 0 }}</span>
          <input
            v-model.number="stockDraft[sku.skuId]"
            type="number"
            min="0"
            class="w-24 px-2 py-1 border border-gray-300 rounded-md text-sm"
          />
          <button
            class="text-xs text-blue-600 hover:underline"
            :disabled="saving"
            @click="saveStock(sku)"
          >
            保存库存
          </button>
        </div>

        <div class="border-t border-gray-100 pt-3 space-y-2">
          <h3 class="text-sm font-medium text-gray-700">新增规格</h3>
          <div class="grid gap-2 sm:grid-cols-[1fr_100px_100px_80px] items-center">
            <input
              v-model="newSku.specName"
              class="px-3 py-2 border border-gray-300 rounded-md text-sm"
              placeholder="规格名称"
            />
            <input
              v-model="newSku.price"
              type="number"
              min="0.01"
              step="0.01"
              class="px-3 py-2 border border-gray-300 rounded-md text-sm"
              placeholder="价格（元）"
            />
            <input
              v-model="newSku.stockAvailable"
              type="number"
              min="0"
              class="px-3 py-2 border border-gray-300 rounded-md text-sm"
              placeholder="库存"
            />
            <button
              class="px-3 py-2 rounded-md border border-blue-600 text-blue-600 text-sm hover:bg-blue-50 disabled:opacity-50"
              :disabled="saving"
              @click="addSku"
            >
              新增
            </button>
          </div>
        </div>
      </section>

      <div class="flex justify-end gap-2">
        <RouterLink
          :to="{ name: 'seller-products' }"
          class="px-5 py-2 rounded-md border border-gray-300 text-gray-600 text-sm hover:bg-gray-50"
        >
          取消
        </RouterLink>
        <button
          class="px-5 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
          :disabled="saving"
          @click="submit"
        >
          {{ saving ? '保存中…' : isEdit ? '保存修改' : '提交审核' }}
        </button>
      </div>
    </template>
  </div>
</template>
