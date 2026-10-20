<script setup>
import { nextTick, ref } from 'vue'
import { aiApi } from '../api'
import { auth, isLoggedIn } from '../stores/auth'

const open = ref(false)
const input = ref('')
const sending = ref(false)
const listRef = ref(null)
const messages = ref([
  { role: 'ai', content: '你好，我是平台助手。可以问我商品推荐、下单流程或售后规则。' },
])

async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return

  messages.value.push({ role: 'user', content: text })
  input.value = ''
  sending.value = true
  scrollToBottom()

  try {
    const payload = {
      userId: auth.user?.userId || 'guest',
      message: text,
      // 带上上下文，后端按需使用
      history: messages.value.slice(0, -1).map((m) => ({ role: m.role, content: m.content })),
    }
    const { data } = await aiApi.chat(payload)
    const reply =
      typeof data === 'string' ? data : (data?.content ?? data?.reply ?? data?.message ?? '')
    messages.value.push({ role: 'ai', content: reply || '（未收到回复）' })
  } catch (err) {
    const detail = err.response?.data?.message || err.message || '未知错误'
    messages.value.push({ role: 'ai', content: `出错了：${detail}` })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function toggle() {
  if (!isLoggedIn.value && !open.value) {
    messages.value.push({ role: 'ai', content: '提示：登录后可以获得更贴合你浏览记录的推荐。' })
  }
  open.value = !open.value
  if (open.value) scrollToBottom()
}
</script>

<template>
  <div class="fixed right-5 bottom-5 z-40">
    <div
      v-if="open"
      class="mb-3 w-80 sm:w-96 bg-white rounded-xl shadow-2xl border border-gray-200 flex flex-col overflow-hidden"
    >
      <div class="px-4 py-3 bg-blue-600 text-white flex items-center justify-between">
        <span class="text-sm font-medium">AI 助手</span>
        <button class="text-white/80 hover:text-white text-lg leading-none" @click="open = false">
          ×
        </button>
      </div>

      <div ref="listRef" class="chat-messages p-3 flex flex-col bg-gray-50">
        <div
          v-for="(m, i) in messages"
          :key="i"
          class="message-bubble text-sm"
          :class="m.role === 'user' ? 'message-user' : 'message-ai'"
        >
          {{ m.content }}
        </div>
        <div v-if="sending" class="message-bubble message-ai text-sm text-gray-400">正在思考…</div>
      </div>

      <div class="p-3 border-t border-gray-200 flex gap-2">
        <input
          v-model="input"
          type="text"
          placeholder="输入问题后回车"
          class="flex-1 px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          @keyup.enter="send"
        />
        <button
          class="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
          :disabled="sending || !input.trim()"
          @click="send"
        >
          发送
        </button>
      </div>
    </div>

    <button
      class="w-14 h-14 rounded-full bg-blue-600 text-white shadow-lg hover:bg-blue-700 flex items-center justify-center text-sm"
      @click="toggle"
    >
      AI
    </button>
  </div>
</template>
