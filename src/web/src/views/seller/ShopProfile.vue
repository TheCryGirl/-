<script setup>
/**
 * 卖家店铺资料（#81 T06）
 *
 * 上传仍走原来的原生 file input + FormData → productApi.uploadImage：
 * 不换成 Element 的上传组件，避免它自带的 action 与自定义上传叠加出两次请求；
 * 5MB 限制、accept、返回体解析（{ url }）与失败可重试都保持原样。
 *
 * 表单字段、校验规则、保存接口（PUT /shops/mine）与清空语义均不变。
 */
import { onMounted, reactive, ref } from 'vue'
import { isOk, productApi, shopApi, unwrapResult } from '../../api'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'

/* ---------- 1. 状态 ---------- */
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
const loadError = ref('')
const saving = ref(false)
const uploading = ref(false)
const formRef = ref(null)
const fileInputRef = ref(null)

const rules = {
  name: [
    { required: true, message: '店铺名称不能为空', trigger: 'blur' },
    { max: 64, message: '店铺名称最多 64 字', trigger: 'blur' },
  ],
  contactPhone: [
    {
      pattern: /^$|^1[3-9]\d{9}$|^0\d{2,3}-?\d{7,8}$/,
      message: '联系电话格式不正确，支持手机号或区号座机',
      trigger: 'blur',
    },
  ],
}

/* ---------- 2. 方法 ---------- */
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
  loadError.value = ''
  try {
    const { data } = await shopApi.mine()
    if (!isOk(data)) {
      loadError.value = data?.message || '加载店铺资料失败'
      return
    }
    const shop = unwrapResult(data)
    if (!shop) {
      loadError.value = '加载店铺资料失败'
      return
    }
    fill(shop)
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载店铺资料失败')
  } finally {
    loading.value = false
  }
}

function pickLogo() {
  fileInputRef.value?.click()
}

async function handleLogoChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    showError('图片不能超过 5MB')
    event.target.value = ''
    return
  }
  if (uploading.value) return

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await productApi.uploadImage(formData)
    if (!isOk(data)) {
      showError(data?.message || '图片上传失败')
      return
    }
    const uploaded = unwrapResult(data)
    if (!uploaded?.url) {
      showError('图片上传失败，未返回图片地址')
      return
    }
    // 与商品主图共用 /images 静态映射，只更新草稿，保存时才写库
    form.logo = uploaded.url
    showSuccess('图片已上传，保存后生效')
  } catch (err) {
    showError(resolveErrorMessage(err, '图片上传失败'))
  } finally {
    uploading.value = false
    // 允许重复选择同一文件再次上传
    event.target.value = ''
  }
}

async function save() {
  if (saving.value) return
  try {
    await formRef.value?.validate()
  } catch {
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
      showError(data?.message || '保存店铺资料失败')
      return
    }
    fill(unwrapResult(data) || form)
    showSuccess('店铺资料已保存')
  } catch (err) {
    showError(resolveErrorMessage(err, '保存店铺资料失败'))
  } finally {
    saving.value = false
  }
}

/* ---------- 3. 生命周期 ---------- */
onMounted(load)
</script>

<template>
  <div class="max-w-3xl mx-auto space-y-4">
    <div class="flex items-center justify-between">
      <el-tag v-if="form.status === 'SUSPENDED'" type="danger" size="small">已被管理员停用</el-tag>
      <el-tag v-else type="success" size="small">正常营业</el-tag>
      <span class="text-xs text-gray-400">资料保存后立即对买家生效</span>
    </div>

    <el-result v-if="loadError" status="error" title="加载店铺资料失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="seller-shop-retry" @click="load">重试</el-button>
      </template>
    </el-result>

    <el-form v-else ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="96px">
      <el-card shadow="never">
        <template #header>
          <span class="text-base font-medium">店铺资料</span>
        </template>

        <el-alert
          v-if="form.status === 'SUSPENDED'"
          type="error"
          :closable="false"
          show-icon
          class="mb-4"
          title="店铺停用期间，你的商品不会出现在买家搜索结果中，也无法下单。恢复营业后会自动重新上架。"
        />

        <el-form-item label="店铺名称" prop="name">
          <el-input
            v-model="form.name"
            maxlength="64"
            show-word-limit
            placeholder="必填，最多 64 字"
            data-testid="seller-shop-name"
          />
        </el-form-item>

        <el-form-item label="店铺头像">
          <div class="w-full space-y-2">
            <div class="flex items-center gap-3">
              <el-image
                v-if="form.logo"
                :src="form.logo"
                fit="cover"
                class="w-16 h-16 rounded-md border border-gray-200"
                :preview-src-list="[form.logo]"
                preview-teleported
              />
              <div
                v-else
                class="w-16 h-16 rounded-md bg-gray-100 text-xs text-gray-400 flex items-center justify-center"
              >
                无头像
              </div>

              <!-- 原生文件选择保留，保证一次点击只发一次上传请求 -->
              <el-button
                :loading="uploading"
                data-testid="seller-shop-logo-pick"
                @click="pickLogo"
              >
                {{ uploading ? '上传中…' : '上传图片' }}
              </el-button>
              <el-button
                v-if="form.logo"
                link
                type="danger"
                data-testid="seller-shop-logo-clear"
                @click="form.logo = ''"
              >
                清除
              </el-button>
              <input
                ref="fileInputRef"
                type="file"
                accept="image/*"
                class="hidden"
                :disabled="uploading"
                data-testid="seller-shop-logo-file"
                @change="handleLogoChange"
              />
            </div>

            <el-input
              v-model="form.logo"
              maxlength="255"
              placeholder="也可以直接填写图片地址，例如 /images/shop1.png"
              data-testid="seller-shop-logo-url"
            />
            <p class="text-xs text-gray-400">
              上传只改动本地草稿，点「保存资料」后才写入店铺信息；图片不超过 5MB。
            </p>
          </div>
        </el-form-item>

        <el-form-item label="店铺简介">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="选填，最多 255 字"
            data-testid="seller-shop-description"
          />
        </el-form-item>

        <el-form-item label="联系电话" prop="contactPhone">
          <el-input
            v-model="form.contactPhone"
            maxlength="20"
            placeholder="手机号或座机，例如 13800138000 / 010-12345678"
            data-testid="seller-shop-contact-phone"
          />
        </el-form-item>

        <el-form-item label="在线联系方式">
          <el-input
            v-model="form.contactWechat"
            maxlength="64"
            placeholder="微信 / 旺旺"
            data-testid="seller-shop-contact-wechat"
          />
        </el-form-item>

        <div class="flex justify-end">
          <el-button
            type="primary"
            :loading="saving"
            data-testid="seller-shop-save"
            @click="save"
          >
            保存资料
          </el-button>
        </div>
      </el-card>
    </el-form>
  </div>
</template>
