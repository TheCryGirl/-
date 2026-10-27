<script setup>
import { computed, onMounted, ref } from 'vue'
import { productApi, unwrapResult, userApi } from '../../api'

/**
 * 店铺管理：按卖家维度聚合商品与库存情况。
 *
 * 后端没有专门的店铺接口，这里用「用户列表筛 SELLER」+「逐卖家查商品」组合出来。
 * 卖家数量在课程规模下很少，逐店铺请求可以接受。
 */
const shops = ref([])
const loading = ref(true)
const error = ref('')
const keyword = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await userApi.list({ page: 1, size: 100 })
    const payload = unwrapResult(data)
    const users = Array.isArray(payload) ? payload : payload?.list || []
    const sellers = users.filter((u) => u.role === 'SELLER')

    // 并发拉取每个卖家的商品
    const details = await Promise.all(
      sellers.map(async (seller) => {
        try {
          const res = await productApi.list({ sellerId: seller.userId, size: 100 })
          const body = unwrapResult(res.data)
          const list = Array.isArray(body) ? body : body?.list || []
          return { seller, products: list }
        } catch {
          return { seller, products: [] }
        }
      }),
    )

    shops.value = details.map(({ seller, products }) => ({
      userId: seller.userId,
      account: seller.account,
      nickname: seller.nickname,
      status: seller.status,
      phone: seller.phone,
      createdAt: seller.createdAt,
      productCount: products.length,
      onSaleCount: products.filter((p) => p.status === 'ON_SALE').length,
      pendingCount: products.filter((p) => p.status === 'PENDING').length,
      offShelfCount: products.filter((p) => p.status === 'OFF_SHELF').length,
      totalStock: products.reduce((sum, p) => sum + (p.stock || 0), 0),
    }))
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载店铺失败'
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return shops.value
  return shops.value.filter(
    (s) =>
      String(s.account).toLowerCase().includes(kw) ||
      String(s.nickname || '').toLowerCase().includes(kw) ||
      String(s.userId).toLowerCase().includes(kw),
  )
})

const summary = computed(() => ({
  shops: shops.value.length,
  products: shops.value.reduce((n, s) => n + s.productCount, 0),
  onSale: shops.value.reduce((n, s) => n + s.onSaleCount, 0),
  pending: shops.value.reduce((n, s) => n + s.pendingCount, 0),
}))

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">店铺管理</h1>

    <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
      <div class="bg-white rounded-lg border border-gray-200 p-3">
        <p class="text-xs text-gray-500">入驻店铺</p>
        <p class="text-xl font-semibold text-gray-900">{{ summary.shops }}</p>
      </div>
      <div class="bg-white rounded-lg border border-gray-200 p-3">
        <p class="text-xs text-gray-500">商品总数</p>
        <p class="text-xl font-semibold text-gray-900">{{ summary.products }}</p>
      </div>
      <div class="bg-white rounded-lg border border-gray-200 p-3">
        <p class="text-xs text-gray-500">在售商品</p>
        <p class="text-xl font-semibold text-green-600">{{ summary.onSale }}</p>
      </div>
      <div class="bg-white rounded-lg border border-gray-200 p-3">
        <p class="text-xs text-gray-500">待审核</p>
        <p class="text-xl font-semibold text-orange-500">{{ summary.pending }}</p>
      </div>
    </div>

    <div class="flex gap-2">
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索店铺账号 / 昵称"
        class="flex-1 px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      <button
        class="px-4 py-2 text-sm rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50"
        @click="load"
      >
        刷新
      </button>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>
    <div v-else-if="!filtered.length" class="py-16 text-center text-gray-400 text-sm">
      暂无入驻店铺
    </div>

    <div v-else class="bg-white rounded-lg border border-gray-200 overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-gray-50 text-gray-600">
          <tr>
            <th class="text-left px-4 py-2.5 font-medium">店铺</th>
            <th class="text-left px-4 py-2.5 font-medium">状态</th>
            <th class="text-right px-4 py-2.5 font-medium">商品</th>
            <th class="text-right px-4 py-2.5 font-medium">在售</th>
            <th class="text-right px-4 py-2.5 font-medium">待审核</th>
            <th class="text-right px-4 py-2.5 font-medium">总库存</th>
            <th class="text-left px-4 py-2.5 font-medium">入驻时间</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr v-for="s in filtered" :key="s.userId">
            <td class="px-4 py-2.5">
              <p class="text-gray-900">{{ s.nickname || s.account }}</p>
              <p class="text-xs text-gray-400">{{ s.account }}</p>
            </td>
            <td class="px-4 py-2.5">
              <span
                class="text-xs px-2 py-0.5 rounded"
                :class="s.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'"
              >
                {{ s.status === 'ACTIVE' ? '正常' : '已冻结' }}
              </span>
            </td>
            <td class="px-4 py-2.5 text-right text-gray-700">{{ s.productCount }}</td>
            <td class="px-4 py-2.5 text-right text-gray-700">{{ s.onSaleCount }}</td>
            <td class="px-4 py-2.5 text-right text-gray-700">{{ s.pendingCount }}</td>
            <td class="px-4 py-2.5 text-right text-gray-700">{{ s.totalStock }}</td>
            <td class="px-4 py-2.5 text-xs text-gray-500">{{ s.createdAt || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <p class="text-xs text-gray-400">
      说明：店主账号的冻结/角色变更请到「用户管理」操作；本页只做店铺维度的经营概览。
    </p>
  </div>
</template>
