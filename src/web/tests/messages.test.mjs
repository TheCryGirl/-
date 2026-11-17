import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import vm from 'node:vm'

const source = (path) => readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8')
const script = (path) => source(path).match(/<script setup>([\s\S]*?)<\/script>/)[1]
  .replace(/^import[\s\S]*? from ['"][^'"\n]+['"]\r?$/gm, '')
const ref = (value) => ({ value })
const computed = (fn) => ({ get value() { return fn() } })
const ok = (data) => ({ data: { code: 0, data } })
const page = (list = [], total = list.length) => ok({ list, total, page: 1, size: 30 })
const deferred = () => { let resolve; const promise = new Promise((r) => { resolve = r }); return { promise, resolve } }

function messages(query = {}, api = {}) {
  assert.ok(existsSync(new URL('../src/views/Messages.vue', import.meta.url)), '共用消息页尚未实现')
  const route = { query, path: '/messages', name: 'messages' }
  const calls = { reads: [], sends: [], products: [], routes: [] }
  const ctx = {
    ref, computed, reactive: (v) => v, watch: () => {}, onMounted: () => {}, onBeforeUnmount: () => {}, nextTick: async () => {},
    useRoute: () => route,
    useRouter: () => ({ replace: async (to) => { calls.routes.push(to); route.query = to.query } }),
    currentUserId: ref('buyer-1'), isAdmin: ref(false),
    isOk: (v) => v?.code === 0, unwrapResult: (v) => v?.code === 0 ? v.data : null,
    resolveErrorMessage: (e, fallback) => e?.response?.data?.message || e?.message || fallback,
    messageApi: {
      conversations: async () => page(), list: async () => page(),
      read: async (id) => { calls.reads.push(id); return ok(null) },
      send: async (payload) => { calls.sends.push(payload); return ok({ messageId: 'm1', conversationId: 'c1', content: payload.content }) },
      ...api,
    },
    productApi: { batch: async (ids) => { calls.products.push(ids); return ok([]) } },
    window: { dispatchEvent: () => {} }, Event: class {},
  }
  vm.createContext(ctx)
  vm.runInContext(source('composables/usePagination.js').replace(/^import .*$/gm, '')
    .replace(/export function /g, 'function ').replace(/^export default .*$/gm, ''), ctx)
  vm.runInContext(script('views/Messages.vue') + '\nthis.view={loadConversations,loadMessages,sendMessage,syncTarget,markRead,conversations,conversationError,conversationLoading,messages,messageError,messageLoading,messagePage,messageTotal,readError,sendError,draft,sending,recipientName};', ctx)
  return { view: ctx.view, route, calls, ctx }
}

test('message routes share one page and retain authentication and seller restrictions', () => {
  let config
  const ctx = { createWebHistory: () => ({}), createRouter: (v) => { config = v; return { beforeEach: () => {} } } }
  vm.createContext(ctx)
  vm.runInContext(source('router/index.js').replace(/^import .*$/gm, '').replace(/^export default .*$/gm, ''), ctx)
  const buyer = config.routes.find((r) => r.path === '/messages')
  const seller = config.routes.find((r) => r.path === '/seller/messages')
  assert.ok(buyer, '买家消息入口缺失'); assert.ok(seller, '卖家消息入口缺失')
  assert.equal(buyer.meta.requiresAuth, true); assert.equal(seller.meta.role, 'SELLER')
  assert.equal(String(buyer.component), String(seller.component))
})

test('contact seller preserves the consultation destination through login and blocks own products', () => {
  const routes = [], redirects = []
  const ctx = {
    ref, computed, watch: () => {}, onMounted: () => {},
    useRoute: () => ({ params: { id: 'p1' }, fullPath: '/products/p1' }),
    useRouter: () => ({ push: (to) => routes.push(to), resolve: (to) => ({ fullPath: `/messages?sellerId=${to.query.sellerId}&contextProductId=${to.query.contextProductId}` }) }),
    currentUserId: ref('buyer-1'), isLoggedIn: ref(false),
    sessionStorage: { setItem: (k, v) => redirects.push([k, v]) },
  }
  vm.createContext(ctx)
  vm.runInContext(script('views/ProductDetail.vue') + '\nthis.view={product,contactSeller,isOwnProduct};', ctx)
  ctx.view.product.value = { productId: 'p1', sellerId: 'seller-1' }
  ctx.view.contactSeller()
  assert.equal(routes[0].name, 'login')
  assert.deepEqual(redirects[0], ['redirectAfterLogin', '/messages?sellerId=seller-1&contextProductId=p1'])
  ctx.isLoggedIn.value = true
  ctx.view.contactSeller()
  assert.equal(routes[1].name, 'messages'); assert.equal(routes[1].query.contextProductId, 'p1')
  ctx.currentUserId.value = 'seller-1'
  ctx.view.contactSeller()
  assert.equal(ctx.view.isOwnProduct.value, true); assert.equal(routes.length, 2)
})

test('conversation loading distinguishes a permission error from an empty list and retries', async () => {
  let attempt = 0
  const { view } = messages({}, { conversations: async () => ++attempt === 1 ? { data: { code: 10003, message: '无权访问消息' } } : page() })
  const first = view.loadConversations()
  assert.equal(view.conversationLoading.value, true)
  await first
  assert.equal(view.conversationError.value, '无权访问消息'); assert.equal(view.conversationLoading.value, false)
  await view.loadConversations()
  assert.equal(view.conversationError.value, ''); assert.equal(view.conversations.value.length, 0)
})

test('switching conversations ignores old responses and marks only the opened response read', async () => {
  const old = deferred(), current = deferred()
  const { view, route, calls } = messages({ conversationId: 'old' }, { list: (id) => id === 'old' ? old.promise : current.promise })
  const oldLoad = view.loadMessages()
  route.query = { conversationId: 'new' }
  const newLoad = view.loadMessages()
  current.resolve(page([{ messageId: 'new-m', conversationId: 'new', senderId: 'seller-1', content: '新消息', contextProductId: 'p1' }]))
  await newLoad
  old.resolve(page([{ messageId: 'old-m', content: '旧消息' }]))
  await oldLoad
  assert.equal(view.messages.value[0].messageId, 'new-m')
  assert.deepEqual(calls.reads, ['new'])
  assert.deepEqual(Array.from(calls.products[0]), ['p1'])
})

test('failed message reads stay visible and can be retried without hiding text', async () => {
  let attempt = 0
  const { view } = messages({ conversationId: 'c1' }, {
    list: async () => page([{ messageId: 'm1', content: '已加载正文' }]),
    read: async () => ++attempt === 1 ? { data: { code: 10003, message: '更新已读失败' } } : ok(null),
  })
  await view.loadMessages()
  assert.equal(view.messages.value[0].content, '已加载正文'); assert.equal(view.readError.value, '更新已读失败')
  await view.markRead()
  assert.equal(view.readError.value, '')
})

test('sending failure preserves draft and retry opens the returned conversation without duplicate sends', async () => {
  const pending = deferred()
  let attempt = 0
  const { view, calls, route } = messages({ sellerId: 'seller-1', contextProductId: 'p1' }, {
    send: async (payload) => { calls.sends.push(payload); return ++attempt === 1 ? { data: { code: 20010, message: '消息发送失败' } } : pending.promise },
  })
  view.draft.value = '  想咨询一下  '
  await view.sendMessage()
  assert.equal(view.sendError.value, '消息发送失败'); assert.equal(view.draft.value, '  想咨询一下  ')
  const retry = view.sendMessage()
  await view.sendMessage()
  assert.equal(calls.sends.length, 2)
  assert.equal(calls.sends[1].content, '想咨询一下'); assert.equal(calls.sends[1].sellerId, 'seller-1')
  assert.equal(calls.sends[1].contextProductId, 'p1'); assert.equal(calls.sends[1].senderId, undefined)
  pending.resolve(ok({ messageId: 'm1', conversationId: 'c1', content: '想咨询一下' }))
  await retry
  assert.equal(route.query.conversationId, 'c1'); assert.equal(view.draft.value, ''); assert.equal(view.sendError.value, '')
})

test('message pagination keeps the server chronological order and failed loads do not clear unread', async () => {
  const { view, calls } = messages({ conversationId: 'c1' }, { list: async (_, params) => params.page === 2
    ? page([{ messageId: 'm31', content: '先' }, { messageId: 'm32', content: '后' }], 32)
    : { data: { code: 10003, message: '无权查看会话' } } })
  await view.loadMessages()
  assert.equal(view.messageError.value, '无权查看会话'); assert.equal(calls.reads.length, 0)
  view.messagePage.value = 2
  await view.loadMessages()
  assert.equal(view.messages.value.map((m) => m.messageId).join(','), 'm31,m32')
  assert.equal(view.messageTotal.value, 32); assert.equal(calls.reads.length, 1)
})

test('seller who buys from another seller sees the actual conversation counterpart', () => {
  const { view } = messages({ conversationId: 'c1' })
  view.conversations.value = [{ conversationId: 'c1', buyerId: 'buyer-1', sellerId: 'seller-1', buyerName: '我', sellerName: '对方店主' }]
  assert.equal(view.recipientName.value, '对方店主')
})

test('finding an existing conversation preserves the consultation draft typed during loading', async () => {
  const { view, route } = messages({ sellerId: 'seller-1', contextProductId: 'p1' }, {
    conversations: async () => page([{ conversationId: 'c1', buyerId: 'buyer-1', sellerId: 'seller-1', sellerName: '店主' }]),
  })
  view.syncTarget()
  view.draft.value = '咨询草稿不能丢'
  await view.loadConversations()
  assert.equal(route.query.conversationId, 'c1')
  view.syncTarget()
  assert.equal(view.draft.value, '咨询草稿不能丢')
})

test('a slow conversation list cannot revive unread after this page successfully marked it read', async () => {
  const pending = deferred()
  const { view } = messages({ conversationId: 'c1' }, { conversations: () => pending.promise })
  const loading = view.loadConversations()
  await view.markRead()
  pending.resolve(page([{ conversationId: 'c1', buyerId: 'buyer-1', sellerId: 'seller-1', unreadCount: 4, buyerUnread: 4 }]))
  await loading
  assert.equal(view.conversations.value[0].unreadCount, 0)
  assert.equal(view.conversations.value[0].buyerUnread, 0)
})

test('opening an existing conversation starts at the latest page and preserves chronological order', async () => {
  const { view } = messages({ conversationId: 'c1' }, { list: async (_, params) => params.page === 2
    ? page([{ messageId: 'm31', content: '先' }, { messageId: 'm32', content: '后' }], 32)
    : page([{ messageId: 'm1', content: '最早' }], 32) })
  await view.syncTarget()
  assert.equal(view.messagePage.value, 2)
  assert.equal(view.messages.value.map((m) => m.messageId).join(','), 'm31,m32')
})

test('a consultation resolved to an existing conversation while sending still completes and clears its draft', async () => {
  const list = deferred(), send = deferred(), messageLoads = []
  const { view, route, calls } = messages({ sellerId: 'seller-1', contextProductId: 'p1' }, {
    conversations: () => list.promise,
    send: (payload) => { calls.sends.push(payload); return send.promise },
    list: async (id, params) => { messageLoads.push([id, params.page]); return page([{ messageId: 'm1', conversationId: id, content: '已发送' }]) },
  })
  view.draft.value = '商品咨询'
  const conversations = view.loadConversations()
  const sending = view.sendMessage()
  list.resolve(page([{ conversationId: 'c1', buyerId: 'buyer-1', sellerId: 'seller-1', sellerName: '店主' }]))
  await conversations
  view.syncTarget()
  send.resolve(ok({ messageId: 'm1', conversationId: 'c1', content: '商品咨询' }))
  await sending
  assert.equal(route.query.conversationId, 'c1')
  assert.equal(view.draft.value, '')
  assert.ok(messageLoads.some(([id]) => id === 'c1'))
})

test('a failed send remains visible after its consultation resolves to the matching conversation', async () => {
  const list = deferred(), send = deferred()
  const { view } = messages({ sellerId: 'seller-1', contextProductId: 'p1' }, {
    conversations: () => list.promise,
    send: () => send.promise,
  })
  view.draft.value = '商品咨询'
  const conversations = view.loadConversations()
  const sending = view.sendMessage()
  list.resolve(page([{ conversationId: 'c1', buyerId: 'buyer-1', sellerId: 'seller-1', sellerName: '店主' }]))
  await conversations
  view.syncTarget()
  send.resolve({ data: { code: 20010, message: '发送失败，请稍后重试' } })
  await sending
  assert.equal(view.draft.value, '商品咨询')
  assert.equal(view.sendError.value, '发送失败，请稍后重试')
})

test('an in-flight consultation does not clear or report errors over a manually selected unrelated conversation', async () => {
  const send = deferred()
  const { view, route } = messages({ sellerId: 'seller-1', contextProductId: 'p1' }, { send: () => send.promise })
  view.draft.value = '咨询商品'
  const sending = view.sendMessage()
  view.conversations.value = [{ conversationId: 'other', buyerId: 'buyer-1', sellerId: 'seller-2', sellerName: '另一位卖家' }]
  route.query = { conversationId: 'other' }
  view.syncTarget()
  view.draft.value = '另一段草稿'
  send.resolve({ data: { code: 20010, message: '原会话发送失败' } })
  await sending
  assert.equal(view.draft.value, '另一段草稿')
  assert.equal(view.sendError.value, '')
})
