<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { userApi } from '../api'
import { setLogin, redirectTarget } from '../stores/auth'

const router = useRouter()
const userId = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  if (!userId.value.trim() || !password.value) {
    error.value = '请输入账号和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const { data } = await userApi.login(userId.value.trim(), password.value)
    const token = data?.token || ''
    const user = data?.user || { userId: userId.value.trim() }
    if (!token) {
      error.value = '登录返回异常，请检查后端接口'
      return
    }
    setLogin(token, user)
    router.push(redirectTarget())
  } catch (err) {
    error.value =
      err.response?.status === 401 ? '账号或密码错误' : err.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="max-w-md mx-auto mt-10 bg-white rounded-lg border border-gray-200 p-6">
    <h1 class="text-lg font-semibold text-gray-900 mb-1">登录</h1>
    <p class="text-sm text-gray-500 mb-5">登录后可以发布商品、下单与私聊卖家</p>

    <form class="space-y-4" @submit.prevent="submit">
      <div>
        <label class="block text-sm text-gray-600 mb-1">账号</label>
        <input
          v-model="userId"
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
