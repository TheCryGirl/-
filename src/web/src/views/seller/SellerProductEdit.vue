<script setup>
/**
 * 卖家新建 / 编辑商品（#81 T02，最高风险页）
 *
 * 两种模式严格分开，不合并成一套可任意改 SKU 的表单：
 * - 新建：商品资料 + 可增删的初始 SKU 行（至少保留一行）
 * - 编辑：商品资料 + 已有 SKU 只能改库存、只能新增，不提供删除既有 SKU 的能力
 *
 * 主图保持 URL 字符串（与后端 mainImage 一致），不引入上传接口。
 * 单价输入是元，提交前用 Math.round(Number(yuan) * 100) 转分；12.34 元 → 1234 分。
 * el-input-number 清空会得到 null，统一走 toCents / Math.floor 兜底，绝不提交 NaN。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isOk, productApi, unwrapResult } from '../../api'
import { PRODUCT_STATUS_MAP, PRODUCT_STATUS_TAG, statusTag, statusText } from '../../constants/status'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import UiTable from '../../components/ui/UiTable.vue'

const route = useRoute()
const router = useRouter()
const productId = computed(() => route.params.id || '')
const isEdit = computed(() => Boolean(productId.value))

/* ---------- 1. 状态 ---------- */
const categories = ref([])
const categoryLoading = ref(false)
const categoryError = ref('')

const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const savingSkuId = ref('')
const addingSku = ref(false)
const productStatus = ref('')

const formRef = ref(null)
const form = reactive({
  title: '',
  subtitle: '',
  mainImage: '',
  categoryId: '',
})
const rules = {
  title: [{ required: true, message: '请填写商品标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择商品分类', trigger: 'change' }],
}

/** 新建模式：初始 SKU 行，价格单位元 */
const skus = ref([{ specName: '', price: null, stockAvailable: 0 }])
/** 编辑模式：已有 SKU，只允许调整库存 */
const existingSkus = ref([])
const newSku = reactive({ specName: '', price: null, stockAvailable: 0 })
const stockDraft = reactive({})

/* ---------- 2. 计算属性 ---------- */
const skuColumns = [
  { key: 'specName', label: '规格', minWidth: 160 },
  { key: 'price', label: '单价', width: 110, align: 'right', formatter: (row) => `¥${formatPrice(row.price)}` },
  { key: 'stockLocked', label: '已锁定', width: 90, align: 'right' },
  { key: 'stock', label: '可售库存', width: 160, align: 'center', slot: 'stock' },
]

const canSubmit = computed(() => (isEdit.value ? true : !categoryError.value && categories.value.length > 0))

/* ---------- 3. 方法 ---------- */
async function loadCategories() {
  categoryLoading.value = true
  categoryError.value = ''
  try {
    const { data } = await productApi.categories()
    if (!isOk(data)) {
      categoryError.value = data?.message || '分类加载失败'
      categories.value = []
      return
    }
    const payload = unwrapResult(data)
    categories.value = Array.isArray(payload) ? payload : payload?.list ?? []
  } catch (err) {
    categoryError.value = resolveErrorMessage(err, '分类加载失败')
    categories.value = []
  } finally {
    categoryLoading.value = false
  }
}

async function loadProduct() {
  if (!isEdit.value) return
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await productApi.detail(productId.value)
    if (!isOk(data)) {
      loadError.value = data?.message || '商品不存在或无权编辑'
      return
    }
    const product = unwrapResult(data)
    if (!product) {
      loadError.value = '商品不存在或无权编辑'
      return
    }
    form.title = product.title || ''
    form.subtitle = product.subtitle || ''
    form.mainImage = product.mainImage || ''
    form.categoryId = product.categoryId || ''
    productStatus.value = product.status || ''
    existingSkus.value = product.skus || []
    existingSkus.value.forEach((sku) => {
      stockDraft[sku.skuId] = sku.stockAvailable ?? 0
    })
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载商品失败')
  } finally {
    loading.value = false
  }
}

function addSkuRow() {
  skus.value.push({ specName: '', price: null, stockAvailable: 0 })
}

function removeSkuRow(index) {
  // 至少保留一行，与原规则一致
  if (skus.value.length === 1) return
  skus.value.splice(index, 1)
}

/**
 * 元 → 分。非法值（空、null、NaN、非正数）统一返回 0，
 * 由调用方按原规则判定为「价格需大于 0」并提示，不会把 NaN 发给后端。
 */
function toCents(yuan) {
  const value = Math.round(Number(yuan) * 100)
  return Number.isFinite(value) && value > 0 ? value : 0
}

/** 库存：空 / null / 负数一律按 0 处理，保证是正整数 */
function toStock(value) {
  const n = Math.floor(Number(value))
  return Number.isFinite(n) && n > 0 ? n : 0
}

/** 规格行 → 提交数组；没有有效行或价格非法时返回 null，沿用原判断 */
function buildSkus(rows) {
  const result = []
  for (const row of rows) {
    if (!row.specName?.trim()) continue
    const price = toCents(row.price)
    if (price <= 0) return null
    result.push({ specName: row.specName.trim(), price, stockAvailable: toStock(row.stockAvailable) })
  }
  return result.length ? result : null
}

async function handleSubmit() {
  if (saving.value) return
  if (!canSubmit.value) {
    showError(categoryError.value || '暂无可选分类，无法提交')
    return
  }

  try {
    await formRef.value?.validate()
  } catch {
    // el-form 已在字段下方给出校验提示，这里不重复弹错误
    return
  }

  if (isEdit.value) {
    await saveProduct()
  } else {
    await createProduct()
  }
}

async function createProduct() {
  const skuPayload = buildSkus(skus.value)
  if (!skuPayload) {
    showError('请至少填写一个规格，且价格需大于 0')
    return
  }

  saving.value = true
  try {
    const { data } = await productApi.create({ ...form, skus: skuPayload })
    if (!isOk(data)) {
      showError(data?.message || '发布失败')
      return
    }
    showSuccess('商品已提交，等待管理员审核')
    router.push({ name: 'seller-products' })
  } catch (err) {
    showError(resolveErrorMessage(err, '发布失败'))
  } finally {
    saving.value = false
  }
}

async function saveProduct() {
  saving.value = true
  try {
    const { data } = await productApi.update(productId.value, { ...form })
    if (!isOk(data)) {
      showError(data?.message || '保存失败')
      return
    }
    // 修改商品后后端会把状态重置为待审核（BR-07），必须先告知用户再刷新
    showSuccess('商品信息已保存，修改后需重新审核上架')
    // 保持原流程：保存后重新拉取详情。
    // 若商品因状态变化导致详情读不到，属既有问题，不在本次 UI 任务里改业务流。
    await loadProduct()
  } catch (err) {
    showError(resolveErrorMessage(err, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function handleAddSku() {
  if (addingSku.value) return
  if (!newSku.specName?.trim()) {
    showError('请填写规格名称')
    return
  }
  const price = toCents(newSku.price)
  if (price <= 0) {
    showError('SKU 价格需大于 0')
    return
  }

  addingSku.value = true
  try {
    const { data } = await productApi.createSku(productId.value, {
      specName: newSku.specName.trim(),
      price,
      stockAvailable: toStock(newSku.stockAvailable),
    })
    if (!isOk(data)) {
      showError(data?.message || '新增规格失败')
      return
    }
    newSku.specName = ''
    newSku.price = null
    newSku.stockAvailable = 0
    showSuccess('已新增规格')
    await loadProduct()
  } catch (err) {
    showError(resolveErrorMessage(err, '新增规格失败'))
  } finally {
    addingSku.value = false
  }
}

async function handleSaveStock(sku) {
  if (savingSkuId.value) return
  const stock = toStock(stockDraft[sku.skuId])

  savingSkuId.value = sku.skuId
  try {
    const { data } = await productApi.updateStock(sku.skuId, stock)
    if (!isOk(data)) {
      showError(data?.message || '调整库存失败')
      return
    }
    showSuccess(`规格 ${sku.specName} 库存已更新为 ${stock}`)
    await loadProduct()
  } catch (err) {
    showError(resolveErrorMessage(err, '调整库存失败'))
  } finally {
    savingSkuId.value = ''
  }
}

function handleBack() {
  router.push({ name: 'seller-products' })
}

/** 金额：后端单位是分 */
function formatPrice(cents) {
  const n = Number(cents)
  return Number.isFinite(n) ? (n / 100).toFixed(2) : '-'
}

/* ---------- 4. 生命周期 ---------- */
onMounted(() => {
  loadCategories()
  loadProduct()
})
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="flex items-center gap-3">
        <h1 class="text-lg font-semibold text-gray-900">{{ isEdit ? '编辑商品' : '发布商品' }}</h1>
        <el-tag v-if="isEdit && productStatus" :type="statusTag(PRODUCT_STATUS_TAG, productStatus)" size="small">
          {{ statusText(PRODUCT_STATUS_MAP, productStatus) }}
        </el-tag>
      </div>
      <div class="flex items-center gap-3">
        <span class="text-xs text-gray-400">{{ isEdit ? '修改后需重新审核上架' : '提交后进入管理员审核' }}</span>
        <el-button link type="primary" data-testid="seller-product-back" @click="handleBack">
          返回商品管理
        </el-button>
      </div>
    </div>

    <!-- 失败态：与空态严格区分，可重试 -->
    <el-result v-if="loadError" status="error" title="加载商品失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="seller-product-retry" @click="loadProduct">重试</el-button>
      </template>
    </el-result>

    <el-form
      v-else
      ref="formRef"
      v-loading="loading"
      :model="form"
      :rules="rules"
      label-width="96px"
    >
      <!-- 基本信息 -->
      <el-card shadow="never" class="mb-4">
        <template #header>
          <span class="text-base font-medium">基本信息</span>
        </template>

        <el-form-item label="商品标题" prop="title">
          <el-input
            v-model="form.title"
            maxlength="100"
            show-word-limit
            placeholder="商品标题"
            data-testid="seller-product-title"
          />
        </el-form-item>

        <el-form-item label="副标题">
          <el-input
            v-model="form.subtitle"
            maxlength="200"
            placeholder="选填，一句话卖点"
            data-testid="seller-product-subtitle"
          />
        </el-form-item>

        <el-form-item label="主图地址">
          <div class="w-full">
            <el-input
              v-model="form.mainImage"
              maxlength="255"
              placeholder="选填，例如 /images/xxx.jpg"
              data-testid="seller-product-main-image"
            />
            <p class="mt-1 text-xs text-gray-400">
              这里填写图片地址（与商品原有字段一致），本页不做上传，避免多出一次写请求。
            </p>
          </div>
        </el-form-item>

        <el-form-item label="分类" prop="categoryId">
          <div class="w-full">
            <el-select
              v-model="form.categoryId"
              placeholder="请选择分类"
              filterable
              :loading="categoryLoading"
              :disabled="!canSubmit"
              style="width: 260px"
              data-testid="seller-product-category"
            >
              <el-option
                v-for="c in categories"
                :key="c.categoryId"
                :label="c.name"
                :value="c.categoryId"
              />
            </el-select>
            <el-alert
              v-if="categoryError"
              type="warning"
              :closable="false"
              show-icon
              class="mt-2"
              title="分类加载失败，请重试后再选择"
            >
              <el-button link type="primary" size="small" @click="loadCategories">重新加载分类</el-button>
            </el-alert>
            <el-alert
              v-else-if="!categoryLoading && !categories.length"
              type="warning"
              :closable="false"
              show-icon
              class="mt-2"
              title="暂无可选分类，请联系管理员创建后再发布商品"
            />
          </div>
        </el-form-item>
      </el-card>

      <!-- 新建：可增删的初始规格 -->
      <el-card v-if="!isEdit" shadow="never" class="mb-4">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-base font-medium">商品规格</span>
            <el-button link type="primary" data-testid="seller-product-add-sku-row" @click="addSkuRow">
              添加规格
            </el-button>
          </div>
        </template>

        <div
          v-for="(row, index) in skus"
          :key="`new-sku-${index}`"
          class="flex flex-wrap items-center gap-2 mb-3"
        >
          <el-input
            v-model="row.specName"
            placeholder="规格名称，如 黑色"
            style="width: 200px"
            :data-testid="`seller-product-sku-name-${index}`"
          />
          <el-input-number
            v-model="row.price"
            :min="0.01"
            :step="0.01"
            :precision="2"
            controls-position="right"
            placeholder="价格（元）"
            style="width: 150px"
            :data-testid="`seller-product-sku-price-${index}`"
          />
          <el-input-number
            v-model="row.stockAvailable"
            :min="0"
            :step="1"
            :precision="0"
            controls-position="right"
            placeholder="库存"
            style="width: 130px"
            :data-testid="`seller-product-sku-stock-${index}`"
          />
          <el-button
            link
            type="danger"
            :disabled="skus.length === 1"
            :data-testid="`seller-product-sku-remove-${index}`"
            @click="removeSkuRow(index)"
          >
            删除
          </el-button>
        </div>
        <p class="text-xs text-gray-400">至少保留一行；规格名称为空的行不会提交。</p>
      </el-card>

      <!-- 编辑：已有规格只改库存，另可新增 -->
      <el-card v-if="isEdit" shadow="never" class="mb-4">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-base font-medium">已有规格与库存</span>
            <span class="text-xs text-gray-400">已有规格只支持调整库存与新增，不支持删除或改价</span>
          </div>
        </template>

        <UiTable
          :columns="skuColumns"
          :data="existingSkus"
          row-key="skuId"
          empty-text="暂无规格，请在下方新增"
          show-actions
          actions-width="110"
        >
          <template #stock="{ row }">
            <el-input-number
              v-model="stockDraft[row.skuId]"
              :min="0"
              :step="1"
              :precision="0"
              size="small"
              controls-position="right"
              style="width: 120px"
              :data-testid="`seller-sku-stock-${row.skuId}`"
            />
          </template>
          <template #actions="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              :loading="savingSkuId === row.skuId"
              :data-testid="`seller-sku-stock-save-${row.skuId}`"
              @click="handleSaveStock(row)"
            >
              保存库存
            </el-button>
          </template>
        </UiTable>

        <el-divider content-position="left">新增规格</el-divider>
        <div class="flex flex-wrap items-center gap-2">
          <el-input
            v-model="newSku.specName"
            placeholder="规格名称"
            style="width: 200px"
            data-testid="seller-sku-new-name"
          />
          <el-input-number
            v-model="newSku.price"
            :min="0.01"
            :step="0.01"
            :precision="2"
            controls-position="right"
            placeholder="价格（元）"
            style="width: 150px"
            data-testid="seller-sku-new-price"
          />
          <el-input-number
            v-model="newSku.stockAvailable"
            :min="0"
            :step="1"
            :precision="0"
            controls-position="right"
            placeholder="库存"
            style="width: 130px"
            data-testid="seller-sku-new-stock"
          />
          <el-button
            type="primary"
            plain
            :loading="addingSku"
            data-testid="seller-sku-new-submit"
            @click="handleAddSku"
          >
            新增
          </el-button>
        </div>
      </el-card>

      <div class="flex justify-end gap-2">
        <el-button data-testid="seller-product-cancel" @click="handleBack">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          data-testid="seller-product-save"
          @click="handleSubmit"
        >
          {{ isEdit ? '保存修改' : '提交审核' }}
        </el-button>
      </div>
    </el-form>
  </div>
</template>
