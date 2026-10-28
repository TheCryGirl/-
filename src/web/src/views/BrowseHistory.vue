<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { historyApi, isOk, unwrapResult } from '../api'
import { formatTime } from '../utils/format'

const router = useRouter()

const list = ref([])
const limit = ref(20)
const loading = ref(true)
const error = ref('')
const clearing = ref(false)

const limitOptions = [
  { value: 10, label: '最近 10 条' },
  { value: 20, label: '最近 20 条' },
  { value: 50, label: '最近 50 条' },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await historyApi.recent(limit.value)
    const payload = unwrapResult(data)
    if (!payload) {
      error.value = data?.message || '加载浏览足迹失败'
      return
    }
    list.value = Array.isArray(payload) ? payload : payload.list || []
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载浏览足迹失败'
  } finally {
    loading.value = false
  }
}

function formatPrice(cents) {
  return (Number(cents || 0) / 100).toFixed(2)
}

function openDetail(item) {
  if (item.invalid) return
  router.push({ name: 'product-detail', params: { id: item.productId } })
}

async function remove(item) {
  error.value = ''
  try {
    const { data } = await historyApi.remove(item.productId)
    if (!isOk(data)) {
      error.value = data?.message || '删除失败'
      return
    }
    list.value = list.value.filter((i) => i.productId !== item.productId)
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '删除失败'
  }
}

async function clearAll() {
  if (!window.confirm('清空全部浏览足迹？')) return
  clearing.value = true
  error.value = ''
  try {
    const { data } = await historyApi.clear()
    if (!isOk(data)) {
      error.value = data?.message || '清空失败'
      return
    }
    list.value = []
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '清空失败'
  } finally {
    clearing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h1 class="text-lg font-semibold text-gray-900">
        浏览足迹
        <span class="text-sm text-gray-400 font-normal">{{ list.length }} 条</span>
      </h1>
      <div class="flex items-center gap-2">
        <select
          v-model.number="limit"
          class="px-3 py-1.5 text-sm border border-gray-300 rounded-md"
          @change="load"
        >
          <option v-for="opt in limitOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
        <button
          class="px-3 py-1.5 text-sm rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50 disabled:opacity-40"
          :disabled="clearing || !list.length"
          @click="clearAll"
        >
          {{ clearing ? '清空中…' : '清空足迹' }}
        </button>
      </div>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

    <template v-else>
      <div v-if="!list.length" class="py-16 text-center text-gray-400 text-sm space-y-3">
        <p>还没有浏览记录</p>
        <RouterLink :to="{ name: 'products' }" class="text-blue-600 hover:underline">
          去商品市场看看
        </RouterLink>
      </div>

      <ul v-else class="bg-white rounded-lg border border-gray-200 divide-y divide-gray-100">
        <li
          v-for="item in list"
          :key="item.historyId"
          class="flex items-center gap-4 p-3"
          :class="item.invalid ? 'opacity-60' : 'cursor-pointer hover:bg-gray-50'"
          @click="openDetail(item)"
        >
          <img
            v-if="item.mainImage"
            :src="item.mainImage"
            :alt="item.title"
            class="w-16 h-16 rounded object-cover bg-gray-100"
            :class="item.invalid ? 'grayscale' : ''"
          />
          <div
            v-else
            class="w-16 h-16 rounded bg-gray-100 flex items-center justify-center text-xs text-gray-400"
          >
            暂无图片
          </div>

          <div class="flex-1 min-w-0">
            <p class="text-sm text-gray-900 line-clamp-1">{{ item.title }}</p>
            <p class="text-sm text-red-600">¥{{ formatPrice(item.price) }}</p>
            <p v-if="item.invalid" class="text-xs text-gray-500">
              {{ item.invalidReason || '暂不可购买' }}
            </p>
          </div>

          <div class="text-right text-xs text-gray-400 whitespace-nowrap space-y-1">
            <p>{{ formatTime(item.visitedAt) }}</p>
            <button class="text-red-500 hover:underline" @click.stop="remove(item)">删除</button>
          </div>
        </li>
      </ul>
    </template>
  </div>
</template>
