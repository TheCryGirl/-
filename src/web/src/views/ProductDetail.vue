<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { productApi, purchaseApi } from '../api'
import { auth, isLoggedIn } from '../stores/auth'

const route = useRoute()
const router = useRouter()

const product = ref(null)
const loading = ref(true)
const error = ref('')
const notice = ref('')

const cover = computed(() => {
  const raw = product.value?.image || product.value?.imageUrl || product.value?.picture
  if (!raw) return ''
  return raw.startsWith('http') || raw.startsWith('data:') ? raw : `/images/${raw}`
})

const isMine = computed(
  () =>
    product.value &&
    auth.user?.userId &&
    String(product.value.userId) === String(auth.user.userId),
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await productApi.detail(route.params.id)
    product.value = data
  } catch (err) {
    error.value = err.response?.status === 404 ? '商品不存在或已下架' : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function want() {
  if (!isLoggedIn.value) {
    sessionStorage.setItem('redirectAfterLogin', route.fullPath)
    router.push({ name: 'login' })
    return
  }
  try {
    await purchaseApi.create(route.params.id)
    notice.value = '购买申请已提交，请到「消息」页与卖家确认交易细节。'
  } catch (err) {
    notice.value = err.response?.data?.message || '提交失败，请稍后重试'
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div v-if="loading" class="py-20 text-center text-gray-400 text-sm">加载中…</div>

    <div v-else-if="error" class="py-20 text-center space-y-3">
      <p class="text-gray-500">{{ error }}</p>
      <RouterLink :to="{ name: 'products' }" class="text-blue-600 text-sm hover:underline">
        返回商品列表
      </RouterLink>
    </div>

    <div v-else-if="product" class="space-y-4">
      <nav class="text-sm text-gray-400">
        <RouterLink :to="{ name: 'products' }" class="hover:text-blue-600">商品市场</RouterLink>
        <span class="mx-2">/</span>
        <span class="text-gray-600">{{ product.name }}</span>
      </nav>

      <div class="bg-white rounded-lg border border-gray-200 p-5 grid md:grid-cols-2 gap-6">
        <div>
          <img
            v-if="cover"
            :src="cover"
            :alt="product.name"
            class="w-full rounded-lg object-cover aspect-4/3 bg-gray-100"
          />
          <div
            v-else
            class="w-full rounded-lg aspect-4/3 bg-gray-100 flex items-center justify-center text-gray-400 text-sm"
          >
            暂无图片
          </div>
        </div>

        <div class="flex flex-col gap-3">
          <h1 class="text-xl font-semibold text-gray-900">{{ product.name }}</h1>
          <p class="text-2xl font-bold text-red-600">
            ¥{{ Number(product.price ?? 0).toFixed(2) }}
          </p>

          <dl class="grid grid-cols-[80px_1fr] gap-y-2 text-sm">
            <dt class="text-gray-500">分类</dt>
            <dd class="text-gray-800">{{ product.classification || '未分类' }}</dd>
            <dt class="text-gray-500">卖家</dt>
            <dd class="text-gray-800">{{ product.userId }}</dd>
            <dt class="text-gray-500">发布时间</dt>
            <dd class="text-gray-800">{{ product.createTime || product.createdAt || '—' }}</dd>
          </dl>

          <div>
            <h2 class="text-sm font-medium text-gray-700 mb-1">商品描述</h2>
            <p class="text-sm text-gray-600 whitespace-pre-wrap leading-6">
              {{ product.description || product.desc || '卖家没有填写描述' }}
            </p>
          </div>

          <div class="mt-2 flex gap-2">
            <button
              v-if="!isMine"
              class="px-6 py-2.5 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700"
              @click="want"
            >
              我想要
            </button>
            <span v-else class="text-sm text-gray-400 py-2.5">这是你发布的商品</span>
          </div>

          <p v-if="notice" class="text-sm text-green-600 bg-green-50 rounded-md px-3 py-2">
            {{ notice }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
