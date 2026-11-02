<script setup>
import { onMounted, ref } from 'vue'
import { isOk, unwrapResult, userApi } from '../../api'
import { confirmAction } from '../../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import UiTable from '../../components/ui/UiTable.vue'
import { usePagination } from '../../composables/usePagination'
import { formatTotal } from '../../utils/format'
import { auth } from '../../stores/auth'

/* ---------- 1. 状态 ---------- */
const users = ref([])
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
/** 行级忙状态：保存正在写操作的行 ID，避免误以为整页都在加载 */
const busyId = ref('')
/** 角色下拉的本地草稿：确认前不写回 row.role，取消时回退到原值 */
const roleDraft = ref({})

const pagination = usePagination({ defaultSize: 10, onChange: () => load() })
const { page, size, total, onPageChange, onSizeChange } = pagination

const roleOptions = [
  { value: 'BUYER', label: '买家' },
  { value: 'SELLER', label: '卖家' },
  { value: 'ADMIN', label: '管理员' },
]

/* ---------- 2. 计算属性 ---------- */
const columns = [
  { key: 'account', label: '账号', width: 160 },
  { key: 'nickname', label: '昵称', minWidth: 140 },
  { key: 'role', label: '角色', width: 170, align: 'center', slot: 'role' },
  { key: 'status', label: '状态', width: 110, align: 'center', slot: 'status' },
  { key: 'createdAt', label: '注册时间', width: 190, formatter: (row) => formatTime(row.createdAt) },
]

/* ---------- 3. 方法 ---------- */
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: page.value, size: size.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()

    const { data } = await userApi.list(params)
    if (!isOk(data)) {
      loadError.value = data?.message || '加载用户列表失败'
      users.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    users.value = Array.isArray(payload) ? payload : payload?.list ?? []
    total.value = payload?.total ?? users.value.length
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载用户列表失败')
    users.value = []
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
  page.value = 1
  load()
}

function isSelf(user) {
  return user.userId === auth.user?.userId
}

/** 角色下拉当前值：优先本地草稿，取消后草稿清除即回到 row.role */
function roleValue(row) {
  return roleDraft.value[row.userId] ?? row.role
}

/** 用户状态只有 ACTIVE / FROZEN 两种，未知值不吞掉 */
function userStatusText(status) {
  if (status === 'ACTIVE') return '正常'
  if (status === 'FROZEN') return '已冻结'
  return status || '-'
}

function userStatusTag(status) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'FROZEN') return 'danger'
  return 'info'
}

async function changeRole(user, nextRole) {
  if (nextRole === user.role) return
  const label = roleOptions.find((r) => r.value === nextRole)?.label || nextRole

  // 先让下拉显示新选择，取消/失败时清掉草稿回退
  roleDraft.value = { ...roleDraft.value, [user.userId]: nextRole }
  const ok = await confirmAction({
    message: `确定把 ${user.account} 的角色改为「${label}」？对方需重新登录后生效。`,
    title: '变更角色',
  })
  if (!ok) {
    const next = { ...roleDraft.value }
    delete next[user.userId]
    roleDraft.value = next
    return
  }

  if (busyId.value) return
  busyId.value = user.userId
  try {
    const { data } = await userApi.updateRole(user.userId, nextRole)
    if (!isOk(data)) {
      showError(data?.message || '变更角色失败')
      const next = { ...roleDraft.value }
      delete next[user.userId]
      roleDraft.value = next
      return
    }
    showSuccess(`已将 ${user.account} 的角色改为「${label}」`)
    const next = { ...roleDraft.value }
    delete next[user.userId]
    roleDraft.value = next
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, '变更角色失败'))
    const next = { ...roleDraft.value }
    delete next[user.userId]
    roleDraft.value = next
  } finally {
    busyId.value = ''
  }
}

async function toggleStatus(user) {
  const next = user.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE'
  const actionText = next === 'FROZEN' ? '冻结' : '解冻'

  const ok = await confirmAction({
    message:
      next === 'FROZEN'
        ? `确定冻结账号「${user.account}」？冻结后该账号将无法登录。`
        : `确定解冻账号「${user.account}」？解冻后该账号可以正常登录。`,
    title: `${actionText}账号`,
    danger: next === 'FROZEN',
  })
  if (!ok) return

  if (busyId.value) return
  busyId.value = user.userId
  try {
    const { data } = await userApi.updateStatus(user.userId, { status: next })
    if (!isOk(data)) {
      showError(data?.message || `${actionText}失败`)
      return
    }
    showSuccess(`${actionText}成功`)
    // 冻结/解冻不改变总条数，直接刷新当前页即可（afterDelete 会再触发一次请求）
    await load()
  } catch (err) {
    showError(resolveErrorMessage(err, `${actionText}失败`))
  } finally {
    busyId.value = ''
  }
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

/* ---------- 4. 生命周期 ---------- */
onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <!-- 筛选区：不加卡片 -->
    <el-form inline class="bg-white p-4 rounded-lg" @submit.prevent>
      <el-form-item label="关键词">
        <el-input
          v-model="keyword"
          placeholder="搜索账号 / 昵称"
          clearable
          style="width: 220px"
          @keyup.enter="search"
          @clear="search"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-card shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-base font-medium">用户列表</span>
          <span v-if="!loadError" class="text-xs text-gray-400">{{ formatTotal(total, '条') }}</span>
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
        :data="users"
        :loading="loading"
        row-key="userId"
        empty-text="暂无用户"
        show-actions
        actions-width="110"
      >
        <template #role="{ row }">
          <div class="flex items-center justify-center gap-1">
            <el-select
              :model-value="roleValue(row)"
              size="small"
              style="width: 100px"
              :disabled="isSelf(row) || busyId === row.userId"
              :teleported="true"
              @change="(value) => changeRole(row, value)"
            >
              <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
            </el-select>
            <el-tag v-if="isSelf(row)" size="small" type="info">本人</el-tag>
          </div>
        </template>

        <template #status="{ row }">
          <el-tag :type="userStatusTag(row.status)" size="small">
            {{ userStatusText(row.status) }}
          </el-tag>
        </template>

        <template #actions="{ row }">
          <el-button
            link
            size="small"
            :type="row.status === 'ACTIVE' ? 'danger' : 'primary'"
            :loading="busyId === row.userId"
            :disabled="!!busyId && busyId !== row.userId"
            @click="toggleStatus(row)"
          >
            {{ row.status === 'ACTIVE' ? '冻结' : '解冻' }}
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
      角色变更后，对方需要重新登录，新的角色才会随令牌生效；管理员不能变更自己的角色。
    </p>
  </div>
</template>
