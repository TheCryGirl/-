<script setup>
import { computed, onMounted, ref } from 'vue'
import { isOk, ticketApi, unwrapResult } from '../../api'
import PageHero from '../../components/store/PageHero.vue'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import { formatTime } from '../../utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const status = ref('')
const loading = ref(true)
const error = ref('')

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const replyContent = ref('')
const replying = ref(false)
const nextStatus = ref('')

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'OPEN', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已关闭' },
]
const statusLabel = Object.fromEntries(statusOptions.filter((o) => o.value).map((o) => [o.value, o.label]))
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const closed = computed(() => detail.value?.ticket?.status === 'CLOSED')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: page.value, size }
    if (status.value) params.status = status.value
    const { data } = await ticketApi.list(params)
    if (!isOk(data)) {
      error.value = data?.message || '加载工单失败'
      list.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    list.value = payload?.list || []
    total.value = payload?.total || 0
  } catch (err) {
    error.value = resolveErrorMessage(err, '加载工单失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  replyContent.value = ''
  try {
    const { data } = await ticketApi.detail(row.ticketId)
    if (!isOk(data)) {
      showError(data?.message || '加载失败')
      return
    }
    detail.value = unwrapResult(data)
    nextStatus.value = detail.value.ticket.status
  } catch (err) {
    showError(resolveErrorMessage(err, '加载失败'))
  } finally {
    detailLoading.value = false
  }
}

async function reply() {
  if (replying.value || !detail.value) return
  const content = replyContent.value.trim()
  if (!content) {
    showError('请填写回复内容')
    return
  }
  replying.value = true
  try {
    const { data } = await ticketApi.reply(detail.value.ticket.ticketId, { content })
    if (!isOk(data)) {
      showError(data?.message || '回复失败')
      return
    }
    detail.value = unwrapResult(data)
    replyContent.value = ''
    showSuccess('回复成功')
    load()
  } catch (err) {
    showError(resolveErrorMessage(err, '回复失败'))
  } finally {
    replying.value = false
  }
}

async function saveStatus() {
  if (!detail.value || !nextStatus.value) return
  try {
    const { data } = await ticketApi.updateStatus(detail.value.ticket.ticketId, nextStatus.value)
    if (!isOk(data)) {
      showError(data?.message || '状态更新失败')
      return
    }
    detail.value = unwrapResult(data)
    showSuccess('状态已更新')
    load()
  } catch (err) {
    showError(resolveErrorMessage(err, '状态更新失败'))
  }
}

function goPage(next) {
  page.value = Math.min(totalPages.value, Math.max(1, next))
  load()
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <PageHero title="客服工单" subtitle="查看用户提交的咨询与投诉，回复并推进处理状态" icon="headset" tone="indigo">
      <template #meta>
        <span class="text-xs text-[var(--jd-ink-4)]">共 {{ total }} 个工单</span>
      </template>
    </PageHero>

    <el-card shadow="never">
      <div class="flex flex-wrap items-center gap-2 mb-3">
        <el-select v-model="status" style="width: 150px" @change="page = 1; load()">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>

      <div v-if="loading" class="py-10 text-center text-sm text-gray-400">加载中…</div>
      <el-result v-else-if="error" status="error" title="加载失败" :sub-title="error" />
      <el-empty v-else-if="!list.length" description="暂无工单" />

      <el-table v-else :data="list" size="small" @row-click="openDetail">
        <el-table-column prop="ticketNo" label="工单号" width="190" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'CLOSED' ? 'info' : row.status === 'RESOLVED' ? 'success' : 'warning'">
              {{ statusLabel[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户" width="150" show-overflow-tooltip />
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div v-if="totalPages > 1" class="flex justify-center pt-3">
        <el-pagination background layout="prev, pager, next" :current-page="page" :page-size="size" :total="total" @current-change="goPage" />
      </div>
    </el-card>

    <el-dialog v-model="detailOpen" title="工单详情" width="680px">
      <div v-if="detailLoading" class="py-8 text-center text-sm text-gray-400">加载中…</div>
      <template v-else-if="detail">
        <h3 class="text-base font-semibold text-gray-900">{{ detail.ticket.title }}</h3>
        <p class="text-xs text-gray-400 mt-1">
          {{ detail.ticket.ticketNo }} · 用户 {{ detail.ticket.userId }} · {{ formatTime(detail.ticket.createdAt) }}
        </p>

        <div class="mt-3 space-y-2 max-h-64 overflow-y-auto">
          <div
            v-for="msg in detail.messages"
            :key="msg.id"
            class="p-2 rounded-lg text-sm"
            :class="msg.senderRole === 'ADMIN' ? 'bg-blue-50 text-blue-900' : 'bg-gray-50 text-gray-800'"
          >
            <p class="text-xs text-gray-400 mb-1">
              {{ msg.senderRole === 'ADMIN' ? '客服' : '用户' }} · {{ formatTime(msg.createdAt) }}
            </p>
            <p class="whitespace-pre-line">{{ msg.content }}</p>
          </div>
          <p v-if="!detail.messages.length" class="text-sm text-gray-400">暂无对话</p>
        </div>

        <div class="mt-3 flex flex-wrap items-center gap-2">
          <el-select v-model="nextStatus" style="width: 150px">
            <el-option v-for="opt in statusOptions.filter((o) => o.value)" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-button @click="saveStatus">更新状态</el-button>
          <el-tag v-if="closed" type="info">工单已关闭</el-tag>
        </div>

        <div v-if="!closed" class="mt-3 flex gap-2">
          <el-input v-model="replyContent" type="textarea" :rows="2" maxlength="1000" placeholder="回复用户" />
          <el-button type="primary" :loading="replying" @click="reply">回复</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>