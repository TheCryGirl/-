<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { auth } from '../stores/auth'
import { messageApi } from '../api'

const route = useRoute()

const partners = ref([])
const activeId = ref('')
const messages = ref([])
const draft = ref('')
const loadingPartners = ref(true)
const loadingMessages = ref(false)
const error = ref('')
const listRef = ref(null)
let timer = null

async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function loadPartners() {
  loadingPartners.value = true
  try {
    const { data } = await messageApi.partners(auth.user.userId)
    partners.value = Array.isArray(data) ? data : []
    if (!activeId.value && partners.value.length) {
      select(partners.value[0])
    }
  } catch (err) {
    error.value = err.response?.data?.message || '会话列表加载失败'
  } finally {
    loadingPartners.value = false
  }
}

async function loadMessages() {
  if (!activeId.value) return
  loadingMessages.value = true
  try {
    const { data } = await messageApi.conversation(auth.user.userId, activeId.value)
    messages.value = Array.isArray(data) ? data : []
    scrollToBottom()
  } catch (err) {
    error.value = err.response?.data?.message || '消息加载失败'
  } finally {
    loadingMessages.value = false
  }
}

function select(p) {
  const id = p.userId ?? p.otherId ?? p.id ?? p
  if (activeId.value === id) return
  activeId.value = String(id)
  loadMessages()
}

async function send() {
  const text = draft.value.trim()
  if (!text || !activeId.value) return
  const payload = {
    senderId: auth.user.userId,
    receiverId: activeId.value,
    content: text,
  }
  draft.value = ''
  // 乐观更新，失败再回滚提示
  const temp = { messageId: `tmp-${Date.now()}`, senderId: auth.user.userId, content: text }
  messages.value.push(temp)
  scrollToBottom()
  try {
    await messageApi.send(payload)
    loadMessages()
  } catch (err) {
    messages.value = messages.value.filter((m) => m.messageId !== temp.messageId)
    error.value = err.response?.data?.message || '发送失败'
  }
}

function isMine(m) {
  return String(m.senderId ?? m.fromId ?? '') === String(auth.user.userId)
}

onMounted(() => {
  loadPartners()
  // 轮询新消息：课程作业规模下够用，后续可换 SSE
  timer = setInterval(loadMessages, 5000)

  // 从商品详情「我想要」跳过来时，可能带了目标用户
  if (route.query.with) {
    activeId.value = String(route.query.with)
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="space-y-3">
    <h1 class="text-lg font-semibold text-gray-900">消息</h1>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div class="bg-white rounded-lg border border-gray-200 grid md:grid-cols-[240px_1fr] min-h-125">
      <!-- 会话列表 -->
      <aside class="border-r border-gray-200">
        <div v-if="loadingPartners" class="p-4 text-sm text-gray-400">加载中…</div>
        <div v-else-if="!partners.length" class="p-4 text-sm text-gray-400">还没有会话</div>
        <ul v-else>
          <li v-for="p in partners" :key="p.userId ?? p.otherId ?? p">
            <button
              class="w-full text-left px-4 py-3 text-sm border-b border-gray-100 hover:bg-gray-50"
              :class="
                String(p.userId ?? p.otherId ?? p) === activeId
                  ? 'bg-blue-50 text-blue-600 font-medium'
                  : 'text-gray-700'
              "
              @click="select(p)"
            >
              {{ p.name || p.userId || p.otherId || p }}
            </button>
          </li>
        </ul>
      </aside>

      <!-- 对话区 -->
      <section class="flex flex-col">
        <div ref="listRef" class="chat-messages p-4 flex flex-col flex-1 bg-gray-50">
          <div v-if="loadingMessages" class="text-sm text-gray-400 m-auto">加载中…</div>
          <div v-else-if="!messages.length" class="text-sm text-gray-400 m-auto">
            选择左侧会话开始聊天
          </div>
          <template v-else>
            <div
              v-for="m in messages"
              :key="m.messageId ?? m.id"
              class="message-bubble text-sm"
              :class="isMine(m) ? 'message-user' : 'message-ai'"
            >
              {{ m.content }}
            </div>
          </template>
        </div>

        <div class="p-3 border-t border-gray-200 flex gap-2">
          <input
            v-model="draft"
            type="text"
            placeholder="输入消息后回车发送"
            class="flex-1 px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            :disabled="!activeId"
            @keyup.enter="send"
          />
          <button
            class="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
            :disabled="!activeId || !draft.trim()"
            @click="send"
          >
            发送
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
