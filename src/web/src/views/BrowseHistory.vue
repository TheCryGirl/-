<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { historyApi, isOk, unwrapResult } from '../api'
import { confirmAction } from '../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../components/ui/message'
import { formatPrice, formatTime } from '../utils/format'
import PageHero from '../components/store/PageHero.vue'

const router = useRouter()

const list = ref([])
const limit = ref(20)
const loading = ref(true)
const loadError = ref('')
const clearing = ref(false)
const removingId = ref(null)

const limitOptions = [
  { value: 10, label: '最近 10 条' },
  { value: 20, label: '最近 20 条' },
  { value: 50, label: '最近 50 条' },
]

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await historyApi.recent(limit.value)
    const payload = unwrapResult(data)
    if (!payload) {
      loadError.value = data?.message || '加载浏览足迹失败'
      list.value = []
      return
    }
    list.value = Array.isArray(payload) ? payload : payload.list || []
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载浏览足迹失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function openDetail(item) {
  if (item.invalid) return
  router.push({ name: 'product-detail', params: { id: item.productId } })
}

async function remove(item) {
  if (removingId.value !== null || clearing.value) return
  const confirmed = await confirmAction({
    title: '删除浏览记录',
    message: `删除「${item.title}」的浏览记录？`,
    confirmButtonText: '删除',
    danger: true,
  })
  if (!confirmed) return

  removingId.value = item.productId
  try {
    const { data } = await historyApi.remove(item.productId)
    if (!isOk(data)) {
      showError(data?.message || '删除失败')
      return
    }
    list.value = list.value.filter((i) => i.productId !== item.productId)
    showSuccess('已删除浏览记录')
  } catch (err) {
    showError(resolveErrorMessage(err, '删除失败'))
  } finally {
    removingId.value = null
  }
}

async function clearAll() {
  if (clearing.value) return
  const confirmed = await confirmAction({
    title: '清空浏览足迹',
    message: '清空全部浏览足迹？',
    confirmButtonText: '清空',
    danger: true,
  })
  if (!confirmed) return

  clearing.value = true
  try {
    const { data } = await historyApi.clear()
    if (!isOk(data)) {
      showError(data?.message || '清空失败')
      return
    }
    list.value = []
    showSuccess('已清空浏览足迹')
  } catch (err) {
    showError(resolveErrorMessage(err, '清空失败'))
  } finally {
    clearing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <PageHero
      title="浏览足迹"
      subtitle="最近看过的商品都记在这里，方便回头比较"
      icon="clock"
      tone="teal"
    >
      <template #meta>
        <span v-if="!loadError" class="text-xs text-[var(--jd-ink-4)]">{{ list.length }} 条记录</span>
      </template>
      <template #actions>
        <el-select
          v-model="limit"
          style="width: 140px"
          :disabled="loading"
          data-testid="history-limit"
          @change="load"
        >
          <el-option v-for="opt in limitOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button
          :loading="clearing"
          :disabled="loading || !list.length"
          data-testid="history-clear"
          @click="clearAll"
        >
          清空足迹
        </el-button>
      </template>
    </PageHero>

    <el-card v-if="loading" shadow="never">
      <el-skeleton :rows="5" animated />
    </el-card>

    <el-card v-else-if="loadError" shadow="never">
      <el-result status="error" icon="error" title="加载浏览足迹失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" data-testid="history-retry" @click="load">重试</el-button>
        </template>
      </el-result>
    </el-card>

    <el-card v-else-if="!list.length" shadow="never">
      <el-empty description="还没有浏览记录">
        <el-button type="primary" @click="router.push({ name: 'products' })">去商品市场看看</el-button>
      </el-empty>
    </el-card>

    <ul v-else class="bg-white rounded-[14px] border border-[var(--jd-line)] divide-y divide-gray-100 overflow-hidden">
      <li
        v-for="item in list"
        :key="item.historyId"
        class="group flex items-center gap-4 p-3 transition-colors"
        :class="item.invalid ? 'opacity-60' : 'cursor-pointer hover:bg-[#fff8f6]'"
        @click="openDetail(item)"
      >
        <span v-if="item.mainImage" class="block overflow-hidden rounded-lg shrink-0">
          <img
            :src="item.mainImage"
            :alt="item.title"
            class="w-16 h-16 rounded-lg object-cover bg-gray-100 transition-transform duration-500 group-hover:scale-105"
            :class="item.invalid ? 'grayscale' : ''"
          />
        </span>
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
          <el-button
            link
            type="danger"
            size="small"
            :loading="removingId === item.productId"
            :disabled="(removingId !== null && removingId !== item.productId) || clearing"
            :data-testid="`history-remove-${item.productId}`"
            @click.stop="remove(item)"
          >
            删除
          </el-button>
        </div>
      </li>
    </ul>
  </div>
</template>

