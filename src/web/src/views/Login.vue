<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { userApi, unwrapResult } from '../api'
import { setLogin, redirectTarget } from '../stores/auth'

const router = useRouter()
const account = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  if (!account.value.trim() || !password.value) {
    error.value = '请输入账号和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const { data } = await userApi.login(account.value.trim(), password.value)
    const payload = unwrapResult(data)
    const token = payload?.token || ''
    const user = payload?.user || null
    if (!token || !user) {
      error.value = data?.message || '登录失败，请稍后重试'
      return
    }
    setLogin(token, user)
    router.push(redirectTarget())
  } catch (err) {
    const body = err.response?.data
    error.value = body?.message || err.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="max-w-md mx-auto mt-10 bg-white rounded-lg border border-gray-200 p-6">
    <h1 class="text-lg font-semibold text-gray-900 mb-1">登录</h1>
    <p class="text-sm text-gray-500 mb-5">登录后可以购物、下单与管理店铺</p>

    <form class="space-y-4" @submit.prevent="submit">
      <div>
        <label class="block text-sm text-gray-600 mb-1">账号</label>
        <input
          v-model="account"
          type="text"
          autocomplete="username"
          class="w-full px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>
      <div>
        <label class="block text-sm text-gray-600 mb-1">密码</label>
        <input
          v-model="password"
          type="password"
          autocomplete="current-password"
          class="w-full px-3 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

      <button
        type="submit"
        class="w-full py-2.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
        :disabled="loading"
      >
        {{ loading ? '登录中…' : '登录' }}
      </button>
    </form>

    <p class="text-sm text-gray-500 mt-4 text-center">
      还没有账号？
      <RouterLink :to="{ name: 'register' }" class="text-blue-600 hover:underline">
        去注册
      </RouterLink>
    </p>
  </div>
</template>
