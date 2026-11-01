<script setup>
/**
 * 标准页骨架示范（不是业务页，是给全组照抄的模板）
 *
 * 用途：四人改造自己的页面时，结构和写法直接对照这个文件。
 * 只要你的页面骨架和它一致，合并时就不会出现四种风格。
 *
 * 这个文件演示了：
 * 1. 筛选区（不加卡片）+ el-card 包表格 + UiTable 三段式结构
 * 2. 加载 / 空 / 失败 三态分离
 * 3. 写操作用 isOk 判断 + confirmAction 二次确认
 * 4. 分页用 usePagination，删除后回退空页
 * 5. 状态文案从 constants/status 取
 * 6. 列表读 data.list / data.total，不读 records
 * 7. script 六段顺序：import / state / computed / methods / lifecycle
 */
import { computed, onMounted, ref } from 'vue'
import { isOk, userApi, unwrapResult } from '../api'
import { ROLE_MAP, ROLE_TAG, statusTag, statusText } from '../constants/status'
import { confirmAction } from '../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../components/ui/message'
import UiTable from '../components/ui/UiTable.vue'
import { usePagination } from '../composables/usePagination'

/* ---------- 1. 状态 ---------- */
const list = ref([])
const loading = ref(false)
const loadError = ref('')
const submitting = ref(false)
const filters = ref({ keyword: '', role: '' })

const pagination = usePagination({ defaultSize: 10, onChange: () => fetchList() })
const { page, size, total, onPageChange, onSizeChange, afterDelete } = pagination

/* ---------- 2. 计算属性 ---------- */
const columns = computed(() => [
  { key: 'account', label: '账号', width: 160 },
  { key: 'nickname', label: '昵称', minWidth: 140 },
  { key: 'role', label: '角色', width: 110, align: 'center', slot: 'role' },
  { key: 'status', label: '状态', width: 100, align: 'center', slot: 'status' },
  { key: 'createdAt', label: '注册时间', width: 170, formatter: (row) => formatDateTime(row.createdAt) },
])

const isEmpty = computed(() => !loading.value && !loadError.value && list.value.length === 0)

/* ---------- 3. 方法 ---------- */
/** 列表请求：读接口，成功判断用 isOk */
async function fetchList() {
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: page.value, size: size.value }
    // 空字符串不传参
    if (filters.value.keyword.trim()) params.keyword = filters.value.keyword.trim()
    if (filters.value.role) params.role = filters.value.role

    const { data } = await userApi.list(params)
    if (!isOk(data)) {
      loadError.value = data?.message || '加载用户列表失败'
      list.value = []
      return
    }
    const payload = unwrapResult(data)
    // 列表永远读 list / total，兼容后端直接返回数组的情况
    list.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = payload?.total ?? list.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载用户列表失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchList()
}

function handleReset() {
  filters.value = { keyword: '', role: '' }
  page.value = 1
  fetchList()
}

/** 写操作：危险动作必须二次确认，取消直接 return */
async function handleToggleStatus(row) {
  const next = row.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE'
  const actionText = next === 'FROZEN' ? '冻结' : '恢复正常'

  const ok = await confirmAction({
    message: `确定${actionText}账号「${row.account}」？`,
    danger: next === 'FROZEN',
  })
  if (!ok) return

  if (submitting.value) return
  submitting.value = true
  try {
    const { data } = await userApi.updateStatus(row.userId, { status: next })
    if (!isOk(data)) {
      showError(data?.message || `${actionText}失败`)
      return
    }
    showSuccess(`${actionText}成功`)
    // 删除/状态变化后若当前页空了，自动回退
    const remaining = Math.max(0, total.value - (next === 'FROZEN' ? 0 : 0))
    afterDelete(remaining)
    await fetchList()
  } catch (err) {
    showError(resolveErrorMessage(err, `${actionText}失败`))
  } finally {
    submitting.value = false
  }
}

/** 金额：后端是分 */
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
    <!-- 筛选区：不加卡片 -->
    <el-form inline class="bg-white p-4 rounded-lg">
      <el-form-item label="关键词">
        <el-input
          v-model="filters.keyword"
          placeholder="账号或昵称"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="filters.role" placeholder="全部" clearable style="width: 140px">
          <el-option label="买家" value="BUYER" />
          <el-option label="卖家" value="SELLER" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 主内容：卡片包表格 -->
    <el-card shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-base font-medium">用户列表</span>
          <span class="text-xs text-gray-400">共 {{ total }} 条</span>
        </div>
      </template>

      <!-- 失败态：与空态严格区分，可重试 -->
      <el-result v-if="loadError" status="error" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" @click="fetchList">重试</el-button>
        </template>
      </el-result>

      <!-- 正常态 -->
      <UiTable
        v-else
        :columns="columns"
        :data="list"
        :loading="loading"
        row-key="userId"
        show-actions
        actions-width="120"
      >
        <template #role="{ row }">
          <el-tag :type="statusTag(ROLE_TAG, row.role)" size="small">
            {{ statusText(ROLE_MAP, row.role) }}
          </el-tag>
        </template>

        <template #status="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
            {{ row.status === 'ACTIVE' ? '正常' : '已冻结' }}
          </el-tag>
        </template>

        <template #actions="{ row }">
          <el-button
            link
            size="small"
            :type="row.status === 'ACTIVE' ? 'danger' : 'primary'"
            :loading="submitting"
            @click="handleToggleStatus(row)"
          >
            {{ row.status === 'ACTIVE' ? '冻结' : '恢复' }}
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
  </div>
</template>
