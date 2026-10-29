<script setup>
import { onMounted, reactive, ref } from 'vue'
import { isOk, productApi, shopApi, unwrapResult } from '../../api'

const form = reactive({
  shopId: '',
  name: '',
  logo: '',
  description: '',
  contactPhone: '',
  contactWechat: '',
  status: 'ACTIVE',
})
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const message = ref('')
const error = ref('')

function fill(shop) {
  form.shopId = shop.shopId || ''
  form.name = shop.name || ''
  form.logo = shop.logo || ''
  form.description = shop.description || ''
  form.contactPhone = shop.contactPhone || ''
  form.contactWechat = shop.contactWechat || ''
  form.status = shop.status || 'ACTIVE'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await shopApi.mine()
    const shop = unwrapResult(data)
    if (!shop) {
      error.value = data?.message || '加载店铺资料失败'
      return
    }
    fill(shop)
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '加载店铺资料失败'
  } finally {
    loading.value = false
  }
}

async function uploadLogo(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    error.value = '图片不能超过 5MB'
    return
  }
  uploading.value = true
  error.value = ''
  try {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await productApi.uploadImage(formData)
    const uploaded = unwrapResult(data)
    if (!uploaded) {
      error.value = data?.message || '图片上传失败'
      return
    }
    // 与商品主图共用 /images 静态映射
    form.logo = uploaded.url || ''
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '图片上传失败'
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

async function save() {
  message.value = ''
  error.value = ''
  if (!form.name.trim()) {
    error.value = '店铺名称不能为空'
    return
  }
  if (form.contactPhone && !/^$|^1[3-9]\d{9}$|^0\d{2,3}-?\d{7,8}$/.test(form.contactPhone)) {
    error.value = '联系电话格式不正确'
    return
  }
  saving.value = true
  try {
    const { data } = await shopApi.updateMine({
      name: form.name.trim(),
      logo: form.logo,
      description: form.description,
      contactPhone: form.contactPhone,
      contactWechat: form.contactWechat,
    })
    if (!isOk(data)) {
      error.value = data?.message || '保存店铺资料失败'
      return
    }
    fill(unwrapResult(data) || form)
    message.value = '店铺资料已保存'
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '保存店铺资料失败'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-gray-900">店铺资料</h1>
      <span
        v-if="form.status === 'SUSPENDED'"
        class="text-xs text-red-600 border border-red-500 px-2 py-0.5 rounded"
      >
        已被管理员停用
      </span>
      <span
        v-else
        class="text-xs text-green-600 border border-green-500 px-2 py-0.5 rounded"
      >
        正常营业
      </span>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p v-if="message" class="text-sm text-green-600">{{ message }}</p>
    <p v-if="loading" class="text-sm text-gray-500">加载中…</p>

    <section v-else class="bg-white rounded-lg border border-gray-200 p-5 space-y-4">
      <p v-if="form.status === 'SUSPENDED'" class="text-sm text-red-600 bg-red-50 rounded p-3">
        店铺停用期间，你的商品不会出现在买家搜索结果中，也无法下单。恢复营业后会自动重新上架。
      </p>

      <label class="block text-sm text-gray-600">
        店铺名称
        <input
          v-model="form.name"
          maxlength="64"
          class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="必填，最多 64 字"
        />
      </label>

      <div class="text-sm text-gray-600">
        店铺头像
        <div class="mt-1 flex items-center gap-3">
          <img
            v-if="form.logo"
            :src="form.logo"
            alt="店铺头像"
            class="w-16 h-16 rounded-md object-cover border border-gray-200"
          />
          <div v-else class="w-16 h-16 rounded-md bg-gray-100 text-xs text-gray-400 flex items-center justify-center">
            无头像
          </div>
          <label class="px-3 py-1.5 rounded-md border border-gray-300 text-gray-600 cursor-pointer hover:bg-gray-50">
            {{ uploading ? '上传中…' : '上传图片' }}
            <input type="file" accept="image/*" class="hidden" :disabled="uploading" @change="uploadLogo" />
          </label>
          <button v-if="form.logo" class="text-xs text-red-500" @click="form.logo = ''">清除</button>
        </div>
        <input
          v-model="form.logo"
          maxlength="255"
          class="mt-2 w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
          placeholder="也可以直接填写图片地址"
        />
      </div>

      <label class="block text-sm text-gray-600">
        店铺简介
        <textarea
          v-model="form.description"
          rows="3"
          maxlength="255"
          class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          placeholder="选填，最多 255 字"
        ></textarea>
      </label>

      <div class="grid gap-4 sm:grid-cols-2">
        <label class="text-sm text-gray-600">
          联系电话
          <input
            v-model="form.contactPhone"
            maxlength="20"
            class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="手机号或座机"
          />
        </label>
        <label class="text-sm text-gray-600">
          在线联系方式
          <input
            v-model="form.contactWechat"
            maxlength="64"
            class="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md text-sm"
            placeholder="微信 / 旺旺"
          />
        </label>
      </div>

      <div class="flex justify-end">
        <button
          class="px-5 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
          :disabled="saving"
          @click="save"
        >
          {{ saving ? '保存中…' : '保存资料' }}
        </button>
      </div>
    </section>
  </div>
</template>
