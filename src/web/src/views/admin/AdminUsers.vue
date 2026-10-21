<script setup>
import { onMounted, ref } from 'vue'
import { userApi } from '../../api'

const users = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await userApi.list({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
    })
    const payload = data?.data ?? data
    users.value = payload?.list || []
    total.value = payload?.total || 0
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleStatus(user) {
  const newStatus = user.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE'
  try {
    await userApi.updateStatus(user.userId, { status: newStatus })
    await load()
  } catch (err) {
    error.value = err.response?.data?.message || '操作失败'
  }
}

function search() {
  page.value = 1
  load()
}

function formatTime(t) {
  return t || '—'
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">用户管理</h1>

    <div class="flex items-center gap-2">
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索账号/昵称"
        class="flex-1 px-3 py-2 text-sm border border-gray-300 rounded-md"
        @keyup.enter="search"
      />
      <button
        class="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700"
        @click="search"
      >
        搜索
      </button>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <div v-if="loading" class="py-16 text-center text-gray-400 text-sm">加载中…</div>

    <div v-else class="bg-white rounded-lg border border-gray-200 overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-gray-50 text-gray-600">
          <tr>
            <th class="text-left px-4 py-2.5 font-medium">账号</th>
            <th class="text-left px-4 py-2.5 font-medium">昵称</th>
            <th class="text-left px-4 py-2.5 font-medium">角色</th>
            <th class="text-left px-4 py-2.5 font-medium">状态</th>
            <th class="text-left px-4 py-2.5 font-medium">注册时间</th>
            <th class="text-left px-4 py-2.5 font-medium">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr v-for="u in users" :key="u.userId">
            <td class="px-4 py-2.5 text-gray-900">{{ u.account }}</td>
            <td class="px-4 py-2.5 text-gray-700">{{ u.nickname }}</td>
            <td class="px-4 py-2.5 text-gray-700">{{ u.role }}</td>
            <td class="px-4 py-2.5">
              <span
                class="text-xs px-2 py-0.5 rounded"
                :class="u.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'"
              >
                {{ u.status === 'ACTIVE' ? '正常' : '已冻结' }}
              </span>
            </td>
            <td class="px-4 py-2.5 text-gray-500">{{ formatTime(u.createdAt) }}</td>
            <td class="px-4 py-2.5">
              <button
                class="text-xs text-blue-600 hover:underline"
                @click="toggleStatus(u)"
              >
                {{ u.status === 'ACTIVE' ? '冻结' : '解冻' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total > size" class="flex items-center justify-center gap-2">
      <button
        class="px-3 py-1 text-sm border border-gray-300 rounded-md"
        :disabled="page === 1"
        @click="page--; load()"
      >
        上一页
      </button>
      <span class="text-sm text-gray-500">{{ page }} / {{ Math.ceil(total / size) }}</span>
      <button
        class="px-3 py-1 text-sm border border-gray-300 rounded-md"
        :disabled="page * size >= total"
        @click="page++; load()"
      >
        下一页
      </button>
    </div>
  </div>
</template>
