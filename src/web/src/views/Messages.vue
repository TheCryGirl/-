<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isOk, messageApi, productApi, unwrapResult } from '../api'
import { currentUserId, isAdmin } from '../stores/auth'
import { resolveErrorMessage } from '../components/ui/message'
import { usePagination } from '../composables/usePagination'
import { formatPrice, formatTime } from '../utils/format'
import PageHero from '../components/store/PageHero.vue'
import StoreIcon from '../components/store/StoreIcon.vue'

const route = useRoute()
const router = useRouter()

/* ---------- 1. 状态 ---------- */
const conversations = ref([])
const conversationLoading = ref(true)
const conversationError = ref('')
const { page: conversationPage, size: conversationSize, total: conversationTotal, onPageChange: changeConversationPage } = usePagination({
  defaultSize: 20,
  onChange: () => loadConversations(),
})
const messages = ref([])
const messageLoading = ref(false)
const messageError = ref('')
const readError = ref('')
const sendError = ref('')
const draft = ref('')
const sending = ref(false)
const products = ref({})
const { page: messagePage, size: messageSize, total: messageTotal, onPageChange: changeMessagePage } = usePagination({
  defaultSize: 30,
  onChange: () => loadMessages(),
})
const drafts = new Map()
const readRevisions = new Map()
let previousTarget = ''
let conversationRequest = 0
let messageRequest = 0
let readRevision = 0
let disposed = false

/* ---------- 2. 计算属性 ---------- */
const conversationId = computed(() => queryText(route.query.conversationId))
const sellerId = computed(() => queryText(route.query.sellerId))
const contextProductId = computed(() => queryText(route.query.contextProductId))
const targetKey = computed(() => [conversationId.value, sellerId.value, contextProductId.value].join('|'))
const selectedConversation = computed(() => conversations.value.find((item) => item.conversationId === conversationId.value))
const hasTarget = computed(() => Boolean(conversationId.value || sellerId.value))
const recipientName = computed(() => selectedConversation.value
  ? counterpart(selectedConversation.value)
  : sellerId.value ? '商品卖家' : conversationId.value ? '当前会话' : '选择一个会话')
const canCompose = computed(() => hasTarget.value && !isAdmin.value && !messageLoading.value && !messageError.value)
const canSend = computed(() => canCompose.value && !sending.value && Boolean(draft.value.trim()))

/* ---------- 3. 方法 ---------- */
function queryText(value) {
  return typeof value === 'string' ? value : ''
}

function counterpart(item) {
  return item.buyerId === currentUserId.value
    ? item.sellerName || '卖家'
    : item.buyerName || '买家'
}

function unreadCount(item) {
  return Number(item.unreadCount ?? (item.buyerId === currentUserId.value ? item.buyerUnread : item.sellerUnread)) || 0
}

function isCurrent(request, id) {
  return !disposed && request === messageRequest && id === conversationId.value
}

function isSameSendTarget(origin, sentConversationId = '') {
  if (targetKey.value === origin.key) return true
  if (origin.conversationId) return conversationId.value === origin.conversationId
  if (origin.sellerId && contextProductId.value === origin.contextProductId) {
    if (!conversationId.value) return sellerId.value === origin.sellerId
    if (sentConversationId && conversationId.value === sentConversationId) return true
    const selected = conversations.value.find((item) => item.conversationId === conversationId.value)
    return selected?.buyerId === currentUserId.value && selected?.sellerId === origin.sellerId
  }
  return false
}

async function loadConversations() {
  const request = ++conversationRequest
  const startedReadRevision = readRevision
  conversationLoading.value = true
  conversationError.value = ''
  try {
    const { data } = await messageApi.conversations({ page: conversationPage.value, size: conversationSize.value })
    if (disposed || request !== conversationRequest) return
    const payload = unwrapResult(data)
    if (!isOk(data) || !Array.isArray(payload?.list)) throw new Error(data?.message || '加载会话失败')
    // 列表请求早于本页已读成功时，不能用旧快照恢复角标。
    conversations.value = payload.list.map((item) => (readRevisions.get(item.conversationId) || 0) > startedReadRevision
      ? withoutUnread(item)
      : item)
    conversationTotal.value = payload.total || 0
    // 从商品发起咨询时，优先打开当前列表中已有的双方会话。
    const existing = !conversationId.value && sellerId.value && payload.list.find((item) =>
      item.buyerId === currentUserId.value && item.sellerId === sellerId.value)
    if (existing) {
      const matchedKey = [existing.conversationId, sellerId.value, contextProductId.value].join('|')
      drafts.set(matchedKey, draft.value)
      await router.replace({ path: route.path, query: { ...route.query, conversationId: existing.conversationId } })
    }
  } catch (err) {
    if (disposed || request !== conversationRequest) return
    conversationError.value = resolveErrorMessage(err, '加载会话失败')
    conversations.value = []
    conversationTotal.value = 0
  } finally {
    if (!disposed && request === conversationRequest) conversationLoading.value = false
  }
}

async function loadProductSummaries(ids) {
  const missing = [...new Set(ids.filter(Boolean))].filter((id) => !products.value[id])
  if (!missing.length) return
  try {
    const { data } = await productApi.batch(missing)
    const payload = unwrapResult(data)
    if (disposed || !Array.isArray(payload)) return
    const next = { ...products.value }
    for (const product of payload) next[product.productId] = product
    products.value = next
  } catch {
    // 商品摘要失败不影响文字阅读，界面仍保留关联商品编号。
  }
}

async function loadMessages(goToLatest = false) {
  const id = conversationId.value
  const request = ++messageRequest
  if (!id) return
  messageLoading.value = true
  messageError.value = ''
  readError.value = ''
  try {
    let response = await messageApi.list(id, { page: messagePage.value, size: messageSize.value })
    if (!isCurrent(request, id)) return
    let payload = unwrapResult(response.data)
    if (!isOk(response.data) || !Array.isArray(payload?.list)) throw new Error(response.data?.message || '加载消息失败')
    const lastPage = Math.max(1, Math.ceil((payload.total || 0) / messageSize.value))
    if (goToLatest === true && messagePage.value !== lastPage) {
      messagePage.value = lastPage
      response = await messageApi.list(id, { page: lastPage, size: messageSize.value })
      if (!isCurrent(request, id)) return
      payload = unwrapResult(response.data)
      if (!isOk(response.data) || !Array.isArray(payload?.list)) throw new Error(response.data?.message || '加载消息失败')
    }
    // 后端已按 createdAt/messageId 正序分页，不反转当前页。
    messages.value = payload.list
    messageTotal.value = payload.total || 0
    loadProductSummaries([contextProductId.value, ...payload.list.map((item) => item.contextProductId)])
    await markRead(id, request)
  } catch (err) {
    if (!isCurrent(request, id)) return
    messageError.value = resolveErrorMessage(err, '加载消息失败')
    messages.value = []
  } finally {
    if (isCurrent(request, id)) messageLoading.value = false
  }
}

async function markRead(id = conversationId.value, request = messageRequest) {
  if (!id || !isCurrent(request, id)) return
  readError.value = ''
  try {
    const { data } = await messageApi.read(id)
    if (!isCurrent(request, id)) return
    if (!isOk(data)) throw new Error(data?.message || '更新已读状态失败')
    readRevisions.set(id, ++readRevision)
    conversations.value = conversations.value.map((item) => item.conversationId === id
      ? withoutUnread(item)
      : item)
  } catch (err) {
    if (isCurrent(request, id)) readError.value = resolveErrorMessage(err, '更新已读状态失败')
  }
}

function withoutUnread(item) {
  return { ...item, unreadCount: 0, [item.buyerId === currentUserId.value ? 'buyerUnread' : 'sellerUnread']: 0 }
}

function openConversation(item) {
  router.replace({ path: route.path, query: { conversationId: item.conversationId } })
}

function syncTarget() {
  if (previousTarget) drafts.set(previousTarget, draft.value)
  previousTarget = targetKey.value
  draft.value = drafts.get(previousTarget) || ''
  sendError.value = ''
  readError.value = ''
  messageError.value = ''
  messages.value = []
  messagePage.value = 1
  messageTotal.value = 0
  ++messageRequest
  messageLoading.value = false
  if (contextProductId.value) loadProductSummaries([contextProductId.value])
  if (conversationId.value) return loadMessages(true)
}

async function sendMessage() {
  if (sending.value || !canCompose.value) return
  const content = draft.value.trim()
  if (!content || content.length > 500) {
    sendError.value = '请输入 1–500 字的消息'
    return
  }
  const id = conversationId.value
  const productId = contextProductId.value
  const origin = {
    key: targetKey.value,
    conversationId: id,
    sellerId: sellerId.value,
    contextProductId: productId,
  }
  const payload = { content }
  if (id) payload.conversationId = id
  else payload.sellerId = sellerId.value
  if (productId) payload.contextProductId = productId
  sending.value = true
  sendError.value = ''
  try {
    const { data } = await messageApi.send(payload)
    const sent = unwrapResult(data)
    if (!isOk(data) || !sent?.conversationId) throw new Error(data?.message || '发送消息失败')
    drafts.delete(origin.key)
    if (disposed) return
    if (isSameSendTarget(origin, sent.conversationId)) {
      drafts.delete(targetKey.value)
      draft.value = ''
      if (conversationId.value !== sent.conversationId) {
        const query = { conversationId: sent.conversationId }
        if (productId) query.contextProductId = productId
        await router.replace({ path: route.path, query })
      }
      await nextTick()
      if (conversationId.value === sent.conversationId) await loadMessages(true)
    }
    conversationPage.value = 1
    await loadConversations()
  } catch (err) {
    if (!disposed && isSameSendTarget(origin)) sendError.value = resolveErrorMessage(err, '发送消息失败，请重试')
  } finally {
    if (!disposed) sending.value = false
  }
}

/* ---------- 4. 生命周期 ---------- */
watch(targetKey, syncTarget, { immediate: true })
onMounted(loadConversations)
onBeforeUnmount(() => {
  disposed = true
  ++conversationRequest
  ++messageRequest
})
</script>

<template>
  <div class="space-y-4">
    <PageHero
      :title="route.name === 'seller-messages' ? '买家消息' : '我的消息'"
      subtitle="商品咨询、细节确认，在这里继续沟通"
      icon="headset"
      tone="red"
    >
      <template #actions>
        <RouterLink :to="{ name: 'products' }" class="jd-link-btn">
          继续逛逛 <StoreIcon name="chevron-right" :size="14" />
        </RouterLink>
      </template>
    </PageHero>

    <div class="messages-layout">
      <el-card shadow="never" class="conversation-card">
        <template #header>
          <div class="flex items-center justify-between gap-3">
            <h2 class="font-medium">全部会话 <span class="text-xs text-gray-400">{{ conversationTotal }}</span></h2>
            <el-button link :disabled="conversationLoading" @click="loadConversations">刷新</el-button>
          </div>
        </template>
        <div v-if="conversationLoading" class="p-4" aria-label="正在加载会话"><el-skeleton :rows="5" animated /></div>
        <el-result v-else-if="conversationError" icon="error" title="加载会话失败" :sub-title="conversationError">
          <template #extra><el-button type="primary" @click="loadConversations">重试</el-button></template>
        </el-result>
        <el-empty v-else-if="!conversations.length" description="还没有会话，去商品页联系卖家吧" :image-size="72" />
        <ul v-else class="conversation-list" aria-label="会话列表">
          <li v-for="item in conversations" :key="item.conversationId">
            <button
              type="button"
              class="conversation-item"
              :class="{ 'is-selected': conversationId === item.conversationId }"
              :aria-current="conversationId === item.conversationId ? 'true' : undefined"
              @click="openConversation(item)"
            >
              <span class="conversation-avatar" aria-hidden="true">{{ counterpart(item).slice(0, 1) }}</span>
              <span class="conversation-copy">
                <span class="flex items-center justify-between gap-2">
                  <strong class="truncate">{{ counterpart(item) }}</strong>
                  <span v-if="unreadCount(item)" class="unread-badge" :aria-label="`${unreadCount(item)} 条未读`">{{ unreadCount(item) > 99 ? '99+' : unreadCount(item) }}</span>
                </span>
                <span class="conversation-preview">{{ item.lastPreview || '暂无消息' }}</span>
                <time class="conversation-time">{{ formatTime(item.lastMessageAt) }}</time>
              </span>
            </button>
          </li>
        </ul>
        <div v-if="!conversationLoading && !conversationError && conversationTotal > conversationSize" class="messages-pagination">
          <el-pagination
            :current-page="conversationPage" :page-size="conversationSize" :total="conversationTotal"
            small :pager-count="5" layout="prev, pager, next" @current-change="changeConversationPage"
          />
        </div>
      </el-card>

      <el-card shadow="never" class="message-card">
        <template #header>
          <div class="flex items-center justify-between gap-3">
            <h2 class="font-medium">{{ recipientName }}</h2>
            <el-button v-if="conversationId" link :disabled="messageLoading" @click="loadMessages()">刷新消息</el-button>
          </div>
        </template>
        <el-empty v-if="!hasTarget" description="选择左侧会话，或从商品页发起咨询" class="message-placeholder" />
        <template v-else>
          <div v-if="messageLoading" class="p-5" aria-label="正在加载消息"><el-skeleton :rows="7" animated /></div>
          <el-result v-else-if="messageError" icon="error" title="加载消息失败" :sub-title="messageError">
            <template #extra><el-button type="primary" @click="loadMessages()">重试</el-button></template>
          </el-result>
          <template v-else>
            <div v-if="readError" class="message-notice" role="alert">
              <span>{{ readError }}</span><el-button link type="primary" @click="markRead()">重试已读</el-button>
            </div>
            <ol v-if="messages.length" class="message-list" aria-label="消息记录">
              <li v-for="item in messages" :key="item.messageId" class="message-row" :class="{ 'is-mine': item.senderId === currentUserId }">
                <div class="message-bubble">
                  <RouterLink v-if="item.contextProductId && products[item.contextProductId]" :to="{ name: 'product-detail', params: { id: item.contextProductId } }" class="product-context">
                    <img v-if="products[item.contextProductId].mainImage" :src="products[item.contextProductId].mainImage" alt="" />
                    <span><strong>{{ products[item.contextProductId].title }}</strong><small>¥{{ formatPrice(products[item.contextProductId].price) }}</small></span>
                    <StoreIcon name="chevron-right" :size="14" />
                  </RouterLink>
                  <p v-else-if="item.contextProductId" class="product-unavailable">关联商品 {{ item.contextProductId }} · 暂无商品摘要</p>
                  <p class="message-content">{{ item.content }}</p>
                  <time class="message-time">{{ formatTime(item.createdAt) }}</time>
                </div>
              </li>
            </ol>
            <el-empty v-else :description="conversationId ? '暂无消息，发送第一条问候吧' : '想了解商品细节？发送消息向卖家咨询'" :image-size="72" class="message-placeholder" />
            <div v-if="messageTotal > messageSize" class="messages-pagination">
              <el-pagination
                :current-page="messagePage" :page-size="messageSize" :total="messageTotal"
                small :pager-count="5" layout="total, prev, pager, next" @current-change="changeMessagePage"
              />
            </div>
          </template>

          <form class="message-compose" @submit.prevent="sendMessage">
            <RouterLink v-if="contextProductId && products[contextProductId]" :to="{ name: 'product-detail', params: { id: contextProductId } }" class="product-context compose-context">
              <img v-if="products[contextProductId].mainImage" :src="products[contextProductId].mainImage" alt="" />
              <span><small>正在咨询</small><strong>{{ products[contextProductId].title }}</strong></span>
              <StoreIcon name="chevron-right" :size="14" />
            </RouterLink>
            <p v-else-if="contextProductId" class="product-unavailable mb-2">正在咨询商品 {{ contextProductId }} · 暂无商品摘要</p>
            <label for="message-draft" class="text-sm font-medium">{{ conversationId ? '回复消息' : '发送咨询' }}</label>
            <textarea
              id="message-draft" v-model="draft" rows="3" maxlength="500"
              class="message-input" placeholder="输入消息，最多 500 字"
              :disabled="!canCompose || sending" :aria-invalid="Boolean(sendError)"
              :aria-describedby="sendError ? 'message-send-error' : undefined"
              @keydown.ctrl.enter.prevent="sendMessage" @keydown.meta.enter.prevent="sendMessage"
            />
            <p v-if="sendError" id="message-send-error" class="send-error" role="alert">{{ sendError }}；草稿已保留，可再次发送。</p>
            <p v-if="isAdmin" class="send-error" role="alert">管理员不能参与买卖双方消息。</p>
            <div class="compose-actions">
              <span class="text-xs text-gray-400">{{ draft.length }}/500 · Ctrl / ⌘ + Enter 发送</span>
              <el-button type="primary" native-type="submit" :loading="sending" :disabled="!canSend">{{ sendError ? '重试发送' : '发送' }}</el-button>
            </div>
          </form>
        </template>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.messages-layout { display: grid; grid-template-columns: minmax(240px, 310px) minmax(0, 1fr); gap: 16px; align-items: start; }
.conversation-card, .message-card { min-width: 0; }
.conversation-card :deep(.el-card__body), .message-card :deep(.el-card__body) { padding: 0; }
.conversation-list { max-height: 620px; overflow-y: auto; }
.conversation-item { display: flex; align-items: flex-start; gap: 12px; width: 100%; padding: 16px; text-align: left; border-bottom: 1px solid var(--jd-line); transition: background .2s; }
.conversation-item:hover { background: var(--jd-red-light, #fff5f3); }
.conversation-item.is-selected { background: #fff5f3; box-shadow: inset 3px 0 var(--jd-red); }
.conversation-item:focus-visible, .product-context:focus-visible { outline: 2px solid var(--jd-red); outline-offset: -2px; }
.conversation-avatar { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 38px; height: 38px; border-radius: 50%; color: var(--jd-red); background: #fff0ee; font-weight: 600; }
.conversation-copy { display: block; flex: 1; min-width: 0; font-size: 14px; }
.conversation-preview { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin: 5px 0; color: var(--jd-ink-4); font-size: 13px; }
.conversation-time, .message-time { color: var(--jd-ink-4); font-size: 11px; }
.unread-badge { flex-shrink: 0; min-width: 19px; padding: 1px 5px; border-radius: 12px; background: var(--jd-red); color: white; font-size: 11px; text-align: center; }
.message-placeholder { min-height: 260px; }
.message-list { display: flex; flex-direction: column; gap: 18px; min-height: 300px; max-height: 550px; overflow-y: auto; padding: 24px; }
.message-row { display: flex; justify-content: flex-start; min-width: 0; }
.message-row.is-mine { justify-content: flex-end; }
.message-bubble { max-width: 85%; min-width: 100px; border: 1px solid var(--jd-line); border-radius: 4px 14px 14px; padding: 12px 14px; background: #f9fafb; }
.is-mine .message-bubble { background: #fff5f3; border-color: #fbd8d3; border-radius: 14px 4px 14px 14px; }
.message-content { white-space: pre-wrap; overflow-wrap: anywhere; color: var(--jd-ink); font-size: 14px; line-height: 1.7; }
.message-time { display: block; margin-top: 5px; }
.product-context { display: flex; align-items: center; gap: 10px; padding: 10px; margin-bottom: 10px; min-width: 0; border: 1px solid var(--jd-line); border-radius: 8px; background: #fff; }
.product-context img { width: 42px; height: 42px; border-radius: 4px; object-fit: cover; }
.product-context > span { flex: 1; min-width: 0; }
.product-context strong { display: block; font-size: 13px; color: var(--jd-ink); overflow-wrap: anywhere; }
.product-context small { display: block; font-size: 12px; color: var(--jd-red); }
.product-context > svg { flex-shrink: 0; }
.product-unavailable { color: var(--jd-ink-4); font-size: 12px; overflow-wrap: anywhere; }
.product-unavailable + .message-content { margin-top: 6px; }
.messages-pagination { display: flex; justify-content: center; padding: 14px 8px; border-top: 1px solid var(--jd-line); }
.message-notice { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 20px; background: #fff8ed; color: #9a5d0c; font-size: 13px; }
.message-compose { border-top: 1px solid var(--jd-line); padding: 18px 20px; }
.compose-context { max-width: 420px; }
.message-input { display: block; width: 100%; resize: vertical; min-height: 90px; margin: 8px 0; padding: 10px 12px; border: 1px solid var(--jd-line); border-radius: 8px; color: var(--jd-ink); background: #fff; font-size: 14px; line-height: 1.6; }
.message-input:focus { outline: 2px solid var(--jd-red); outline-offset: 1px; }
.message-input:disabled { background: #f9fafb; cursor: not-allowed; }
.compose-actions { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px; }
.send-error { margin: 8px 0; color: var(--jd-red); font-size: 13px; overflow-wrap: anywhere; }
@media (max-width: 760px) {
  .messages-layout { grid-template-columns: minmax(0, 1fr); }
  .conversation-list { max-height: 230px; }
  .conversation-item { padding: 12px 16px; }
  .message-list { padding: 16px; max-height: 470px; }
  .message-bubble { max-width: 94%; }
  .message-compose { padding: 16px; }
}
</style>
