<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { userApi } from '../api'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const ok = ref('')

const form = reactive({
  userId: '',
  name: '',
  password: '',
  confirm: '',
  age: '',
  sex: '男',
  address: '',
  phone: '',
})

function validate() {
  if (!form.userId.trim()) return '请填写账号'
  if (!form.name.trim()) return '请填写昵称'
  if (form.password.length < 6) return '密码至少 6 位'
  if (form.password !== form.confirm) return '两次输入的密码不一致'
  if (form.phone && !/^\d{11}$/.test(form.phone)) return '手机号应为 11 位数字'
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
    const payload = {
      userId: form.userId.trim(),
      name: form.name.trim(),
      password: form.password,
      age: form.age || null,
      sex: form.sex,
      address: form.address || null,
      phone: form.phone || null,
    }
    await userApi.register(payload)
    ok.value = '注册成功，正在跳转到登录页…'
    setTimeout(() => router.push({ name: 'login' }), 900)
  } catch (err) {
    const body = err.response?.data
    error.value =
      typeof body === 'string' && body
        ? body
        : err.response?.status === 400
          ? '账号或昵称已被占用'
          : '注册失败，请稍后重试'
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
        <input v-model="form.userId" type="text" class="field" />
      </div>
      <div>
        <label class="block text-sm text-gray-600 mb-1">昵称 *</label>
        <input v-model="form.name" type="text" class="field" />
      </div>
      <div>
        <label class="block text-sm text-gray-600 mb-1">密码 *</label>
        <input v-model="form.password" type="password" class="field" />
      </div>
      <div>
        <label class="block text-sm text-gray-600 mb-1">确认密码 *</label>
        <input v-model="form.confirm" type="password" class="field" />
      </div>
      <div>
        <label class="block text-sm text-gray-600 mb-1">年龄</label>
        <input v-model="form.age" type="number" min="1" max="120" class="field" />
      </div>
      <div>
        <label class="block text-sm text-gray-600 mb-1">性别</label>
        <select v-model="form.sex" class="field">
          <option value="男">男</option>
          <option value="女">女</option>
          <option value="保密">保密</option>
        </select>
      </div>
      <div class="sm:col-span-2">
        <label class="block text-sm text-gray-600 mb-1">宿舍地址</label>
        <input v-model="form.address" type="text" class="field" />
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
