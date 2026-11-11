<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { userApi, unwrapResult } from '../api'
import { setLogin, redirectTarget } from '../stores/auth'
import AuthShell from '../components/store/AuthShell.vue'
import StoreIcon from '../components/store/StoreIcon.vue'
import { showInfo } from '../components/ui/message'

const router = useRouter()
const account = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

/** 登录身份只有两种：用户与管理员；卖家由管理员开通 */
const role = ref('BUYER')
const roleOptions = [
  { value: 'BUYER', label: '用户', desc: '购买 · 已开通卖家可售卖', icon: 'user' },
  { value: 'ADMIN', label: '管理员', desc: '审核 · 风控 · 对账', icon: 'shield' },
]

const roleHint = computed(() => {
  if (role.value === 'ADMIN') return '管理员账号由部署环境变量初始化，普通注册账号没有管理权限。'
  return '注册默认为买家；需要售卖商品时请联系管理员开通，开通后重新登录。'
})

const submitLabel = computed(() => {
  const opt = roleOptions.find((o) => o.value === role.value)
  return `以${opt?.label || '用户'}身份登录`
})

function landingFor(actualRole) {
  if (actualRole === 'ADMIN') return { name: 'admin-users' }
  return redirectTarget()
}

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
    const actualRole = user.role || 'BUYER'
    if (actualRole !== role.value) {
      const actualLabel = roleOptions.find((o) => o.value === actualRole)?.label || (actualRole === 'SELLER' ? '卖家' : '买家')
      showInfo(`该账号的实际角色是「${actualLabel}」，已为你切换到对应入口`)
    }
    router.push(landingFor(actualRole))
  } catch (err) {
    const body = err.response?.data
    error.value = body?.message || err.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell
    badge="账号登录"
    title="欢迎回来"
    subtitle="普通用户可购买商品；卖家身份由管理员开通，管理员负责审核与运营。"
    :bullets="['注册默认为买家', '联系管理员开通卖家后重新登录', '演示环境支持 MOCK 支付，不产生真实扣款']"
  >
    <div class="role-tabs" role="tablist" aria-label="登录身份">
      <button
        v-for="opt in roleOptions"
        :key="opt.value"
        type="button"
        role="tab"
        class="role-tab"
        :class="{ 'is-active': role === opt.value }"
        :aria-selected="role === opt.value"
        :data-testid="`login-role-${opt.value.toLowerCase()}`"
        @click="role = opt.value; error = ''"
      >
        <StoreIcon :name="opt.icon" :size="18" />
        <span class="role-tab__text">
          <strong>{{ opt.label }}</strong>
          <small>{{ opt.desc }}</small>
        </span>
      </button>
    </div>
    <p class="role-hint">{{ roleHint }}</p>

    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="账号" prop="account">
        <el-input
          id="login-account"
          v-model="account"
          type="text"
          size="large"
          placeholder="请输入账号"
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
          size="large"
          placeholder="请输入密码"
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
        {{ loading ? '登录中…' : submitLabel }}
      </el-button>
    </el-form>

    <template #footer>
      还没有账号？
      <RouterLink :to="{ name: 'register' }">立即注册</RouterLink>
    </template>
  </AuthShell>
</template>

<style scoped>
.role-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 20px;
}
.role-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #ebeef5;
  background: #fbfbfc;
  color: #606266;
  text-align: left;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}
.role-tab:hover {
  transform: translateY(-1px);
  border-color: #f3cfca;
}
.role-tab.is-active {
  border-color: var(--jd-red);
  background: linear-gradient(140deg, #fff4f2, #fff);
  color: var(--jd-red);
  box-shadow: 0 10px 22px -18px rgba(225, 37, 27, 0.8);
}
.role-tab__text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  min-width: 0;
}
.role-tab__text strong {
  font-size: 13px;
  font-weight: 700;
}
.role-tab__text small {
  font-size: 10.5px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.role-tab.is-active .role-tab__text small {
  color: #c2706b;
}
.role-hint {
  margin: 10px 0 2px;
  font-size: 11.5px;
  line-height: 1.6;
  color: #909399;
}
</style>