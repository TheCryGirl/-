<script setup>
import { onMounted, ref } from 'vue'
import { isOk, shopApi, unwrapResult } from '../../api'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import UiTable from '../../components/ui/UiTable.vue'
import { usePagination } from '../../composables/usePagination'
import { formatTime, formatTotal } from '../../utils/format'

/* ---------- 1. 状态 ---------- */
const shops = ref([])
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
const statusFilter = ref('')
/** 行级忙状态：只锁当前操作的店铺 */
const operating = ref('')

const pagination = usePagination({ defaultSize: 10, onChange: () => load() })
const { page, size, total, onPageChange, onSizeChange } = pagination

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'ACTIVE', label: '正常营业' },
  { value: 'SUSPENDED', label: '已停用' },
]

/* ---------- 2. 计算属性 ---------- */
const columns = [
  { key: 'name', label: '店铺', minWidth: 240, slot: 'shop', tooltip: false },
  { key: 'sellerId', label: '卖家 ID', minWidth: 160 },
  { key: 'status', label: '营业状态', width: 120, align: 'center', slot: 'status' },
  { key: 'updatedAt', label: '更新时间', width: 180, formatter: (row) => formatTime(row.updatedAt) },
]

/* ---------- 3. 方法 ---------- */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: page.value, size: size.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (statusFilter.value) params.status = statusFilter.value

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
  page.value = 1
  load()
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
        actions-width="110"
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

        <template #status="{ row }">
          <el-tag :type="shopStatusTag(row.status)" size="small">
            {{ shopStatusText(row.status) }}
          </el-tag>
        </template>

        <template #actions="{ row }">
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

    <p class="text-xs text-gray-400">
      停用店铺会同步把该卖家的在售商品置为「店铺停用」，买家不可见、不可下单；恢复营业后自动重新上架。
    </p>
  </div>
</template>
