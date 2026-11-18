<script setup>
import { computed, onMounted, ref } from 'vue'
import { http, isOk, unwrapResult } from '../../api'
import PageHero from '../../components/store/PageHero.vue'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import { formatPrice, formatTime } from '../../utils/format'

/**
 * 促销活动配置（#140）：优惠券模板与满减/满折活动。
 *
 * 券与活动都由管理员创建；金额输入按元，提交时换算成分（后端统一以「分」存储）。
 */

/* ---------------- 通用 ---------------- */
const tab = ref('coupon')
const activeError = ref('')

/* ---------------- 优惠券模板 ---------------- */
const coupons = ref([])
const couponTotal = ref(0)
const couponPage = ref(1)
const couponSize = 20
const couponLoading = ref(false)
const couponDialogVisible = ref(false)
const couponSaving = ref(false)
const couponFormRef = ref(null)
const couponForm = ref(blankCoupon())

const couponTotalPages = computed(() => Math.max(1, Math.ceil(couponTotal.value / couponSize)))

const COUPON_TYPE_OPTIONS = [
  { value: 'FIXED', label: '满减券（面额，分）' },
  { value: 'PERCENT', label: '折扣券（85 = 8.5 折）' },
]

const couponRules = {
  shopId: [{ required: true, message: '请填写店铺ID', trigger: 'blur' }],
  name: [{ required: true, message: '请填写券名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择券类型', trigger: 'change' }],
  value: [{ required: true, message: '请填写券值', trigger: 'blur' }],
  minAmount: [{ required: true, message: '请填写使用门槛', trigger: 'blur' }],
  totalQty: [{ required: true, message: '请填写发行量', trigger: 'blur' }],
  range: [{ required: true, message: '请选择领取时间', trigger: 'change' }],
}

function blankCoupon() {
  return {
    shopId: '',
    name: '',
    type: 'FIXED',
    value: '',
    minAmount: '',
    totalQty: '100',
    range: null,
  }
}

function couponTypeLabel(type) {
  return COUPON_TYPE_OPTIONS.find((o) => o.value === type)?.label || type
}

async function loadCoupons() {
  couponLoading.value = true
  activeError.value = ''
  try {
    const { data } = await http.get('/coupons', {
      params: { page: couponPage.value, size: couponSize },
    })
    if (!isOk(data)) {
      activeError.value = data?.message || '加载券模板失败'
      coupons.value = []
      couponTotal.value = 0
      return
    }
    const payload = unwrapResult(data)
    coupons.value = payload?.list || []
    couponTotal.value = payload?.total || 0
  } catch (err) {
    activeError.value = resolveErrorMessage(err, '加载券模板失败')
    coupons.value = []
    couponTotal.value = 0
  } finally {
    couponLoading.value = false
  }
}

function openCouponCreate() {
  couponForm.value = blankCoupon()
  couponDialogVisible.value = true
}

async function submitCoupon() {
  if (couponSaving.value) return
  try {
    await couponFormRef.value?.validate()
  } catch {
    return
  }
  couponSaving.value = true
  try {
    const form = couponForm.value
    const payload = {
      shopId: form.shopId.trim(),
      name: form.name.trim(),
      type: form.type,
      // 金额按「分」提交：元 * 100 后取整
      value: Math.round(Number(form.value) * 100),
      minAmount: Math.round(Number(form.minAmount) * 100),
      totalQty: Number(form.totalQty),
      startAt: form.range?.[0],
      endAt: form.range?.[1],
    }
    const { data } = await http.post('/coupons', payload)
    if (!isOk(data)) {
      showError(data?.message || '创建券模板失败')
      return
    }
    couponDialogVisible.value = false
    showSuccess('券模板已创建')
    await loadCoupons()
  } catch (err) {
    showError(resolveErrorMessage(err, '创建券模板失败'))
  } finally {
    couponSaving.value = false
  }
}

/* ---------------- 满减/满折活动 ---------------- */
const promotions = ref([])
const promotionTotal = ref(0)
const promotionPage = ref(1)
const promotionSize = 20
const promotionLoading = ref(false)
const promotionDialogVisible = ref(false)
const promotionSaving = ref(false)
const togglingId = ref('')
const promotionFormRef = ref(null)
const promotionForm = ref(blankPromotion())

const promotionTotalPages = computed(() => Math.max(1, Math.ceil(promotionTotal.value / promotionSize)))

const SCOPE_OPTIONS = [
  { value: 'PLATFORM', label: '平台活动' },
  { value: 'SHOP', label: '店铺活动' },
]
const PROMOTION_TYPE_OPTIONS = [
  { value: 'FULL_REDUCE', label: '满减（减金额）' },
  { value: 'FULL_DISCOUNT', label: '满折（85 = 8.5 折）' },
]

const promotionRules = {
  scope: [{ required: true, message: '请选择活动范围', trigger: 'change' }],
  type: [{ required: true, message: '请选择活动类型', trigger: 'change' }],
  threshold: [{ required: true, message: '请填写门槛金额', trigger: 'blur' }],
  value: [{ required: true, message: '请填写优惠值', trigger: 'blur' }],
  stackable: [{ required: true, message: '请选择是否可叠加', trigger: 'change' }],
  range: [{ required: true, message: '请选择生效时间', trigger: 'change' }],
}

function blankPromotion() {
  return {
    scope: 'PLATFORM',
    shopId: '',
    type: 'FULL_REDUCE',
    threshold: '',
    value: '',
    stackable: true,
    range: null,
  }
}

function scopeLabel(scope) {
  return SCOPE_OPTIONS.find((o) => o.value === scope)?.label || scope
}

function promotionTypeLabel(type) {
  return PROMOTION_TYPE_OPTIONS.find((o) => o.value === type)?.label || type
}

function promotionValueLabel(row) {
  if (row.type === 'FULL_DISCOUNT') return `${(Number(row.value || 0) / 10).toFixed(1)} 折`
  return `¥${formatPrice(row.value)}`
}

async function loadPromotions() {
  promotionLoading.value = true
  activeError.value = ''
  try {
    const { data } = await http.get('/promotions', {
      params: { page: promotionPage.value, size: promotionSize },
    })
    if (!isOk(data)) {
      activeError.value = data?.message || '加载活动失败'
      promotions.value = []
      promotionTotal.value = 0
      return
    }
    const payload = unwrapResult(data)
    promotions.value = payload?.list || []
    promotionTotal.value = payload?.total || 0
  } catch (err) {
    activeError.value = resolveErrorMessage(err, '加载活动失败')
    promotions.value = []
    promotionTotal.value = 0
  } finally {
    promotionLoading.value = false
  }
}

function openPromotionCreate() {
  promotionForm.value = blankPromotion()
  promotionDialogVisible.value = true
}

async function submitPromotion() {
  if (promotionSaving.value) return
  try {
    await promotionFormRef.value?.validate()
  } catch {
    return
  }
  promotionSaving.value = true
  try {
    const form = promotionForm.value
    const payload = {
      scope: form.scope,
      shopId: form.scope === 'SHOP' ? form.shopId.trim() : null,
      type: form.type,
      threshold: Math.round(Number(form.threshold) * 100),
      value: form.type === 'FULL_DISCOUNT' ? Number(form.value) : Math.round(Number(form.value) * 100),
      stackable: form.stackable,
      startAt: form.range?.[0],
      endAt: form.range?.[1],
    }
    const { data } = await http.post('/promotions', payload)
    if (!isOk(data)) {
      showError(data?.message || '创建活动失败')
      return
    }
    promotionDialogVisible.value = false
    showSuccess('活动已创建')
    await loadPromotions()
  } catch (err) {
    showError(resolveErrorMessage(err, '创建活动失败'))
  } finally {
    promotionSaving.value = false
  }
}

async function togglePromotion(row) {
  if (togglingId.value) return
  const nextStatus = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  togglingId.value = row.promotionId
  try {
    const { data } = await http.put(`/promotions/${row.promotionId}`, { status: nextStatus })
    if (!isOk(data)) {
      showError(data?.message || '状态更新失败')
      return
    }
    showSuccess(nextStatus === 'ACTIVE' ? '活动已启用' : '活动已停用')
    await loadPromotions()
  } catch (err) {
    showError(resolveErrorMessage(err, '状态更新失败'))
  } finally {
    togglingId.value = ''
  }
}

function onTabChange() {
  activeError.value = ''
  if (tab.value === 'coupon') loadCoupons()
  else loadPromotions()
}

onMounted(loadCoupons)
</script>

<template>
  <div class="max-w-5xl mx-auto space-y-4">
    <PageHero
      title="促销活动配置"
      subtitle="维护优惠券模板与满减/满折活动；金额一律以「分」落库，页面按元展示"
      icon="present"
      tone="rose"
    />

    <el-card shadow="never">
      <el-tabs v-model="tab" data-testid="promotion-admin-tabs" @tab-change="onTabChange">
        <!-- 优惠券模板 -->
        <el-tab-pane label="优惠券模板" name="coupon">
          <div class="flex flex-wrap items-center justify-between gap-2 mb-3">
            <span class="text-xs text-gray-400">买家在个人中心领券，结算时按店铺匹配可用券</span>
            <el-button type="primary" data-testid="promotion-admin-coupon-create" @click="openCouponCreate">
              创建券模板
            </el-button>
          </div>

          <el-alert
            v-if="activeError"
            type="error"
            :closable="false"
            show-icon
            class="mb-3"
            :title="activeError"
          />

          <div v-if="couponLoading" v-loading="true" class="min-h-32" />

          <el-empty v-else-if="!coupons.length" description="还没有券模板" />

          <el-table v-else :data="coupons" data-testid="promotion-admin-coupon-table">
            <el-table-column prop="name" label="券名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="shopId" label="店铺" min-width="110" show-overflow-tooltip />
            <el-table-column label="类型" width="150">
              <template #default="{ row }">{{ couponTypeLabel(row.type) }}</template>
            </el-table-column>
            <el-table-column label="券值" width="110">
              <template #default="{ row }">
                <template v-if="row.type === 'PERCENT'">{{ (Number(row.value) / 10).toFixed(1) }} 折</template>
                <template v-else>¥{{ formatPrice(row.value) }}</template>
              </template>
            </el-table-column>
            <el-table-column label="门槛" width="100">
              <template #default="{ row }">¥{{ formatPrice(row.minAmount) }}</template>
            </el-table-column>
            <el-table-column label="领取" width="90">
              <template #default="{ row }">{{ row.issuedQty }}/{{ row.totalQty }}</template>
            </el-table-column>
            <el-table-column label="有效期" min-width="200">
              <template #default="{ row }">{{ formatTime(row.startAt) }} ~ {{ formatTime(row.endAt) }}</template>
            </el-table-column>
          </el-table>

          <div class="flex items-center justify-end gap-2 mt-3">
            <el-button :disabled="couponPage <= 1" @click="couponPage--; loadCoupons()">上一页</el-button>
            <span class="text-xs text-gray-400">{{ couponPage }} / {{ couponTotalPages }}</span>
            <el-button :disabled="couponPage >= couponTotalPages" @click="couponPage++; loadCoupons()">下一页</el-button>
          </div>
        </el-tab-pane>

        <!-- 满减/满折活动 -->
        <el-tab-pane label="满减/满折活动" name="promotion">
          <div class="flex flex-wrap items-center justify-between gap-2 mb-3">
            <span class="text-xs text-gray-400">活动对全部买家生效；关闭叠加后不能与优惠券同时使用</span>
            <el-button type="primary" data-testid="promotion-admin-create" @click="openPromotionCreate">
              创建活动
            </el-button>
          </div>

          <el-alert
            v-if="activeError"
            type="error"
            :closable="false"
            show-icon
            class="mb-3"
            :title="activeError"
          />

          <div v-if="promotionLoading" v-loading="true" class="min-h-32" />

          <el-empty v-else-if="!promotions.length" description="还没有满减/满折活动" />

          <el-table v-else :data="promotions" data-testid="promotion-admin-table">
            <el-table-column label="范围" width="100">
              <template #default="{ row }">{{ scopeLabel(row.scope) }}</template>
            </el-table-column>
            <el-table-column label="店铺" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.shopId || '—' }}</template>
            </el-table-column>
            <el-table-column label="类型" width="130">
              <template #default="{ row }">{{ promotionTypeLabel(row.type) }}</template>
            </el-table-column>
            <el-table-column label="门槛" width="100">
              <template #default="{ row }">¥{{ formatPrice(row.threshold) }}</template>
            </el-table-column>
            <el-table-column label="优惠值" width="100">
              <template #default="{ row }">{{ promotionValueLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="可叠券" width="80">
              <template #default="{ row }">{{ row.stackable ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="生效时间" min-width="200">
              <template #default="{ row }">{{ formatTime(row.startAt) }} ~ {{ formatTime(row.endAt) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? '生效中' : '已停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button
                  link
                  :type="row.status === 'ACTIVE' ? 'danger' : 'primary'"
                  size="small"
                  :loading="togglingId === row.promotionId"
                  @click="togglePromotion(row)"
                >
                  {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="flex items-center justify-end gap-2 mt-3">
            <el-button :disabled="promotionPage <= 1" @click="promotionPage--; loadPromotions()">上一页</el-button>
            <span class="text-xs text-gray-400">{{ promotionPage }} / {{ promotionTotalPages }}</span>
            <el-button
              :disabled="promotionPage >= promotionTotalPages"
              @click="promotionPage++; loadPromotions()"
            >
              下一页
            </el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 创建券模板 -->
    <el-dialog v-model="couponDialogVisible" title="创建优惠券模板" width="560px" destroy-on-close>
      <el-form ref="couponFormRef" :model="couponForm" :rules="couponRules" label-width="90px">
        <el-form-item label="店铺ID" prop="shopId">
          <el-input v-model="couponForm.shopId" maxlength="32" placeholder="券归属店铺的卖家ID" data-testid="coupon-form-shop" />
        </el-form-item>
        <el-form-item label="券名称" prop="name">
          <el-input v-model="couponForm.name" maxlength="64" placeholder="例如：开学季满 50 减 10" data-testid="coupon-form-name" />
        </el-form-item>
        <el-form-item label="券类型" prop="type">
          <el-select v-model="couponForm.type" data-testid="coupon-form-type">
            <el-option v-for="o in COUPON_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="券值" prop="value">
          <el-input
            v-model="couponForm.value"
            :placeholder="couponForm.type === 'PERCENT' ? '折扣百分比，如 85 表示 8.5 折' : '面额（元）'"
            data-testid="coupon-form-value"
          />
        </el-form-item>
        <el-form-item label="使用门槛" prop="minAmount">
          <el-input v-model="couponForm.minAmount" placeholder="满多少元可用（元）" data-testid="coupon-form-min" />
        </el-form-item>
        <el-form-item label="发行量" prop="totalQty">
          <el-input v-model="couponForm.totalQty" placeholder="发放总张数" data-testid="coupon-form-total" />
        </el-form-item>
        <el-form-item label="领取时间" prop="range">
          <el-date-picker
            v-model="couponForm.range"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            data-testid="coupon-form-range"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="couponSaving" data-testid="coupon-form-cancel" @click="couponDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="couponSaving" data-testid="coupon-form-submit" @click="submitCoupon">
          {{ couponSaving ? '创建中…' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 创建满减/满折活动 -->
    <el-dialog v-model="promotionDialogVisible" title="创建满减/满折活动" width="560px" destroy-on-close>
      <el-form ref="promotionFormRef" :model="promotionForm" :rules="promotionRules" label-width="90px">
        <el-form-item label="范围" prop="scope">
          <el-select v-model="promotionForm.scope" data-testid="promotion-form-scope">
            <el-option v-for="o in SCOPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="promotionForm.scope === 'SHOP'" label="店铺ID" prop="shopId">
          <el-input v-model="promotionForm.shopId" maxlength="32" placeholder="活动归属店铺的卖家ID" data-testid="promotion-form-shop" />
        </el-form-item>
        <el-form-item label="活动类型" prop="type">
          <el-select v-model="promotionForm.type" data-testid="promotion-form-type">
            <el-option v-for="o in PROMOTION_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="门槛金额" prop="threshold">
          <el-input v-model="promotionForm.threshold" placeholder="满多少元生效（元）" data-testid="promotion-form-threshold" />
        </el-form-item>
        <el-form-item label="优惠值" prop="value">
          <el-input
            v-model="promotionForm.value"
            :placeholder="promotionForm.type === 'FULL_DISCOUNT' ? '折扣百分比，如 85 表示 8.5 折' : '减免金额（元）'"
            data-testid="promotion-form-value"
          />
        </el-form-item>
        <el-form-item label="可叠券" prop="stackable">
          <el-switch v-model="promotionForm.stackable" data-testid="promotion-form-stackable" />
          <span class="text-xs text-gray-400 ml-2">关闭后该活动与优惠券不能同时使用</span>
        </el-form-item>
        <el-form-item label="生效时间" prop="range">
          <el-date-picker
            v-model="promotionForm.range"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            data-testid="promotion-form-range"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="promotionSaving" data-testid="promotion-form-cancel" @click="promotionDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="promotionSaving" data-testid="promotion-form-submit" @click="submitPromotion">
          {{ promotionSaving ? '创建中…' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* Element Plus 按钮默认不显示焦点环，键盘操作时需要可见反馈 */
.el-button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
