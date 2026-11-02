<script setup>
import { onMounted, reactive, ref } from 'vue'
import { addressApi, isOk, userApi, unwrapResult } from '../api'
import { auth, setUser } from '../stores/auth'
import { confirmAction } from '../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../components/ui/message'

const profile = reactive({ nickname: '', phone: '', avatar: '' })
const profileSaving = ref(false)
const profileLoadError = ref('')
const avatarUploading = ref(false)
const avatarInput = ref(null)

const addresses = ref([])
const addressLoading = ref(false)
const addressLoadError = ref('')
const editingId = ref('')
const formVisible = ref(false)
const saving = ref(false)
const removingId = ref('')
const settingDefaultId = ref('')
const addressFormRef = ref(null)
const form = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  defaultAddress: false,
})

// 校验规则与原页面保持一致：区/县选填，手机号只接受 11 位大陆手机号
const addressRules = {
  receiver: [{ required: true, message: '请填写收货人', trigger: 'blur' }],
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  province: [{ required: true, message: '请填写省份', trigger: 'blur' }],
  city: [{ required: true, message: '请填写城市', trigger: 'blur' }],
  detail: [{ required: true, message: '请填写详细地址', trigger: 'blur' }],
}

function fillProfile(user) {
  if (!user) return
  profile.nickname = user.nickname || ''
  profile.phone = user.phone || ''
  profile.avatar = user.avatar || ''
}

async function loadProfile() {
  profileLoadError.value = ''
  try {
    const { data } = await userApi.me()
    if (!isOk(data)) {
      profileLoadError.value = data?.message || '加载个人信息失败'
      return
    }
    const user = unwrapResult(data)
    // 失败时不能把错误包装当成用户信息写进登录态
    if (!user) {
      profileLoadError.value = '加载个人信息失败'
      return
    }
    setUser(user)
    fillProfile(user)
  } catch (err) {
    profileLoadError.value = resolveErrorMessage(err, '加载个人信息失败')
  }
}

async function saveProfile() {
  if (profileSaving.value) return
  profileSaving.value = true
  try {
    const { data } = await userApi.updateMe({ ...profile })
    if (!isOk(data)) {
      showError(data?.message || '保存失败')
      return
    }
    const user = unwrapResult(data)
    if (!user) {
      showError('保存失败：未返回用户信息')
      return
    }
    setUser(user)
    fillProfile(user)
    showSuccess('个人信息已保存')
  } catch (err) {
    showError(resolveErrorMessage(err, '保存失败'))
  } finally {
    profileSaving.value = false
  }
}

function triggerAvatarUpload() {
  avatarInput.value?.click()
}

async function handleAvatarFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || avatarUploading.value) return
  if (file.size > 5 * 1024 * 1024) {
    showError('头像不能超过 5MB')
    return
  }

  avatarUploading.value = true
  try {
    // 保持原上传流程：FormData 字段固定为 file，一次选择只发一次请求
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await userApi.uploadAvatar(formData)
    if (!isOk(data)) {
      showError(data?.message || '头像上传失败')
      return
    }
    const uploaded = unwrapResult(data)
    if (!uploaded?.avatar) {
      showError('头像上传失败，未返回头像地址')
      return
    }
    profile.avatar = uploaded.avatar
    // 后端已把路径写回档案，重新拉取一次保证内存登录态与库一致
    await loadProfile()
    showSuccess('头像已更新')
  } catch (err) {
    showError(resolveErrorMessage(err, '头像上传失败'))
  } finally {
    avatarUploading.value = false
  }
}

async function loadAddresses() {
  const userId = auth.user?.userId
  if (!userId) {
    addressLoadError.value = '登录状态已失效，请重新登录'
    return
  }
  addressLoading.value = true
  addressLoadError.value = ''
  try {
    const { data } = await addressApi.list(userId)
    if (!isOk(data)) {
      addressLoadError.value = data?.message || '加载地址失败'
      return
    }
    const payload = unwrapResult(data)
    addresses.value = Array.isArray(payload) ? payload : []
  } catch (err) {
    addressLoadError.value = resolveErrorMessage(err, '加载地址失败')
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
  if (saving.value) return
  // 校验期间也算「提交中」，否则双击会并发两次 create
  saving.value = true
  try {
    await addressFormRef.value?.validate()
  } catch {
    saving.value = false
    return
  }

  const isEditing = Boolean(editingId.value)
  try {
    const payload = { ...form }
    const res = isEditing
      ? await addressApi.update(editingId.value, payload)
      : await addressApi.create(payload)
    // 业务失败也是 HTTP 200，必须按 code 判断
    if (!isOk(res.data)) {
      showError(res.data?.message || '保存地址失败')
      return
    }
    formVisible.value = false
    resetForm()
    await loadAddresses()
    showSuccess(isEditing ? '地址已更新' : '地址已新增')
  } catch (err) {
    showError(resolveErrorMessage(err, '保存地址失败'))
  } finally {
    saving.value = false
  }
}

async function removeAddress(addr) {
  if (removingId.value) return
  const ok = await confirmAction({
    message: `确认删除地址「${addr.receiver} ${addr.detail}」？`,
    danger: true,
  })
  if (!ok) return

  removingId.value = addr.addressId
  try {
    const res = await addressApi.remove(addr.addressId)
    if (!isOk(res.data)) {
      showError(res.data?.message || '删除失败')
      return
    }
    await loadAddresses()
    showSuccess('地址已删除')
  } catch (err) {
    showError(resolveErrorMessage(err, '删除失败'))
  } finally {
    removingId.value = ''
  }
}

async function setDefault(addr) {
  if (settingDefaultId.value) return
  settingDefaultId.value = addr.addressId
  try {
    const res = await addressApi.setDefault(addr.addressId)
    if (!isOk(res.data)) {
      showError(res.data?.message || '设置默认地址失败')
      return
    }
    await loadAddresses()
    showSuccess('默认地址已更新')
  } catch (err) {
    showError(resolveErrorMessage(err, '设置默认地址失败'))
  } finally {
    settingDefaultId.value = ''
  }
}

onMounted(() => {
  fillProfile(auth.user)
  loadProfile()
  loadAddresses()
})
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-4">
    <h1 class="text-lg font-semibold text-gray-900">个人中心</h1>

    <!-- 个人资料 -->
    <el-card shadow="never">
      <template #header>
        <div class="flex flex-wrap items-center justify-between gap-2">
          <span class="text-base font-medium">个人资料</span>
          <span class="text-xs text-gray-400">账号：{{ auth.user?.account || '-' }}</span>
        </div>
      </template>

      <el-alert
        v-if="profileLoadError"
        type="error"
        :closable="false"
        show-icon
        class="mb-4"
        :title="profileLoadError"
      >
        <el-button link type="primary" data-testid="profile-retry" @click="loadProfile">重新加载</el-button>
      </el-alert>

      <el-form :model="profile" label-width="80px" @submit.prevent>
        <div class="grid gap-4 sm:grid-cols-2">
          <el-form-item label="昵称">
            <el-input
              v-model="profile.nickname"
              maxlength="20"
              show-word-limit
              placeholder="2-20 个字符"
              data-testid="profile-nickname"
            />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input
              v-model="profile.phone"
              maxlength="11"
              placeholder="选填"
              data-testid="profile-phone"
            />
          </el-form-item>

          <el-form-item label="头像" class="sm:col-span-2">
            <div class="w-full space-y-2">
              <div class="flex flex-wrap items-center gap-3">
                <el-image
                  v-if="profile.avatar"
                  :src="profile.avatar"
                  fit="cover"
                  class="w-14 h-14 rounded-full border border-gray-200"
                  :preview-src-list="[profile.avatar]"
                  preview-teleported
                >
                  <template #error>
                    <div class="w-14 h-14 rounded-full bg-gray-100 text-xs text-gray-400 flex items-center justify-center">
                      加载失败
                    </div>
                  </template>
                </el-image>
                <div
                  v-else
                  class="w-14 h-14 rounded-full bg-gray-100 text-xs text-gray-400 flex items-center justify-center"
                >
                  未设置
                </div>

                <el-button
                  :loading="avatarUploading"
                  data-testid="profile-avatar-upload"
                  @click="triggerAvatarUpload"
                >
                  {{ avatarUploading ? '上传中…' : '上传头像' }}
                </el-button>
                <el-button
                  v-if="profile.avatar"
                  link
                  type="danger"
                  data-testid="profile-avatar-remove"
                  @click="profile.avatar = ''"
                >
                  移除
                </el-button>
                <input
                  ref="avatarInput"
                  type="file"
                  accept="image/*"
                  class="hidden"
                  :disabled="avatarUploading"
                  data-testid="profile-avatar-file"
                  @change="handleAvatarFile"
                />
              </div>

              <el-input
                v-model="profile.avatar"
                maxlength="255"
                placeholder="也可以直接填写图片地址"
                data-testid="profile-avatar-url"
              />
              <p class="text-xs text-gray-400">头像不超过 5MB，上传后立即写入个人档案。</p>
            </div>
          </el-form-item>
        </div>

        <div class="flex justify-end">
          <el-button
            type="primary"
            :loading="profileSaving"
            data-testid="profile-save"
            @click="saveProfile"
          >
            {{ profileSaving ? '保存中…' : '保存资料' }}
          </el-button>
        </div>
      </el-form>
    </el-card>

    <!-- 收货地址 -->
    <el-card shadow="never">
      <template #header>
        <div class="flex flex-wrap items-center justify-between gap-2">
          <span class="text-base font-medium">收货地址</span>
          <el-button type="primary" data-testid="profile-address-create" @click="openCreate">
            新增地址
          </el-button>
        </div>
      </template>

      <el-alert
        v-if="addressLoadError"
        type="error"
        :closable="false"
        show-icon
        class="mb-3"
        :title="addressLoadError"
      >
        <el-button link type="primary" data-testid="profile-address-retry" @click="loadAddresses">
          重新加载
        </el-button>
      </el-alert>

      <template v-else>
        <div v-if="addressLoading" v-loading="true" class="min-h-28 flex items-center justify-center text-sm text-gray-400">
          正在加载地址…
        </div>

        <el-empty v-else-if="!addresses.length" description="还没有收货地址">
          <el-button type="primary" plain data-testid="profile-address-empty-create" @click="openCreate">
            新增第一条地址
          </el-button>
        </el-empty>

        <div v-else class="space-y-3">
          <div
            v-for="addr in addresses"
            :key="addr.addressId"
            class="flex flex-wrap items-start justify-between gap-3 rounded-lg border border-gray-200 p-3"
            :data-testid="`profile-address-${addr.addressId}`"
          >
            <div class="min-w-0 text-sm">
              <p class="font-medium text-gray-900 break-words">
                {{ addr.receiver }} {{ addr.phone }}
                <el-tag v-if="addr.defaultAddress ?? addr.isDefault" type="primary" size="small" class="ml-2">
                  默认
                </el-tag>
              </p>
              <p class="text-gray-500 break-words mt-1">
                {{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detail }}
              </p>
            </div>
            <div class="flex flex-wrap items-center gap-2">
              <el-button
                v-if="!(addr.defaultAddress ?? addr.isDefault)"
                link
                type="primary"
                size="small"
                :loading="settingDefaultId === addr.addressId"
                :data-testid="`profile-address-default-${addr.addressId}`"
                @click="setDefault(addr)"
              >
                设为默认
              </el-button>
              <el-button
                link
                type="primary"
                size="small"
                :data-testid="`profile-address-edit-${addr.addressId}`"
                @click="openEdit(addr)"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                :loading="removingId === addr.addressId"
                :data-testid="`profile-address-delete-${addr.addressId}`"
                @click="removeAddress(addr)"
              >
                删除
              </el-button>
            </div>
          </div>
        </div>
      </template>
    </el-card>

    <!-- 地址表单 -->
    <el-dialog
      v-model="formVisible"
      :title="editingId ? '编辑地址' : '新增地址'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="addressFormRef" :model="form" :rules="addressRules" label-width="90px">
        <el-form-item label="收货人" prop="receiver">
          <el-input v-model="form.receiver" maxlength="20" placeholder="请输入收货人" data-testid="address-form-receiver" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="11 位手机号" data-testid="address-form-phone" />
        </el-form-item>
        <el-form-item label="省份" prop="province">
          <el-input v-model="form.province" maxlength="32" placeholder="省份" data-testid="address-form-province" />
        </el-form-item>
        <el-form-item label="城市" prop="city">
          <el-input v-model="form.city" maxlength="32" placeholder="城市" data-testid="address-form-city" />
        </el-form-item>
        <el-form-item label="区/县">
          <el-input v-model="form.district" maxlength="32" placeholder="选填" data-testid="address-form-district" />
        </el-form-item>
        <el-form-item label="详细地址" prop="detail">
          <el-input
            v-model="form.detail"
            type="textarea"
            :rows="2"
            maxlength="255"
            placeholder="街道、门牌号"
            data-testid="address-form-detail"
          />
        </el-form-item>
        <el-form-item label="默认地址">
          <el-checkbox v-model="form.defaultAddress" data-testid="address-form-default">
            设为默认地址
          </el-checkbox>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="saving" data-testid="address-form-cancel" @click="formVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          data-testid="address-form-submit"
          @click="submitAddress"
        >
          {{ saving ? '保存中…' : '保存地址' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* Element Plus 按钮默认不显示焦点环，键盘操作时需要可见反馈 */
.el-button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
