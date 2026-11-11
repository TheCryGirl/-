<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isOk, userApi } from '../api'
import AuthShell from '../components/store/AuthShell.vue'

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

const errors = reactive({
  account: '',
  nickname: '',
  password: '',
  confirm: '',
  phone: '',
})

function clearError(field) {
  errors[field] = ''
}

function validate() {
  errors.account = ''
  errors.nickname = ''
  errors.password = ''
  errors.confirm = ''
  errors.phone = ''

  if (!form.account.trim()) errors.account = '请填写账号'
  else if (!/^[a-zA-Z0-9_]{4,20}$/.test(form.account.trim())) {
    errors.account = '账号须为 4-20 位字母、数字或下划线'
  }

  if (!form.nickname.trim()) errors.nickname = '请填写昵称'
  else if (form.nickname.trim().length < 2 || form.nickname.trim().length > 20) {
    errors.nickname = '昵称须为 2-20 位'
  }

  if (form.password.length < 6 || form.password.length > 32) errors.password = '密码须为 6-32 位'
  if (form.password !== form.confirm) errors.confirm = '两次输入的密码不一致'
  if (form.phone && !/^1[3-9]\d{9}$/.test(form.phone)) errors.phone = '手机号格式不正确'

  return Object.values(errors).every((msg) => !msg)
}

async function submit() {
  if (loading.value || ok.value) return
  error.value = ''
  ok.value = ''
  if (!validate()) return
  loading.value = true
  try {
    const { data } = await userApi.register({
      account: form.account.trim(),
      nickname: form.nickname.trim(),
      password: form.password,
      phone: form.phone || undefined,
    })
    if (!isOk(data)) {
      error.value = data?.message || '注册失败，请稍后重试'
      return
    }
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
  <AuthShell
    wide
    badge="创建账号"
    title="加入校园商城"
    subtitle="注册默认为买家，可下单购买；需要售卖商品时，请联系管理员开通卖家身份后重新登录。带 * 的为必填项。"
    :bullets="['注册不自动授予卖家权限', '管理员开通卖家后可发布商品', '账号仅用于课程实验，不收集无关信息']"
  >
    <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="账号" for="register-account" required :error="errors.account">
          <el-input
            id="register-account"
            v-model="form.account"
            type="text"
            autocomplete="username"
            placeholder="4-20 位字母数字下划线"
            data-testid="register-account"
            @input="clearError('account')"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-form-item label="昵称" for="register-nickname" required :error="errors.nickname">
          <el-input
            id="register-nickname"
            v-model="form.nickname"
            type="text"
            placeholder="2-20 位"
            data-testid="register-nickname"
            @input="clearError('nickname')"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-form-item label="密码" for="register-password" required :error="errors.password">
          <el-input
            id="register-password"
            v-model="form.password"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="6-32 位"
            data-testid="register-password"
            @input="clearError('password')"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-form-item label="确认密码" for="register-confirm" required :error="errors.confirm">
          <el-input
            id="register-confirm"
            v-model="form.confirm"
            type="password"
            show-password
            autocomplete="new-password"
            data-testid="register-confirm"
            @input="clearError('confirm')"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-form-item label="手机号" for="register-phone" :error="errors.phone">
          <el-input
            id="register-phone"
            v-model="form.phone"
            type="tel"
            maxlength="11"
            data-testid="register-phone"
            @input="clearError('phone')"
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
          data-testid="register-error"
        />
        <el-alert
          v-if="ok"
          class="mb-4"
          type="success"
          :title="ok"
          :closable="false"
          show-icon
          data-testid="register-success"
        />

        <el-button
          type="primary"
          native-type="submit"
          class="w-full"
          :loading="loading"
          :disabled="!!ok"
          data-testid="register-submit"
        >
          {{ loading ? '提交中…' : '注册' }}
        </el-button>
      </el-form>

    <template #footer>
      已有账号？
      <RouterLink :to="{ name: 'login' }">立即登录</RouterLink>
    </template>
  </AuthShell>
</template>


