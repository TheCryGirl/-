<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { isOk, ticketApi, unwrapResult } from '../api'
import PageHero from '../components/store/PageHero.vue'
import { resolveErrorMessage, showError, showSuccess } from '../components/ui/message'
import { formatTime } from '../utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('')
const loading = ref(true)
const error = ref('')

const createOpen = ref(false)
const creating = ref(false)
const form = reactive({ type: 'ORDER', title: '', bizId: '', content: '' })

const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref(null)
const replyContent = ref('')
const replying = ref(false)

const typeOptions = [
  { value: 'ORDER', label: '订单问题' },
  { value: 'REFUND', label: '退款售后' },
  { value: 'TECH', label: '技术支持' },
  { value: 'OTHER', label: '其他问题' },
]
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'OPEN', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已关闭' },
]
const statusLabel = Object.fromEntries(statusOptions.filter((o) => o.value).map((o) => [o.value, o.label]))
const closed = computed(() => detail.value?.ticket?.status === 'CLOSED')
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

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

async function createTicket() {
  if (creating.value) return
  if (!form.title.trim()) {
    showError('请填写工单标题')
    return
  }
  creating.value = true
  try {
    const { data } = await ticketApi.create({
      type: form.type,
      title: form.title.trim(),
      bizId: form.bizId.trim() || undefined,
      content: form.content.trim() || undefined,
    })
    if (!isOk(data)) {
      showError(data?.message || '提交失败')
      return
    }
    showSuccess('工单已提交，客服会尽快处理')
    createOpen.value = false
    form.title = ''
    form.bizId = ''
    form.content = ''
    load()
  } catch (err) {
    showError(resolveErrorMessage(err, '提交失败'))
  } finally {
    creating.value = false
  }
}

async function openDetail(item) {
  detailOpen.value = true
  detailError.value = ''
  detail.value = null
  replyContent.value = ''
  detailLoading.value = true
  try {
    const { data } = await ticketApi.detail(item.ticketId)
    if (!isOk(data)) {
      detailError.value = data?.message || '加载工单失败'
      return
    }
    detail.value = unwrapResult(data)
  } catch (err) {
    detailError.value = resolveErrorMessage(err, '加载工单失败')
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

function goPage(next) {
  page.value = Math.min(totalPages.value, Math.max(1, next))
  load()
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <PageHero title="我的工单" subtitle="向平台客服反馈订单、退款或技术问题，并跟踪处理进度" icon="headset" tone="amber">
      <template #meta>
        <span class="text-xs text-[var(--jd-ink-4)]">共 {{ total }} 个工单</span>
      </template>
      <template #actions>
        <el-button type="primary" @click="createOpen = true">新建工单</el-button>
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
      <el-empty v-else-if="!list.length" description="还没有工单">
        <el-button type="primary" @click="createOpen = true">新建工单</el-button>
      </el-empty>

      <ul v-else class="divide-y divide-gray-100">
        <li
          v-for="item in list"
          :key="item.ticketId"
          class="py-3 flex items-center gap-3 cursor-pointer"
          @click="openDetail(item)"
        >
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-gray-900 truncate">{{ item.title }}</p>
            <p class="text-xs text-gray-400 mt-1">
              {{ item.ticketNo }} · {{ formatTime(item.createdAt) }}
              <span v-if="item.bizId">· 关联 {{ item.bizId }}</span>
            </p>
          </div>
          <el-tag size="small" :type="item.status === 'CLOSED' ? 'info' : item.status === 'RESOLVED' ? 'success' : 'warning'">
            {{ statusLabel[item.status] || item.status }}
          </el-tag>
        </li>
      </ul>

      <div v-if="totalPages > 1" class="flex justify-center pt-3">
        <el-pagination background layout="prev, pager, next" :current-page="page" :page-size="size" :total="total" @current-change="goPage" />
      </div>
    </el-card>

    <el-dialog v-model="createOpen" title="新建工单" width="520px">
      <el-form label-position="top">
        <el-form-item label="问题类型">
          <el-select v-model="form.type" class="w-full">
            <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="100" placeholder="一句话描述问题" />
        </el-form-item>
        <el-form-item label="关联订单/退款号（选填）">
          <el-input v-model="form.bizId" maxlength="32" placeholder="例如订单号 O2026..." />
        </el-form-item>
        <el-form-item label="问题描述（选填）">
          <el-input v-model="form.content" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createTicket">提交工单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailOpen" title="工单详情" width="640px">
      <div v-if="detailLoading" class="py-8 text-center text-sm text-gray-400">加载中…</div>
      <el-result v-else-if="detailError" status="error" title="加载失败" :sub-title="detailError" />
      <template v-else-if="detail">
        <h3 class="text-base font-semibold text-gray-900">{{ detail.ticket.title }}</h3>
        <p class="text-xs text-gray-400 mt-1">
          {{ detail.ticket.ticketNo }} · {{ statusLabel[detail.ticket.status] || detail.ticket.status }}
        </p>
        <div class="mt-3 space-y-2 max-h-72 overflow-y-auto">
          <div
            v-for="msg in detail.messages"
            :key="msg.id"
            class="p-2 rounded-lg text-sm"
            :class="msg.senderRole === 'ADMIN' ? 'bg-blue-50 text-blue-900' : 'bg-gray-50 text-gray-800'"
          >
            <p class="text-xs text-gray-400 mb-1">
              {{ msg.senderRole === 'ADMIN' ? '平台客服' : '我' }} · {{ formatTime(msg.createdAt) }}
            </p>
            <p class="whitespace-pre-line">{{ msg.content }}</p>
          </div>
          <p v-if="!detail.messages.length" class="text-sm text-gray-400">暂无对话</p>
        </div>
        <el-alert v-if="closed" class="mt-3" type="info" :closable="false" show-icon title="工单已关闭，不能再回复" />
        <div v-else class="mt-3 flex gap-2">
          <el-input v-model="replyContent" type="textarea" :rows="2" maxlength="1000" placeholder="补充说明或回复客服" />
          <el-button type="primary" :loading="replying" @click="reply">回复</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>