<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isOk, notificationApi, unwrapResult } from '../api'
import PageHero from '../components/store/PageHero.vue'
import StoreIcon from '../components/store/StoreIcon.vue'
import { resolveErrorMessage, showError, showSuccess } from '../components/ui/message'
import { formatTime } from '../utils/format'

const router = useRouter()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const unreadOnly = ref(false)
const loading = ref(true)
const error = ref('')
const unread = ref(0)
const marking = ref('')

const typeLabel = {
  PAY_SUCCESS: '支付成功',
  SHIPPED: '订单发货',
  REFUND_DONE: '退款完成',
  AFTER_SALE: '售后进度',
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await notificationApi.list({ page: page.value, size, unreadOnly: unreadOnly.value })
    if (!isOk(data)) {
      error.value = data?.message || '加载消息失败'
      list.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    list.value = payload?.list || []
    total.value = payload?.total || 0
  } catch (err) {
    error.value = resolveErrorMessage(err, '加载消息失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadUnread() {
  try {
    const { data } = await notificationApi.unreadCount()
    if (isOk(data)) unread.value = Number(unwrapResult(data)?.count || 0)
  } catch {
    // 未读数失败不影响列表
  }
}

function targetRoute(item) {
  if (item.bizType === 'ORDER' && item.bizId) return { name: 'order-detail', params: { id: item.bizId } }
  if (item.bizType === 'REFUND' && item.bizId) return { name: 'order-detail', params: { id: item.bizId } }
  if (item.bizType === 'AFTER_SALE') return { name: 'after-sales' }
  return null
}

async function openItem(item) {
  if (!item.read) {
    try {
      await notificationApi.read(item.notificationId)
      item.read = true
      unread.value = Math.max(0, unread.value - 1)
      window.dispatchEvent(new Event('notifications:updated'))
    } catch {
      // 已读失败不阻断跳转
    }
  }
  const target = targetRoute(item)
  if (target) router.push(target)
}

async function markAll() {
  if (marking.value) return
  marking.value = 'all'
  try {
    const { data } = await notificationApi.readAll()
    if (!isOk(data)) {
      showError(data?.message || '操作失败')
      return
    }
    showSuccess('已全部标记为已读')
    unread.value = 0
    window.dispatchEvent(new Event('notifications:updated'))
    load()
  } catch (err) {
    showError(resolveErrorMessage(err, '操作失败'))
  } finally {
    marking.value = ''
  }
}

function toggleUnreadOnly() {
  unreadOnly.value = !unreadOnly.value
  page.value = 1
  load()
}

function goPage(next) {
  page.value = Math.min(totalPages.value, Math.max(1, next))
  load()
}

onMounted(() => {
  load()
  loadUnread()
})
</script>

<template>
  <div class="space-y-4">
    <PageHero
      title="消息中心"
      subtitle="支付、发货、退款与售后进度都会在这里通知你"
      icon="bell"
      tone="indigo"
    >
      <template #meta>
        <span class="text-xs text-[var(--jd-ink-4)]">
          {{ unread > 0 ? `${unread} 条未读` : '没有未读消息' }}
        </span>
      </template>
      <template #actions>
        <el-button size="small" :loading="marking === 'all'" @click="markAll">全部已读</el-button>
        <el-button size="small" :type="unreadOnly ? 'primary' : ''" plain @click="toggleUnreadOnly">
          {{ unreadOnly ? '只看未读' : '全部消息' }}
        </el-button>
      </template>
    </PageHero>

    <el-card shadow="never">
      <div v-if="loading" class="py-10 text-center text-sm text-gray-400">加载中…</div>
      <el-result v-else-if="error" status="error" title="加载消息失败" :sub-title="error">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>
      <el-empty v-else-if="!list.length" :description="unreadOnly ? '没有未读消息' : '还没有收到消息'" />

      <ul v-else class="divide-y divide-gray-100">
        <li
          v-for="item in list"
          :key="item.notificationId"
          class="flex items-start gap-3 py-3 cursor-pointer"
          :class="item.read ? 'opacity-70' : ''"
          @click="openItem(item)"
        >
          <span
            class="mt-1 grid place-items-center w-8 h-8 rounded-full shrink-0"
            :class="item.read ? 'bg-gray-100 text-gray-400' : 'bg-red-50 text-red-600'"
          >
            <StoreIcon name="bell" :size="15" />
          </span>
          <div class="flex-1 min-w-0">
            <div class="flex flex-wrap items-center gap-2">
              <span class="text-sm font-medium text-gray-900">{{ item.title }}</span>
              <el-tag size="small" effect="plain">{{ typeLabel[item.type] || item.type }}</el-tag>
              <span v-if="!item.read" class="w-1.5 h-1.5 rounded-full bg-red-500"></span>
            </div>
            <p class="text-sm text-gray-600 mt-1">{{ item.content }}</p>
            <p class="text-xs text-gray-400 mt-1">{{ formatTime(item.createdAt) }}</p>
          </div>
        </li>
      </ul>

      <div v-if="totalPages > 1" class="flex justify-center pt-3">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          :pager-count="5"
          @current-change="goPage"
        />
      </div>
    </el-card>
  </div>
</template>