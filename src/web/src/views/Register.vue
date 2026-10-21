<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { userApi } from '../api'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const ok = ref('')

const form = reactive({
  account: '',
  nickname: '',
  password: '',
  confirm: '',
  phone: '',
})

function validate() {
  if (!form.account.trim()) return '请填写账号'
  if (!/^[a-zA-Z0-9_]{4,20}$/.test(form.account.trim())) return '账号须为 4-20 位字母、数字或下划线'
  if (!form.nickname.trim()) return '请填写昵称'
  if (form.nickname.trim().length < 2 || form.nickname.trim().length > 20) return '昵称须为 2-20 位'
  if (form.password.length < 6 || form.password.length > 32) return '密码须为 6-32 位'
  if (form.password !== form.confirm) return '两次输入的密码不一致'
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone)) return '手机号格式不正确'
  return ''
}

async function submit() {
  const msg = validate()
  if (msg) {
    error.value = msg
    return
  }
  loading.value = true
  error.value = ''
  ok.value = ''
  try {
    await userApi.register({
      account: form.account.trim(),
      nickname: form.nickname.trim(),
      password: form.password,
      phone: form.phone || undefined,
    })
    ok.value = '注册成功，正在跳转到登录页…'
    setTimeout(() => router.push({ name: 'login' }), 900)
  } catch (err) {
    const body = err.response?.data
    error.value = body?.message || err.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="max-w-lg mx-auto mt-8 bg-white rounded-lg border border-gray-200 p-6">
    <h1 class="text-lg font-semibold text-gray-900 mb-1">注册</h1>
    <p class="text-sm text-gray-500 mb-5">带 * 的为必填项</p>

    <form class="grid sm:grid-cols-2 gap-4" @submit.prevent="submit">
      <div>
        <label class="block text-sm text-gray-600 mb-1">账号 *</label>
        <input v-model="form.account" type="text" class="field" placeholder="4-20 位字母数字下划线" />
      </div>
      <div>
        <label class="block text-sm text-gray-600 mb-1">昵称 *</label>
        <input v-model="form.nickname" type="text" class="field" placeholder="2-20 位" />
      </div>
      <div>
        <label class="block text-sm text-gray-600 mb-1">密码 *</label>
        <input v-model="form.password" type="password" class="field" placeholder="6-32 位" />
      </div>
      <div>
        <label class="block text-sm text-gray-600 mb-1">确认密码 *</label>
        <input v-model="form.confirm" type="password" class="field" />
      </div>
      <div class="sm:col-span-2">
        <label class="block text-sm text-gray-600 mb-1">手机号</label>
        <input v-model="form.phone" type="tel" maxlength="11" class="field" />
      </div>

      <p v-if="error" class="sm:col-span-2 text-sm text-red-600">{{ error }}</p>
      <p v-if="ok" class="sm:col-span-2 text-sm text-green-600">{{ ok }}</p>

      <button
        type="submit"
        class="sm:col-span-2 py-2.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
        :disabled="loading"
      >
        {{ loading ? '提交中…' : '注册' }}
      </button>
    </form>

    <p class="text-sm text-gray-500 mt-4 text-center">
      已有账号？
      <RouterLink :to="{ name: 'login' }" class="text-blue-600 hover:underline">去登录</RouterLink>
    </p>
  </div>
</template>

<style scoped>
.field {
  width: 100%;
  padding: 0.5rem 0.75rem;
  font-size: 0.875rem;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  outline: none;
}
.field:focus {
  box-shadow: 0 0 0 2px rgb(59 130 246 / 0.4);
}
</style>
