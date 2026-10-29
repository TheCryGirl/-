<script setup>
import { onMounted, reactive, ref } from 'vue'
import { addressApi, isOk, userApi, unwrapResult } from '../api'
import { auth, setUser } from '../stores/auth'

const profile = reactive({ nickname: '', phone: '', avatar: '' })
const profileLoading = ref(false)
const profileMessage = ref('')
const profileError = ref('')
const avatarUploading = ref(false)

const addresses = ref([])
const addressLoading = ref(false)
const addressError = ref('')
const editingId = ref('')
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  defaultAddress: false,
})

function unwrap(res) {
  return unwrapResult(res.data)
}

function fillProfile(user) {
  if (!user) return
  profile.nickname = user.nickname || ''
  profile.phone = user.phone || ''
  profile.avatar = user.avatar || ''
}

async function loadProfile() {
  try {
    const user = unwrap(await userApi.me())
    // 失败时不能把错误包装当成用户信息写进登录态
    if (!user) {
      profileError.value = '加载个人信息失败'
      return
    }
    setUser(user)
    fillProfile(user)
  } catch (err) {
    profileError.value = err.response?.data?.message || err.message || '加载个人信息失败'
  }
}

async function saveProfile() {
  profileLoading.value = true
  profileMessage.value = ''
  profileError.value = ''
  try {
    const res = await userApi.updateMe({ ...profile })
    const user = unwrap(res)
    if (!user) {
      profileError.value = res.data?.message || '保存失败'
      return
    }
    setUser(user)
    fillProfile(user)
    profileMessage.value = '个人信息已保存'
  } catch (err) {
    profileError.value = err.response?.data?.message || err.message || '保存失败'
  } finally {
    profileLoading.value = false
  }
}

async function uploadAvatar(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    profileError.value = '头像不能超过 5MB'
    return
  }
  avatarUploading.value = true
  profileMessage.value = ''
  profileError.value = ''
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await userApi.uploadAvatar(formData)
    if (!isOk(res.data)) {
      profileError.value = res.data?.message || '头像上传失败'
      return
    }
    const uploaded = unwrapResult(res.data)
    profile.avatar = uploaded?.avatar || ''
    // 后端已把路径写回档案，重新拉取一次保证内存登录态与库一致
    await loadProfile()
    profileMessage.value = '头像已更新'
  } catch (err) {
    profileError.value = err.response?.data?.message || err.message || '头像上传失败'
  } finally {
    avatarUploading.value = false
    event.target.value = ''
  }
}

async function loadAddresses() {
  const userId = auth.user?.userId
  if (!userId) {
    addressError.value = '登录状态已失效，请重新登录'
    return
  }
  addressLoading.value = true
  addressError.value = ''
  try {
    addresses.value = unwrap(await addressApi.list(userId)) || []
  } catch (err) {
    addressError.value = err.response?.data?.message || err.message || '加载地址失败'
  } finally {
    addressLoading.value = false
  }
}

function resetForm() {
  editingId.value = ''
  form.receiver = ''
  form.phone = ''
  form.province = ''
  form.city = ''
  form.district = ''
  form.detail = ''
  form.defaultAddress = false
}

function openCreate() {
  resetForm()
  formVisible.value = true
}

function openEdit(addr) {
  editingId.value = addr.addressId
  form.receiver = addr.receiver || ''
  form.phone = addr.phone || ''
  form.province = addr.province || ''
  form.city = addr.city || ''
  form.district = addr.district || ''
  form.detail = addr.detail || ''
  form.defaultAddress = Boolean(addr.defaultAddress ?? addr.isDefault)
  formVisible.value = true
}

async function submitAddress() {
  addressError.value = ''
  if (!form.receiver || !form.phone || !form.province || !form.city || !form.detail) {
    addressError.value = '请填写收货人、手机号、省份、城市和详细地址'
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    addressError.value = '手机号格式不正确'
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    const res = editingId.value
      ? await addressApi.update(editingId.value, payload)
      : await addressApi.create(payload)
    // 业务失败也是 HTTP 200，必须按 code 判断
    if (!isOk(res.data)) {
      addressError.value = res.data?.message || '保存地址失败'
      return
    }
    formVisible.value = false
    resetForm()
    await loadAddresses()
  } catch (err) {
    addressError.value = err.response?.data?.message || err.message || '保存地址失败'
  } finally {
    saving.value = false
  }
}

async function removeAddress(addr) {
  if (!window.confirm(`确认删除地址「${addr.receiver} ${addr.detail}」？`)) return
  addressError.value = ''
  try {
    const res = await addressApi.remove(addr.addressId)
    if (!isOk(res.data)) {
      addressError.value = res.data?.message || '删除失败'
      return
    }
    await loadAddresses()
  } catch (err) {
    addressError.value = err.response?.data?.message || err.message || '删除失败'
  }
}

async function setDefault(addr) {
  addressError.value = ''
  try {
    const res = await addressApi.setDefault(addr.addressId)
    if (!isOk(res.data)) {
      addressError.value = res.data?.message || '设置默认地址失败'
      return
    }
    await loadAddresses()
  } catch (err) {
    addressError.value = err.response?.data?.message || err.message || '设置默认地址失败'
  }
}

onMounted(() => {
  fillProfile(auth.user)
  loadProfile()
  loadAddresses()
})
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-6">
    <h1 class="text-lg font-semibold text-gray-900">个人中心</h1>

    <!-- 个人资料 -->
    <section class="bg-white rounded-lg border border-gray-200 p-5 space-y-4">
      <div class="flex items-center justify-between">
        <h2 class="text-sm font-medium text-gray-700">个人资料</h2>
        <span class="text-xs text-gray-400">账号：{{ auth.user?.account }}</span>
      </div>

      <p v-if="profileMessage" class="text-sm text-green-600">{{ profileMessage }}</p>
      <p v-if="profileError" class="text-sm text-red-600">{{ profileError }}</p>

      <div class="grid gap-4 sm:grid-cols-2">
        <label class="text-sm text-gray-600">
          昵称
          <input
            v-model="profile.nickname"
            maxlength="20"
            class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="2-20 个字符"
          />
        </label>
        <label class="text-sm text-gray-600">
          手机号
          <input
            v-model="profile.phone"
            maxlength="11"
            class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="选填"
          />
        </label>
        <div class="text-sm text-gray-600 sm:col-span-2">
          头像
          <div class="mt-1 flex items-center gap-3">
            <img
              v-if="profile.avatar"
              :src="profile.avatar"
              alt="头像"
              class="w-14 h-14 rounded-full object-cover border border-gray-200"
            />
            <div
              v-else
              class="w-14 h-14 rounded-full bg-gray-100 text-xs text-gray-400 flex items-center justify-center"
            >
              未设置
            </div>
            <label
              class="px-3 py-1.5 rounded-md border border-gray-300 text-gray-600 cursor-pointer hover:bg-gray-50"
            >
              {{ avatarUploading ? '上传中…' : '上传头像' }}
              <input
                type="file"
                accept="image/*"
                class="hidden"
                :disabled="avatarUploading"
                @change="uploadAvatar"
              />
            </label>
            <button v-if="profile.avatar" class="text-xs text-red-500" @click="profile.avatar = ''">
              移除
            </button>
          </div>
          <input
            v-model="profile.avatar"
            maxlength="255"
            class="mt-2 w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            placeholder="也可以直接填写图片地址"
          />
        </div>
      </div>

      <div class="flex justify-end">
        <button
          class="px-5 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
          :disabled="profileLoading"
          @click="saveProfile"
        >
          {{ profileLoading ? '保存中…' : '保存资料' }}
        </button>
      </div>
    </section>

    <!-- 收货地址 -->
    <section class="bg-white rounded-lg border border-gray-200 p-5 space-y-4">
      <div class="flex items-center justify-between">
        <h2 class="text-sm font-medium text-gray-700">收货地址</h2>
        <button
          class="px-3 py-1.5 rounded-md border border-blue-600 text-blue-600 text-sm hover:bg-blue-50"
          @click="openCreate"
        >
          新增地址
        </button>
      </div>

      <p v-if="addressError" class="text-sm text-red-600">{{ addressError }}</p>
      <p v-if="addressLoading" class="text-sm text-gray-500">加载中…</p>
      <p v-else-if="!addresses.length" class="text-sm text-gray-500">还没有收货地址</p>

      <ul v-else class="space-y-3">
        <li
          v-for="addr in addresses"
          :key="addr.addressId"
          class="flex items-start justify-between gap-4 p-3 border border-gray-200 rounded-md"
        >
          <div class="text-sm">
            <p class="font-medium text-gray-900">
              {{ addr.receiver }} {{ addr.phone }}
              <span
                v-if="addr.defaultAddress ?? addr.isDefault"
                class="ml-2 text-xs text-blue-600 border border-blue-600 px-1 rounded"
              >
                默认
              </span>
            </p>
            <p class="text-gray-500">
              {{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detail }}
            </p>
          </div>
          <div class="flex items-center gap-3 text-sm whitespace-nowrap">
            <button
              v-if="!(addr.defaultAddress ?? addr.isDefault)"
              class="text-gray-500 hover:text-blue-600"
              @click="setDefault(addr)"
            >
              设为默认
            </button>
            <button class="text-gray-500 hover:text-blue-600" @click="openEdit(addr)">编辑</button>
            <button class="text-red-500 hover:text-red-600" @click="removeAddress(addr)">删除</button>
          </div>
        </li>
      </ul>

      <!-- 地址表单 -->
      <div v-if="formVisible" class="border-t border-gray-100 pt-4 space-y-3">
        <h3 class="text-sm font-medium text-gray-700">
          {{ editingId ? '编辑地址' : '新增地址' }}
        </h3>
        <div class="grid gap-3 sm:grid-cols-2">
          <input
            v-model="form.receiver"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="收货人"
          />
          <input
            v-model="form.phone"
            maxlength="11"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="手机号"
          />
          <input
            v-model="form.province"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="省份"
          />
          <input
            v-model="form.city"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="城市"
          />
          <input
            v-model="form.district"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="区/县（选填）"
          />
          <label class="flex items-center gap-2 text-sm text-gray-600">
            <input v-model="form.defaultAddress" type="checkbox" />
            设为默认地址
          </label>
          <input
            v-model="form.detail"
            class="px-3 py-2 border border-gray-300 rounded-md text-sm sm:col-span-2"
            placeholder="详细地址（街道、门牌号）"
          />
        </div>
        <div class="flex justify-end gap-3">
          <button
            class="px-4 py-2 rounded-md border border-gray-300 text-gray-600 text-sm hover:bg-gray-50"
            @click="formVisible = false"
          >
            取消
          </button>
          <button
            class="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
            :disabled="saving"
            @click="submitAddress"
          >
            {{ saving ? '保存中…' : '保存地址' }}
          </button>
        </div>
      </div>
    </section>
  </div>
</template>
