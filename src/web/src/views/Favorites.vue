<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { favoriteApi, isOk, unwrapResult } from '../api'
import { formatTime } from '../utils/format'

const router = useRouter()

const list = ref([])
const page = ref(1)
const size = ref(12)
const total = ref(0)
const loading = ref(true)
const error = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await favoriteApi.list({ page: page.value, size: size.value })
    const payload = unwrapResult(data)
    if (!payload) {
      error.value = data?.message || '加载收藏夹失败'
      return
    }
    list.value = payload.list || []
    total.value = payload.total || 0
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载收藏夹失败'
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
  if (!window.confirm(`取消收藏「${item.title}」？`)) return
  error.value = ''
  try {
    const { data } = await favoriteApi.remove(item.productId)
    if (!isOk(data)) {
      error.value = data?.message || '取消收藏失败'
      return
    }
    // 删掉当前页最后一条时回到上一页，避免停在空页
    if (list.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '取消收藏失败'
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-gray-900">
        我的收藏
        <span class="text-sm text-gray-400 font-normal">共 {{ total }} 件</span>
      </h1>
      <RouterLink :to="{ name: 'products' }" class="text-sm text-blue-600 hover:underline">
        去逛逛
      </RouterLink>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

    <template v-else>
      <div v-if="!list.length" class="py-16 text-center text-gray-400 text-sm space-y-3">
        <p>收藏夹还是空的</p>
        <RouterLink :to="{ name: 'products' }" class="text-blue-600 hover:underline">
          去看看有什么值得收藏的商品
        </RouterLink>
      </div>

      <div v-else class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
        <article
          v-for="item in list"
          :key="item.favoriteId"
          class="bg-white rounded-lg border border-gray-200 overflow-hidden flex flex-col"
          :class="item.invalid ? 'opacity-60' : 'cursor-pointer hover:shadow-md transition-shadow'"
          @click="openDetail(item)"
        >
          <div class="relative">
            <img
              v-if="item.mainImage"
              :src="item.mainImage"
              :alt="item.title"
              class="w-full aspect-4/3 object-cover bg-gray-100"
              :class="item.invalid ? 'grayscale' : ''"
            />
            <div
              v-else
              class="w-full aspect-4/3 bg-gray-100 flex items-center justify-center text-gray-400 text-sm"
            >
              暂无图片
            </div>
            <span
              v-if="item.invalid"
              class="absolute inset-0 flex items-center justify-center bg-black/40 text-white text-sm"
            >
              {{ item.invalidReason || '暂不可购买' }}
            </span>
          </div>
          <div class="p-3 space-y-1 flex-1 flex flex-col">
            <p class="text-sm text-gray-800 line-clamp-2">{{ item.title }}</p>
            <p class="text-red-600 font-medium text-sm">¥{{ formatPrice(item.price) }}</p>
            <div class="flex items-center justify-between text-xs text-gray-400 pt-1">
              <span>{{ formatTime(item.createdAt) }}</span>
              <button class="text-red-500 hover:underline" @click.stop="remove(item)">
                取消收藏
              </button>
            </div>
          </div>
        </article>
      </div>

      <div v-if="totalPages > 1" class="flex items-center justify-center gap-2">
        <button
          class="px-3 py-1.5 text-sm rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50 disabled:opacity-40"
          :disabled="page === 1"
          @click="page--; load()"
        >
          上一页
        </button>
        <span class="text-sm text-gray-500">{{ page }} / {{ totalPages }}</span>
        <button
          class="px-3 py-1.5 text-sm rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50 disabled:opacity-40"
          :disabled="page === totalPages"
          @click="page++; load()"
        >
          下一页
        </button>
      </div>
    </template>
  </div>
</template>
