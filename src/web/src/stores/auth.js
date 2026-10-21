import { computed, reactive, readonly } from 'vue'
import { userApi } from '../api'

/**
 * 全局登录态。用 reactive 而非 Pinia，项目规模还小，一个文件足够，
 * 避免为这点状态引入额外依赖。
 */
const state = reactive({
  token: localStorage.getItem('token') || '',
  user: JSON.parse(localStorage.getItem('user') || 'null'),
})

function persist() {
  if (state.token) {
    localStorage.setItem('token', state.token)
  } else {
    localStorage.removeItem('token')
  }
  if (state.user) {
    localStorage.setItem('user', JSON.stringify(state.user))
  } else {
    localStorage.removeItem('user')
  }
}

export function setLogin(token, user) {
  state.token = token
  state.user = user
  persist()
}

export function setUser(user) {
  state.user = user
  persist()
}

export function logout() {
  state.token = ''
  state.user = null
  persist()
}

export async function refreshUser() {
  if (!state.token) return null
  try {
    const { data } = await userApi.me()
    const user = data?.data ?? data
    setUser(user)
    return user
  } catch {
    return null
  }
}

export const auth = readonly(state)
export const isLoggedIn = computed(() => Boolean(state.token && state.user))
export const currentUserId = computed(() => state.user?.userId || '')
export const currentRole = computed(() => state.user?.role || 'GUEST')
export const isAdmin = computed(() => currentRole.value === 'ADMIN')
export const isSeller = computed(() => currentRole.value === 'SELLER' || currentRole.value === 'ADMIN')

/** 登录后默认跳到当初想去的页面 */
export function redirectTarget() {
  const p = sessionStorage.getItem('redirectAfterLogin')
  sessionStorage.removeItem('redirectAfterLogin')
  return p || '/products'
}
