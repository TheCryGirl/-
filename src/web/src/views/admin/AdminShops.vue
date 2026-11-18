<script setup>
import PageHero from '../../components/store/PageHero.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { isOk, shopApi, unwrapResult } from '../../api'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import UiTable from '../../components/ui/UiTable.vue'
import { usePagination } from '../../composables/usePagination'
import { formatTime, formatTotal } from '../../utils/format'
import { applyStatusTag, applyStatusText, depositText, subjectTypeText } from '../../utils/shopApply'

/* ---------- 1. 状态 ---------- */
const shops = ref([])
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
const statusFilter = ref('')
const applyFilter = ref('')
/** 行级忙状态：只锁当前操作的店铺 */
const operating = ref('')

/* 审核弹窗（#144）：通过时登记保证金（元，提交换算成分），驳回时必填理由 */
const auditVisible = ref(false)
const auditing = ref(false)
const auditTarget = ref(null)
const auditForm = reactive({ decision: 'APPROVED', reason: '', depositYuan: '' })

const pagination = usePagination({ defaultSize: 10, onChange: () => load() })
const { page, size, total, onPageChange, onSizeChange } = pagination

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'ACTIVE', label: '正常营业' },
  { value: 'SUSPENDED', label: '已停用' },
]

const applyOptions = [
  { value: '', label: '全部审核状态' },
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '审核通过' },
  { value: 'REJECTED', label: '已驳回' },
]

/* ---------- 2. 计算属性 ---------- */
const columns = [
  { key: 'name', label: '店铺', minWidth: 240, slot: 'shop', tooltip: false },
  { key: 'sellerId', label: '卖家 ID', minWidth: 160 },
  { key: 'applyStatus', label: '入驻审核', width: 130, align: 'center', slot: 'applyStatus' },
  { key: 'status', label: '营业状态', width: 120, align: 'center', slot: 'status' },
  { key: 'depositAmount', label: '保证金', width: 150, slot: 'deposit' },
  { key: 'updatedAt', label: '更新时间', width: 180, formatter: (row) => formatTime(row.updatedAt) },
]

/** 保证金输入按元填写，提交时换算成分，和后端「金额用分」的口径保持一致 */
const depositCents = computed(() => {
  const yuan = Number(auditForm.depositYuan)
  if (!Number.isFinite(yuan) || yuan < 0) return 0
  return Math.round(yuan * 100)
})

/* ---------- 3. 方法 ---------- */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: page.value, size: size.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (statusFilter.value) params.status = statusFilter.value
    if (applyFilter.value) params.applyStatus = applyFilter.value

    const { data } = await shopApi.list(params)
    if (!isOk(data)) {
      loadError.value = data?.message || '加载店铺列表失败'
      shops.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    shops.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = payload?.total ?? shops.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载店铺列表失败')
    shops.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = ''
  applyFilter.value = ''
  page.value = 1
  load()
}

function openAudit(shop) {
  auditTarget.value = shop
  auditForm.decision = 'APPROVED'
  auditForm.reason = ''
  // 已登记的金额按元回显，未登记（null）留空，提交时按 0 分处理
  auditForm.depositYuan = shop.depositAmount === null || shop.depositAmount === undefined
    ? ''
    : (shop.depositAmount / 100).toFixed(2)
  auditVisible.value = true
}

async function submitAudit() {
  const shop = auditTarget.value
  if (!shop || auditing.value) return
  if (auditForm.decision === 'REJECTED' && !auditForm.reason.trim()) {
    showError('驳回时必须填写理由，卖家会看到这段文字')
    return
  }

  const action = auditForm.decision === 'APPROVED' ? '通过' : '驳回'
  const ok = await confirmAction({
    message: auditForm.decision === 'APPROVED'
      ? `确认通过「${shop.name}」的入驻申请？登记保证金 ¥${auditForm.depositYuan || '0.00'}。审核通过后店铺即可正常营业。`
      : `确认驳回「${shop.name}」的入驻申请？理由将展示给卖家：${auditForm.reason.trim()}`,
    title: `入驻审核${action}`,
    danger: auditForm.decision === 'REJECTED',
  })
  if (!ok) return

  auditing.value = true
  try {
    const payload = { decision: auditForm.decision }
    if (auditForm.decision === 'REJECTED') {
      payload.reason = auditForm.reason.trim()
    } else {
      payload.depositAmount = depositCents.value
    }
    const { data } = await shopApi.audit(shop.shopId, payload)
    if (!isOk(data)) {
      showError(data?.message || `${action}失败`)
      return
    }
    auditVisible.value = false
    showSuccess(`已${action}`)
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, `${action}失败`))
  } finally {
    auditing.value = false
  }
}

/** 营业状态只有 ACTIVE / SUSPENDED，未知值保留原样 */
function shopStatusText(status) {
  if (status === 'ACTIVE') return '正常营业'
  if (status === 'SUSPENDED') return '已停用'
  return status || '-'
}

function shopStatusTag(status) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'SUSPENDED') return 'danger'
  return 'info'
}

async function toggleStatus(shop) {
  const suspend = shop.status !== 'SUSPENDED'
  const action = suspend ? '停用' : '恢复'
  const tip = suspend
    ? `停用后「${shop.name}」的在售商品会立即对买家隐藏，且买家无法下单。确认停用？`
    : `恢复后「${shop.name}」的商品会重新上架展示。确认恢复营业？`

  const ok = await confirmAction({
    message: tip,
    title: suspend ? '停用店铺' : '恢复营业',
    danger: suspend,
  })
  if (!ok) return

  if (operating.value) return
  operating.value = shop.shopId
  try {
    const { data } = await shopApi.updateStatus(shop.shopId, suspend ? 'SUSPENDED' : 'ACTIVE')
    if (!isOk(data)) {
      showError(data?.message || `${action}店铺失败`)
      return
    }
    showSuccess(`${action}成功`)
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, `${action}店铺失败`))
  } finally {
    operating.value = ''
  }
}

/* ---------- 4. 生命周期 ---------- */
onMounted(load)
</script>

<template>
  <div class="space-y-4">

    <PageHero
      title="店铺管理"
      subtitle="审核商家入驻申请、查看资质与保证金，必要时暂停营业"
      icon="grid"
      tone="teal"
    />
    <el-form inline class="bg-white p-4 rounded-lg" @submit.prevent>
      <el-form-item label="关键词">
        <el-input
          v-model="keyword"
          placeholder="搜索店铺名称 / 卖家 ID"
          clearable
          style="width: 240px"
          @keyup.enter="search"
          @clear="search"
        />
      </el-form-item>
      <el-form-item label="营业状态">
        <el-select v-model="statusFilter" style="width: 150px" @change="search">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="入驻审核">
        <el-select v-model="applyFilter" style="width: 160px" data-testid="admin-shop-apply-filter" @change="search">
          <el-option v-for="opt in applyOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-base font-medium">店铺列表</span>
          <span v-if="!loadError" class="text-xs text-gray-400">{{ formatTotal(total, '家') }}</span>
        </div>
      </template>

      <el-result v-if="loadError" status="error" icon="error" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>

      <UiTable
        v-else
        :columns="columns"
        :data="shops"
        :loading="loading"
        row-key="shopId"
        empty-text="没有符合条件的店铺"
        show-actions
        actions-width="150"
      >
        <template #shop="{ row }">
          <div class="flex items-center gap-2">
            <img
              v-if="row.logo"
              :src="row.logo"
              alt=""
              class="w-8 h-8 rounded object-cover border border-gray-200"
            />
            <div class="min-w-0">
              <p class="text-gray-900 truncate">{{ row.name }}</p>
              <p v-if="row.description" class="text-xs text-gray-400 truncate">
                {{ row.description }}
              </p>
            </div>
          </div>
        </template>

        <template #applyStatus="{ row }">
          <el-tag :type="applyStatusTag(row.applyStatus)" size="small" data-testid="admin-shop-apply-tag">
            {{ applyStatusText(row.applyStatus) }}
          </el-tag>
        </template>

        <template #status="{ row }">
          <el-tag :type="shopStatusTag(row.status)" size="small">
            {{ shopStatusText(row.status) }}
          </el-tag>
        </template>

        <template #deposit="{ row }">
          <span class="text-xs text-gray-500" data-testid="admin-shop-deposit">{{ depositText(row.depositAmount) }}</span>
        </template>

        <template #actions="{ row }">
          <div class="flex items-center justify-end gap-1">
            <el-button
              v-if="row.applyStatus === 'PENDING'"
              link
              size="small"
              type="primary"
              data-testid="admin-shop-audit"
              @click="openAudit(row)"
            >
              审核
            </el-button>
            <el-button
              link
              size="small"
              :type="row.status === 'SUSPENDED' ? 'primary' : 'danger'"
              :loading="operating === row.shopId"
              :disabled="!!operating && operating !== row.shopId"
              @click="toggleStatus(row)"
            >
              {{ row.status === 'SUSPENDED' ? '恢复营业' : '停用店铺' }}
            </el-button>
          </div>
        </template>

        <template #pagination>
          <el-pagination
            :current-page="page"
            :page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </template>
      </UiTable>
    </el-card>

    <el-dialog
      v-model="auditVisible"
      :title="`入驻审核：${auditTarget?.name || ''}`"
      width="520px"
      data-testid="admin-shop-audit-dialog"
    >
      <el-descriptions v-if="auditTarget" :column="1" border size="small" class="mb-4">
        <el-descriptions-item label="卖家 ID">{{ auditTarget.sellerId }}</el-descriptions-item>
        <el-descriptions-item label="主体类型">{{ subjectTypeText(auditTarget.subjectType) }}</el-descriptions-item>
        <el-descriptions-item label="证照编号">{{ auditTarget.licenseNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ auditTarget.contactPhone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="在线联系方式">{{ auditTarget.contactWechat || '—' }}</el-descriptions-item>
        <el-descriptions-item label="资质材料">
          <div v-if="auditTarget.materials?.length" class="flex flex-wrap gap-1">
            <a
              v-for="(url, index) in auditTarget.materials"
              :key="`${url}-${index}`"
              :href="url"
              target="_blank"
              rel="noopener"
              class="text-xs text-blue-600 underline"
              data-testid="admin-shop-material"
            >
              材料 {{ index + 1 }}
            </a>
          </div>
          <span v-else class="text-xs text-gray-400">未上传</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="108px">
        <el-form-item label="审核结论">
          <el-radio-group v-model="auditForm.decision" data-testid="admin-shop-audit-decision">
            <el-radio value="APPROVED">通过</el-radio>
            <el-radio value="REJECTED">驳回</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="auditForm.decision === 'APPROVED'" label="保证金（元）">
          <el-input
            v-model="auditForm.depositYuan"
            placeholder="0 表示已要求但尚未缴纳"
            data-testid="admin-shop-audit-deposit"
          />
          <p class="text-xs text-gray-400">
            提交为 {{ depositCents }} 分；保证金仅作记录，审核通过即恢复营业，不拦截商品发布。
          </p>
        </el-form-item>

        <el-form-item v-else label="驳回理由">
          <el-input
            v-model="auditForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="必填，卖家会在店铺页看到"
            data-testid="admin-shop-audit-reason"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button data-testid="admin-shop-audit-cancel" @click="auditVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="auditing"
          data-testid="admin-shop-audit-submit"
          @click="submitAudit"
        >
          提交审核
        </el-button>
      </template>
    </el-dialog>

    <p class="text-xs text-gray-400">
      停用店铺会同步把该卖家的在售商品置为「店铺停用」，买家不可见、不可下单；恢复营业后自动重新上架。
      入驻审核通过后才会开放卖家的资料维护与商品发布；保证金仅作登记，不参与发布拦截。
    </p>
  </div>
</template>
