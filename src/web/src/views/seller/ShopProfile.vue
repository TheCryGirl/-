<script setup>
import PageHero from '../../components/store/PageHero.vue'
/**
 * 卖家店铺资料（#81 T06）
 *
 * 上传仍走原来的原生 file input + FormData → productApi.uploadImage：
 * 不换成 Element 的上传组件，避免它自带的 action 与自定义上传叠加出两次请求；
 * 5MB 限制、accept、返回体解析（{ url }）与失败可重试都保持原样。
 *
 * 表单字段、校验规则、保存接口（PUT /shops/mine）与清空语义均不变。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { isOk, productApi, shopApi, unwrapResult } from '../../api'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import {
  applyStatusTag,
  applyStatusText,
  canEditShopInfo,
  depositText,
  publishBlockReason,
  subjectTypeOptions,
} from '../../utils/shopApply'

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

/* ---------- 1.2 入驻申请状态（#144） ---------- */
/** GET /shops/mine 的原始返回，审核状态、驳回理由与保证金口径都取这里 */
const mine = ref({})
const applyForm = reactive({
  name: '',
  contactPhone: '',
  contactWechat: '',
  subjectType: 'PERSONAL',
  licenseNo: '',
  materials: [],
})
const applyRef = ref(null)
const applying = ref(false)
const materialInputRef = ref(null)
const materialUrl = ref('')
const uploadingMaterial = ref(false)

const needApply = computed(() => !canEditShopInfo(mine.value))
const applyLabel = computed(() => applyStatusText(mine.value.applyStatus))
const publishHint = computed(() => publishBlockReason(mine.value))
const depositHint = computed(() => depositText(mine.value.depositAmount))

const applyRules = {
  name: [
    { required: true, message: '店铺名称不能为空', trigger: 'blur' },
    { max: 64, message: '店铺名称最多 64 字', trigger: 'blur' },
  ],
  subjectType: [{ required: true, message: '请选择主体类型', trigger: 'change' }],
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

/** 店名与联系方式在审核前也按卖家已填的内容回显，避免每次重填 */
function prefillApply(shop) {
  applyForm.name = shop.name || ''
  applyForm.contactPhone = shop.contactPhone || ''
  applyForm.contactWechat = shop.contactWechat || ''
  applyForm.subjectType = shop.subjectType || 'PERSONAL'
  applyForm.licenseNo = shop.licenseNo || ''
  applyForm.materials = Array.isArray(shop.materials) ? [...shop.materials] : []
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
    mine.value = shop
    fill(shop)
    prefillApply(shop)
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

function pickMaterial() {
  materialInputRef.value?.click()
}

async function handleMaterialChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || uploadingMaterial.value) return
  if (file.size > 5 * 1024 * 1024) {
    showError('图片不能超过 5MB')
    return
  }
  if (applyForm.materials.length >= 9) {
    showError('资质材料最多 9 项')
    return
  }

  uploadingMaterial.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await productApi.uploadImage(formData)
    const uploaded = isOk(data) ? unwrapResult(data) : null
    if (!uploaded?.url) {
      showError(data?.message || '资质材料上传失败')
      return
    }
    applyForm.materials.push(uploaded.url)
  } catch (err) {
    showError(resolveErrorMessage(err, '资质材料上传失败'))
  } finally {
    uploadingMaterial.value = false
  }
}

function addMaterialUrl() {
  const url = materialUrl.value.trim()
  if (!url) return
  if (applyForm.materials.length >= 9) {
    showError('资质材料最多 9 项')
    return
  }
  applyForm.materials.push(url)
  materialUrl.value = ''
}

function removeMaterial(index) {
  applyForm.materials.splice(index, 1)
}

async function submitApply() {
  if (applying.value) return
  try {
    await applyRef.value?.validate()
  } catch {
    return
  }

  applying.value = true
  try {
    const { data } = await shopApi.apply({
      name: applyForm.name.trim(),
      contactPhone: applyForm.contactPhone,
      contactWechat: applyForm.contactWechat,
      subjectType: applyForm.subjectType,
      licenseNo: applyForm.licenseNo,
      materials: applyForm.materials,
    })
    if (!isOk(data)) {
      showError(data?.message || '提交入驻申请失败')
      return
    }
    const shop = unwrapResult(data) || {}
    mine.value = shop
    fill(shop)
    showSuccess('入驻申请已提交，审核通过后即可维护资料并发布商品')
  } catch (err) {
    showError(resolveErrorMessage(err, '提交入驻申请失败'))
  } finally {
    applying.value = false
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
    const shop = unwrapResult(data)
    if (shop) {
      mine.value = shop
      fill(shop)
    }
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

    <PageHero
      title="店铺资料"
      subtitle="维护店铺名称、公告与营业状态，保存后立即对买家生效"
      icon="grid"
      tone="indigo"
    >
      <template #meta>
        <el-tag :type="applyStatusTag(mine.applyStatus)" size="small" data-testid="seller-apply-status">
          {{ applyLabel }}
        </el-tag>
        <el-tag v-if="form.status === 'SUSPENDED'" type="danger" size="small">已被管理员停用</el-tag>
        <el-tag v-else-if="form.status === 'ACTIVE'" type="success" size="small">正常营业</el-tag>
      </template>
    </PageHero>

    <el-result v-if="loadError" status="error" title="加载店铺资料失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="seller-shop-retry" @click="load">重试</el-button>
      </template>
    </el-result>

    <el-card v-else-if="needApply" shadow="never" data-testid="seller-apply-card">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="text-base font-medium">入驻申请</span>
          <span class="text-xs text-gray-400">{{ depositHint }}</span>
        </div>
      </template>

      <el-alert
        v-if="mine.applyStatus === 'PENDING'"
        type="warning"
        :closable="false"
        show-icon
        class="mb-4"
        title="入驻申请正在审核中。审核通过前不能维护店铺资料，也不能发布商品。"
      />
      <el-alert
        v-else-if="mine.applyStatus === 'REJECTED'"
        type="error"
        :closable="false"
        show-icon
        class="mb-4"
        :title="publishHint"
      />
      <el-alert
        v-else
        type="info"
        :closable="false"
        show-icon
        class="mb-4"
        title="提交入驻申请并由管理员审核通过后，才能维护店铺资料、发布商品。"
      />

      <el-form ref="applyRef" v-loading="loading" :model="applyForm" :rules="applyRules" label-width="108px">
        <el-form-item label="店铺名称" prop="name">
          <el-input
            v-model="applyForm.name"
            maxlength="64"
            show-word-limit
            placeholder="必填，最多 64 字"
            data-testid="seller-apply-name"
          />
        </el-form-item>

        <el-form-item label="主体类型" prop="subjectType">
          <el-select v-model="applyForm.subjectType" style="width: 200px" data-testid="seller-apply-subject">
            <el-option v-for="opt in subjectTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="证照编号">
          <el-input
            v-model="applyForm.licenseNo"
            maxlength="64"
            placeholder="营业执照 / 统一社会信用代码，个人可留空"
            data-testid="seller-apply-license"
          />
        </el-form-item>

        <el-form-item label="资质材料">
          <div class="w-full space-y-2">
            <div v-if="applyForm.materials.length" class="flex flex-wrap gap-1">
              <el-tag
                v-for="(url, index) in applyForm.materials"
                :key="`${url}-${index}`"
                type="info"
                closable
                data-testid="seller-apply-material"
                @close="removeMaterial(index)"
              >
                {{ url }}
              </el-tag>
            </div>

            <div class="flex items-center gap-3">
              <el-button :loading="uploadingMaterial" data-testid="seller-apply-material-pick" @click="pickMaterial">
                {{ uploadingMaterial ? '上传中…' : '上传材料' }}
              </el-button>
              <input
                ref="materialInputRef"
                type="file"
                accept="image/*"
                class="hidden"
                :disabled="uploadingMaterial"
                data-testid="seller-apply-material-file"
                @change="handleMaterialChange"
              />
              <span class="text-xs text-gray-400">已添加 {{ applyForm.materials.length }}/9</span>
            </div>

            <div class="flex items-center gap-2">
              <el-input
                v-model="materialUrl"
                maxlength="255"
                placeholder="也可以填写图片地址后添加，例如 /images/license.png"
                data-testid="seller-apply-material-url"
              />
              <el-button link type="primary" data-testid="seller-apply-material-add" @click="addMaterialUrl">
                添加
              </el-button>
            </div>

            <p class="text-xs text-gray-400">身份证、营业执照等照片，单张不超过 5MB；提交后管理员只能查看，不能修改。</p>
          </div>
        </el-form-item>

        <el-form-item label="联系电话" prop="contactPhone">
          <el-input
            v-model="applyForm.contactPhone"
            maxlength="20"
            placeholder="手机号或座机，例如 13800138000 / 010-12345678"
            data-testid="seller-apply-phone"
          />
        </el-form-item>

        <el-form-item label="在线联系方式">
          <el-input
            v-model="applyForm.contactWechat"
            maxlength="64"
            placeholder="微信 / 旺旺"
            data-testid="seller-apply-wechat"
          />
        </el-form-item>

        <div class="flex justify-end">
          <el-button
            type="primary"
            :loading="applying"
            data-testid="seller-apply-submit"
            @click="submitApply"
          >
            {{ mine.applyStatus === 'REJECTED' ? '重新提交申请' : '提交入驻申请' }}
          </el-button>
        </div>
      </el-form>
    </el-card>

    <el-form v-else ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="96px">
      <el-card shadow="never">
        <template #header>
          <span class="text-base font-medium">店铺资料</span>
        </template>

        <el-alert
          v-if="publishHint"
          type="warning"
          :closable="false"
          show-icon
          class="mb-4"
          :title="`${publishHint}（${depositHint}）`"
          data-testid="seller-shop-publish-hint"
        />
        <el-alert
          v-else-if="form.status === 'SUSPENDED'"
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
              >
                <template #error>
                  <div class="w-16 h-16 rounded-md bg-gray-100 text-xs text-gray-400 flex items-center justify-center">
                    加载失败
                  </div>
                </template>
              </el-image>
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
