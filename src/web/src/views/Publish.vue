<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { productApi } from '../api'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const ok = ref('')

const form = reactive({
  name: '',
  price: '',
  classification: '',
  description: '',
})

const file = ref(null)
const previewUrl = ref('')

const ALLOWED = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
const MAX_BYTES = 5 * 1024 * 1024

function onFileChange(e) {
  error.value = ''
  const f = e.target.files?.[0]
  if (!f) {
    file.value = null
    previewUrl.value = ''
    return
  }
  if (!ALLOWED.includes(f.type)) {
    error.value = '只支持 JPG / PNG / GIF / WebP 格式'
    e.target.value = ''
    return
  }
  if (f.size > MAX_BYTES) {
    error.value = '图片不能超过 5 MB'
    e.target.value = ''
    return
  }
  file.value = f
  previewUrl.value = URL.createObjectURL(f)
}

async function submit() {
  error.value = ''
  ok.value = ''
  if (!form.name.trim()) {
    error.value = '请填写商品名称'
    return
  }
  const price = Number(form.price)
  if (!Number.isFinite(price) || price < 0) {
    error.value = '请填写有效价格'
    return
  }

  loading.value = true
  try {
    const fd = new FormData()
    fd.append('name', form.name.trim())
    fd.append('price', String(price))
    fd.append('classification', form.classification.trim())
    fd.append('description', form.description.trim())
    if (file.value) fd.append('image', file.value)

    await productApi.createWithImage(fd)
    ok.value = '发布成功，正在跳转到我的商品…'
    setTimeout(() => router.push({ name: 'my-products' }), 900)
  } catch (err) {
    error.value = err.response?.data?.message || err.message || '发布失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="max-w-2xl mx-auto bg-white rounded-lg border border-gray-200 p-6">
    <h1 class="text-lg font-semibold text-gray-900 mb-5">发布商品</h1>

    <form class="space-y-4" @submit.prevent="submit">
      <div>
        <label class="block text-sm text-gray-600 mb-1">商品名称 *</label>
        <input v-model="form.name" type="text" maxlength="60" class="field" />
      </div>

      <div class="grid sm:grid-cols-2 gap-4">
        <div>
          <label class="block text-sm text-gray-600 mb-1">价格（元）*</label>
          <input v-model="form.price" type="number" min="0" step="0.01" class="field" />
        </div>
        <div>
          <label class="block text-sm text-gray-600 mb-1">分类</label>
          <input
            v-model="form.classification"
            type="text"
            placeholder="如 教材、数码、生活用品"
            class="field"
          />
        </div>
      </div>

      <div>
        <label class="block text-sm text-gray-600 mb-1">商品描述</label>
        <textarea
          v-model="form.description"
          rows="4"
          maxlength="500"
          class="field resize-y"
          placeholder="成色、入手时间、有无瑕疵等"
        ></textarea>
      </div>

      <div>
        <label class="block text-sm text-gray-600 mb-1">商品图片</label>
        <input
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          class="block text-sm text-gray-600"
          @change="onFileChange"
        />
        <img
          v-if="previewUrl"
          :src="previewUrl"
          alt="预览"
          class="mt-3 w-40 h-30 object-cover rounded-md border border-gray-200"
        />
      </div>

      <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
      <p v-if="ok" class="text-sm text-green-600">{{ ok }}</p>

      <div class="flex gap-2">
        <button
          type="submit"
          class="px-6 py-2.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700 disabled:bg-gray-300"
          :disabled="loading"
        >
          {{ loading ? '发布中…' : '发布' }}
        </button>
        <button
          type="button"
          class="px-6 py-2.5 rounded-md border border-gray-300 text-gray-600 text-sm hover:bg-gray-50"
          @click="router.back()"
        >
          取消
        </button>
      </div>
    </form>
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
