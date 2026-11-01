<script setup>
/**
 * 卖家商品管理（#81 T01，试点页）
 *
 * 结构照 src/views/_StandardPage.vue：el-card 包 UiTable，加载 / 空 / 失败三态分开，
 * 写操作一律 confirmAction + isOk + 防重复提交。
 *
 * 数据沿用 GET /products/mine，上下架沿用 PUT /products/:id/status，
 * 原操作条件（在售可下架、已下架可上架、已驳回提示重新提交）保持不变。
 *
 * 注：后端 /products/mine 支持 status/page/size，但 src/api/index.js 的 mine()
 * 没有透传参数，该文件属禁改清单，因此本页暂不做筛选与分页（与原页面范围一致）。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isOk, productApi, unwrapResult } from '../../api'
import { PRODUCT_STATUS_MAP, PRODUCT_STATUS_TAG, statusTag, statusText } from '../../constants/status'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import UiTable from '../../components/ui/UiTable.vue'
import { formatTotal } from '../../utils/format'

const router = useRouter()

/* ---------- 1. 状态 ---------- */
const list = ref([])
const loading = ref(false)
const loadError = ref('')
/** 后端返回的总条数：mine() 暂未透传分页参数，可能大于当前列表 */
const total = ref(0)
/** 正在上下架的商品 ID；非空时忽略新的操作，避免重复提交 */
const operatingId = ref('')

/* ---------- 2. 计算属性 ---------- */
const columns = computed(() => [
  { key: 'title', label: '商品', minWidth: 260, slot: 'product' },
  { key: 'price', label: '价格', width: 120, align: 'right', slot: 'price' },
  { key: 'stock', label: '库存', width: 100, align: 'right' },
  { key: 'status', label: '状态', width: 110, align: 'center', slot: 'status' },
  { key: 'createdAt', label: '创建时间', width: 160, formatter: (row) => formatDateTime(row.createdAt) },
])

/* ---------- 3. 方法 ---------- */
async function fetchList() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await productApi.mine()
    if (!isOk(data)) {
      loadError.value = data?.message || '加载商品失败'
      list.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    list.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = Array.isArray(payload) ? payload.length : payload?.total ?? list.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载商品失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  router.push({ name: 'seller-product-new' })
}

function handleEdit(row) {
  router.push({ name: 'seller-product-edit', params: { id: row.productId } })
}

/**
 * 上下架：保持原按钮语义（点了就提交），提交前二次确认。
 * 后端失败只提示错误、不改本地行数据，避免界面出现假成功。
 */
async function handleChangeStatus(row, next) {
  const actionText = next === 'OFF_SHELF' ? '下架' : '上架'

  const ok = await confirmAction({
    message: `确定${actionText}商品「${row.title}」？`,
    danger: next === 'OFF_SHELF',
  })
  if (!ok) return

  if (operatingId.value) return
  operatingId.value = row.productId
  try {
    const { data } = await productApi.updateStatus(row.productId, next)
    if (!isOk(data)) {
      showError(data?.message || `${actionText}失败`)
      return
    }
    showSuccess(`${actionText}成功`)
    await fetchList()
  } catch (err) {
    showError(resolveErrorMessage(err, `${actionText}失败`))
  } finally {
    operatingId.value = ''
  }
}

/** 金额：后端单位是分 */
function formatMoney(cents) {
  const n = Number(cents)
  return Number.isFinite(n) ? `¥${(n / 100).toFixed(2)}` : '-'
}

/** 时间：统一 YYYY-MM-DD HH:mm */
function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

/* ---------- 4. 生命周期 ---------- */
onMounted(fetchList)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-gray-900">商品管理</h1>
      <span v-if="!loadError" class="text-xs text-gray-400">{{ formatTotal(total, '件', list.length) }}</span>
    </div>

    <!-- 后端支持分页，但 api 层 mine() 未透传参数：超过一页时明确提示，避免以为商品丢了 -->
    <el-alert
      v-if="total > list.length"
      type="info"
      :closable="false"
      show-icon
      :title="`共 ${total} 件，当前仅显示前 ${list.length} 件`"
    />

    <el-card shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-base font-medium">商品列表</span>
          <el-button type="primary" data-testid="seller-products-create" @click="handleCreate">
            发布商品
          </el-button>
        </div>
      </template>

      <!-- 失败态：与空态严格区分，可重试 -->
      <el-result v-if="loadError" status="error" title="加载商品失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" data-testid="seller-products-retry" @click="fetchList">
            重试
          </el-button>
        </template>
      </el-result>

      <UiTable
        v-else
        :columns="columns"
        :data="list"
        :loading="loading"
        row-key="productId"
        empty-text="暂无商品"
        show-actions
        actions-width="170"
      >
        <template #product="{ row }">
          <div class="flex items-center gap-3">
            <el-image
              v-if="row.mainImage"
              :src="row.mainImage"
              fit="cover"
              class="w-10 h-10 rounded shrink-0"
              :preview-src-list="[row.mainImage]"
              preview-teleported
            >
              <template #error>
                <div class="w-10 h-10 rounded bg-gray-100 text-xs text-gray-400 flex items-center justify-center">
                  无图
                </div>
              </template>
            </el-image>
            <div
              v-else
              class="w-10 h-10 rounded bg-gray-100 text-xs text-gray-400 flex items-center justify-center shrink-0"
            >
              无图
            </div>
            <div class="min-w-0">
              <p class="truncate text-gray-800">{{ row.title }}</p>
              <p v-if="row.subtitle" class="truncate text-xs text-gray-400">{{ row.subtitle }}</p>
            </div>
          </div>
        </template>

        <template #price="{ row }">
          <span class="text-red-600 font-medium">{{ formatMoney(row.price) }}</span>
        </template>

        <template #status="{ row }">
          <el-tag :type="statusTag(PRODUCT_STATUS_TAG, row.status)" size="small">
            {{ statusText(PRODUCT_STATUS_MAP, row.status) }}
          </el-tag>
        </template>

        <template #actions="{ row }">
          <el-button
            link
            type="primary"
            size="small"
            :data-testid="`seller-products-edit-${row.productId}`"
            @click="handleEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="row.status === 'ON_SALE'"
            link
            type="warning"
            size="small"
            :loading="operatingId === row.productId"
            :data-testid="`seller-products-off-shelf-${row.productId}`"
            @click="handleChangeStatus(row, 'OFF_SHELF')"
          >
            下架
          </el-button>
          <el-button
            v-if="row.status === 'OFF_SHELF'"
            link
            type="success"
            size="small"
            :loading="operatingId === row.productId"
            :data-testid="`seller-products-on-sale-${row.productId}`"
            @click="handleChangeStatus(row, 'ON_SALE')"
          >
            上架
          </el-button>
          <span v-if="row.status === 'REJECTED'" class="text-xs text-gray-400">
            请编辑后重新提交审核
          </span>
        </template>
      </UiTable>
    </el-card>
  </div>
</template>
