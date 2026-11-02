<script setup>
import { onMounted, ref } from 'vue'
import { isOk, productApi, unwrapResult } from '../../api'
import { PRODUCT_STATUS_MAP, PRODUCT_STATUS_TAG, statusTag, statusText } from '../../constants/status'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import { formatTotal } from '../../utils/format'

/* ---------- 1. 状态 ---------- */
const products = ref([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
/** 行级忙状态：正在审核的商品 ID */
const processing = ref('')

/* ---------- 2. 计算属性 ---------- */
// 卡片模式不需要表格列定义

/* ---------- 3. 方法 ---------- */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    // 管理员带 status 时后端走管理列表，可按审核状态筛选（保持改造前参数不变）
    const { data } = await productApi.list({ status: 'PENDING', size: 100 })
    if (!isOk(data)) {
      loadError.value = data?.message || '加载待审核商品失败'
      products.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    // 兼容分页结构 { list, total } 与直接返回数组两种形式
    products.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = payload?.total ?? products.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载待审核商品失败')
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function audit(product, approved) {
  const actionText = approved ? '通过' : '驳回'
  const ok = await confirmAction({
    message: approved
      ? `确定审核通过商品「${product.title}」？通过后将在商品列表展示。`
      : `确定驳回商品「${product.title}」？驳回后卖家需修改后重新提交。`,
    title: `${actionText}审核`,
    danger: !approved,
  })
  if (!ok) return

  if (processing.value) return
  processing.value = product.productId
  try {
    // 审核通过/驳回与原请求字段保持一致
    const { data } = await productApi.audit(product.productId, { approved, remark: '' })
    if (!isOk(data)) {
      showError(data?.message || '审核失败')
      return
    }
    showSuccess(`已${actionText}商品「${product.title}」`)
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, '审核失败'))
  } finally {
    processing.value = ''
  }
}

/** 金额：后端单位分 */
function formatMoney(cents) {
  const n = Number(cents)
  return Number.isFinite(n) ? `¥${(n / 100).toFixed(2)}` : '-'
}

/* ---------- 4. 生命周期 ---------- */
onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <el-card shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-base font-medium">待审核商品</span>
          <span v-if="!loadError" class="text-xs text-gray-400">{{ formatTotal(total, '件', products.length) }}</span>
        </div>
      </template>

      <el-result v-if="loadError" status="error" icon="error" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>

      <div v-else v-loading="loading" class="min-h-32">
        <el-empty v-if="!loading && !products.length" description="暂无待审核商品" />

        <div v-else class="divide-y divide-gray-100">
          <div v-for="p in products" :key="p.productId" class="py-4 first:pt-0 last:pb-0">
            <div class="flex items-start gap-3">
              <img
                :src="p.mainImage || '/icons.svg'"
                class="w-14 h-14 object-cover rounded shrink-0"
                alt=""
              />
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <p class="text-sm font-medium text-gray-900 break-all">{{ p.title }}</p>
                  <el-tag :type="statusTag(PRODUCT_STATUS_TAG, p.status)" size="small">
                    {{ statusText(PRODUCT_STATUS_MAP, p.status) }}
                  </el-tag>
                </div>
                <p class="text-xs text-gray-500 mt-1">卖家：{{ p.sellerId }}</p>
                <p class="text-sm text-gray-700 mt-1">价格：{{ formatMoney(p.price) }}</p>
              </div>
            </div>
            <div class="flex justify-end gap-2 mt-3">
              <el-button
                size="small"
                :loading="processing === p.productId"
                :disabled="!!processing && processing !== p.productId"
                @click="audit(p, false)"
              >
                驳回
              </el-button>
              <el-button
                type="success"
                size="small"
                :loading="processing === p.productId"
                :disabled="!!processing && processing !== p.productId"
                @click="audit(p, true)"
              >
                通过
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>
