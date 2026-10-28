<script setup>
import { onMounted, ref } from 'vue'
import { isOk, shopApi, unwrapResult } from '../../api'

const shops = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')
const statusFilter = ref('')
const loading = ref(true)
const error = ref('')
const operating = ref('')

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'ACTIVE', label: '正常营业' },
  { value: 'SUSPENDED', label: '已停用' },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await shopApi.list({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined,
    })
    const payload = unwrapResult(data)
    if (!payload) {
      error.value = data?.message || '加载店铺列表失败'
      return
    }
    shops.value = payload.list || []
    total.value = payload.total || 0
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载店铺列表失败'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

async function toggleStatus(shop) {
  const suspend = shop.status !== 'SUSPENDED'
  const action = suspend ? '停用' : '恢复'
  const tip = suspend
    ? `停用后「${shop.name}」的在售商品会立即对买家隐藏，且买家无法下单。确认停用？`
    : `恢复后「${shop.name}」的商品会重新上架展示。确认恢复营业？`
  if (!window.confirm(tip)) return
  operating.value = shop.shopId
  error.value = ''
  try {
    const { data } = await shopApi.updateStatus(shop.shopId, suspend ? 'SUSPENDED' : 'ACTIVE')
    if (!isOk(data)) {
      error.value = data?.message || `${action}店铺失败`
      return
    }
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || err.message || `${action}店铺失败`
  } finally {
    operating.value = ''
  }
}

function formatTime(t) {
  return t || '—'
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">店铺管理</h1>

    <div class="flex flex-wrap items-center gap-2">
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索店铺名称 / 卖家 ID"
        class="flex-1 min-w-50 px-3 py-2 text-sm border border-gray-300 rounded-md"
        @keyup.enter="search"
      />
      <select
        v-model="statusFilter"
        class="px-3 py-2 text-sm border border-gray-300 rounded-md"
        @change="search"
      >
        <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
      <button
        class="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700"
        @click="search"
      >
        搜索
      </button>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p class="text-xs text-gray-400">
      停用店铺会同步把该卖家的在售商品置为「店铺停用」，买家不可见、不可下单；恢复营业后自动重新上架。
    </p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

    <template v-else>
      <div v-if="!shops.length" class="py-16 text-center text-gray-400 text-sm">
        没有符合条件的店铺
      </div>

      <div v-else class="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table class="w-full text-sm">
          <thead class="bg-gray-50 text-gray-600">
            <tr>
              <th class="text-left px-4 py-2.5 font-medium">店铺</th>
              <th class="text-left px-4 py-2.5 font-medium">卖家 ID</th>
              <th class="text-left px-4 py-2.5 font-medium">营业状态</th>
              <th class="text-left px-4 py-2.5 font-medium">更新时间</th>
              <th class="text-left px-4 py-2.5 font-medium">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-for="s in shops" :key="s.shopId">
              <td class="px-4 py-2.5">
                <div class="flex items-center gap-2">
                  <img
                    v-if="s.logo"
                    :src="s.logo"
                    alt=""
                    class="w-8 h-8 rounded object-cover border border-gray-200"
                  />
                  <div>
                    <p class="text-gray-900">{{ s.name }}</p>
                    <p v-if="s.description" class="text-xs text-gray-400 line-clamp-1">
                      {{ s.description }}
                    </p>
                  </div>
                </div>
              </td>
              <td class="px-4 py-2.5 text-gray-500">{{ s.sellerId }}</td>
              <td class="px-4 py-2.5">
                <span
                  class="text-xs px-2 py-0.5 rounded"
                  :class="s.status === 'SUSPENDED' ? 'bg-red-100 text-red-700' : 'bg-green-100 text-green-700'"
                >
                  {{ s.status === 'SUSPENDED' ? '已停用' : '正常营业' }}
                </span>
              </td>
              <td class="px-4 py-2.5 text-gray-500">{{ formatTime(s.updatedAt) }}</td>
              <td class="px-4 py-2.5">
                <button
                  class="text-xs hover:underline disabled:opacity-40"
                  :class="s.status === 'SUSPENDED' ? 'text-green-600' : 'text-red-600'"
                  :disabled="operating === s.shopId"
                  @click="toggleStatus(s)"
                >
                  {{ operating === s.shopId ? '处理中…' : s.status === 'SUSPENDED' ? '恢复营业' : '停用店铺' }}
                </button>
              </td>
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
    </template>
  </div>
</template>
