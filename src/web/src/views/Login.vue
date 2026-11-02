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
  if (loading.value) return
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
  <div class="max-w-md mx-auto mt-10 px-4">
    <el-card shadow="never">
      <h1 class="text-lg font-semibold text-gray-900 mb-1">登录</h1>
      <p class="text-sm text-gray-500 mb-5">登录后可以购物、下单与管理店铺</p>

      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="账号" prop="account">
          <el-input
            id="login-account"
            v-model="account"
            type="text"
            autocomplete="username"
            data-testid="login-account"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            id="login-password"
            v-model="password"
            type="password"
            show-password
            autocomplete="current-password"
            data-testid="login-password"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-alert
          v-if="error"
          class="mb-4"
          type="error"
          :title="error"
          :closable="false"
          show-icon
          data-testid="login-error"
        />

        <el-button
          type="primary"
          native-type="submit"
          class="w-full"
          :loading="loading"
          data-testid="login-submit"
        >
          {{ loading ? '登录中…' : '登录' }}
        </el-button>
      </el-form>

      <p class="text-sm text-gray-500 mt-4 text-center">
        还没有账号？
        <RouterLink :to="{ name: 'register' }" class="text-blue-600 hover:underline">
          去注册
        </RouterLink>
      </p>
    </el-card>
  </div>
</template>
