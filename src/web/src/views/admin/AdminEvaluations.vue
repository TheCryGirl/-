<script setup>
import { onMounted, ref } from 'vue'
import { evaluationApi, isOk, unwrapResult } from '../../api'

const list = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const status = ref('')
const productId = ref('')
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await evaluationApi.adminList({
      page: page.value,
      size: size.value,
      status: status.value || undefined,
      productId: productId.value.trim() || undefined,
    })
    const payload = unwrapResult(data)
    if (!payload) {
      error.value = data?.message || '加载失败'
      return
    }
    list.value = payload.list || []
    total.value = payload.total || 0
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

async function toggle(item) {
  const hide = item.status === 'VISIBLE'
  const tip = hide
    ? '确定隐藏该评价？隐藏后商品详情页与评分统计都不再展示。'
    : '确定恢复该评价的展示？'
  if (!window.confirm(tip)) return
  error.value = ''
  try {
    const { data } = hide
      ? await evaluationApi.hide(item.evaluationId)
      : await evaluationApi.show(item.evaluationId)
    if (!isOk(data)) {
      error.value = data?.message || '操作失败'
      return
    }
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '操作失败'
  }
}

function stars(n) {
  return '★'.repeat(n || 0) + '☆'.repeat(5 - (n || 0))
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '—'
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">评价管理</h1>

    <div class="flex flex-wrap items-center gap-2">
      <select
        v-model="status"
        class="px-3 py-2 text-sm border border-gray-300 rounded-md bg-white"
        @change="search"
      >
        <option value="">全部状态</option>
        <option value="VISIBLE">展示中</option>
        <option value="HIDDEN">已隐藏</option>
      </select>
      <input
        v-model="productId"
        type="text"
        placeholder="按商品 ID 筛选"
        class="w-56 px-3 py-2 text-sm border border-gray-300 rounded-md"
        @keyup.enter="search"
      />
      <button
        class="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700"
        @click="search"
      >
        查询
      </button>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p class="text-xs text-gray-400">
      隐藏后该评价从商品详情页消失，平均分统计同步剔除；恢复后重新展示。操作会写入审计日志。
    </p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

    <div v-else class="bg-white rounded-lg border border-gray-200 overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-gray-50 text-gray-600">
          <tr>
            <th class="text-left px-4 py-2.5 font-medium">评价 ID</th>
            <th class="text-left px-4 py-2.5 font-medium">订单 ID</th>
            <th class="text-left px-4 py-2.5 font-medium">买家</th>
            <th class="text-left px-4 py-2.5 font-medium">评分</th>
            <th class="text-left px-4 py-2.5 font-medium">内容</th>
            <th class="text-left px-4 py-2.5 font-medium">晒图</th>
            <th class="text-left px-4 py-2.5 font-medium">状态</th>
            <th class="text-left px-4 py-2.5 font-medium">评价时间</th>
            <th class="text-left px-4 py-2.5 font-medium">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr v-for="item in list" :key="item.evaluationId">
            <td class="px-4 py-2.5 text-gray-500 whitespace-nowrap">{{ item.evaluationId }}</td>
            <td class="px-4 py-2.5 text-gray-500 whitespace-nowrap">{{ item.orderId }}</td>
            <td class="px-4 py-2.5 text-gray-900 whitespace-nowrap">
              {{ item.buyerId }}
              <span v-if="item.anonymous" class="ml-1 text-xs px-1.5 py-0.5 rounded bg-gray-100 text-gray-500">
                匿名
              </span>
            </td>
            <td class="px-4 py-2.5 text-amber-500 whitespace-nowrap">{{ stars(item.rating) }}</td>
            <td class="px-4 py-2.5 text-gray-700 max-w-64">
              <span class="line-clamp-2">{{ item.content || '—' }}</span>
            </td>
            <td class="px-4 py-2.5 text-gray-500 whitespace-nowrap">
              {{ item.images?.length || 0 }} 张
            </td>
            <td class="px-4 py-2.5 whitespace-nowrap">
              <span
                class="text-xs px-2 py-0.5 rounded"
                :class="item.status === 'VISIBLE' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'"
              >
                {{ item.status === 'VISIBLE' ? '展示中' : '已隐藏' }}
              </span>
            </td>
            <td class="px-4 py-2.5 text-gray-500 whitespace-nowrap">{{ formatTime(item.createdAt) }}</td>
            <td class="px-4 py-2.5 whitespace-nowrap">
              <button class="text-xs text-blue-600 hover:underline" @click="toggle(item)">
                {{ item.status === 'VISIBLE' ? '隐藏' : '恢复' }}
              </button>
            </td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="9" class="px-4 py-10 text-center text-gray-400">暂无评价</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total > size" class="flex items-center justify-center gap-2">
      <button
        class="px-3 py-1 text-sm border border-gray-300 rounded-md disabled:opacity-40"
        :disabled="page === 1"
        @click="page--; load()"
      >
        上一页
      </button>
      <span class="text-sm text-gray-500">{{ page }} / {{ Math.ceil(total / size) }}</span>
      <button
        class="px-3 py-1 text-sm border border-gray-300 rounded-md disabled:opacity-40"
        :disabled="page * size >= total"
        @click="page++; load()"
      >
        下一页
      </button>
    </div>
  </div>
</template>
