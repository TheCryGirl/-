<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { favoriteApi, isOk, unwrapResult } from '../api'
import { confirmAction } from '../components/ui/confirm'
import { resolveErrorMessage, showError, showSuccess } from '../components/ui/message'
import { usePagination } from '../composables/usePagination'
import { formatPrice, formatTime } from '../utils/format'
import PageHero from '../components/store/PageHero.vue'
import StoreIcon from '../components/store/StoreIcon.vue'

const router = useRouter()

const list = ref([])
const { page, size, total, onPageChange, afterDelete } = usePagination({
  defaultSize: 12,
  onChange: () => load(),
})
const loading = ref(true)
const loadError = ref('')
const removingId = ref(null)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await favoriteApi.list({ page: page.value, size: size.value })
    const payload = unwrapResult(data)
    if (!payload) {
      loadError.value = data?.message || '加载收藏夹失败'
      list.value = []
      total.value = 0
      return
    }
    list.value = payload.list || []
    total.value = payload.total || 0
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载收藏夹失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function openDetail(item) {
  if (item.invalid) return
  router.push({ name: 'product-detail', params: { id: item.productId } })
}

async function remove(item) {
  if (removingId.value !== null) return
  const confirmed = await confirmAction({
    title: '取消收藏',
    message: `取消收藏「${item.title}」？`,
    confirmButtonText: '取消收藏',
    danger: true,
  })
  if (!confirmed) return

  removingId.value = item.productId
  try {
    const { data } = await favoriteApi.remove(item.productId)
    if (!isOk(data)) {
      showError(data?.message || '取消收藏失败')
      return
    }
    showSuccess('已取消收藏')
    afterDelete(total.value - 1)
  } catch (err) {
    showError(resolveErrorMessage(err, '取消收藏失败'))
  } finally {
    removingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <PageHero
      title="我的收藏"
      subtitle="把心动好物先收藏起来，降价或补货后更容易找到"
      icon="heart"
      tone="red"
    >
      <template #meta>
        <span v-if="!loadError" class="text-xs text-[var(--jd-ink-4)]">共 {{ total }} 件</span>
      </template>
      <template #actions>
        <RouterLink :to="{ name: 'products' }" class="jd-link-btn">
          去逛逛
          <StoreIcon name="chevron-right" :size="14" />
        </RouterLink>
      </template>
    </PageHero>

    <el-card v-if="loading" shadow="never">
      <el-skeleton :rows="4" animated />
    </el-card>

    <el-card v-else-if="loadError" shadow="never">
      <el-result status="error" icon="error" title="加载收藏夹失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" data-testid="favorites-retry" @click="load">重试</el-button>
        </template>
      </el-result>
    </el-card>

    <el-card v-else-if="!list.length" shadow="never">
      <el-empty description="收藏夹还是空的">
        <el-button type="primary" @click="router.push({ name: 'products' })">去逛逛</el-button>
      </el-empty>
    </el-card>

    <template v-else>
      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
        <article
          v-for="item in list"
          :key="item.favoriteId"
          class="group bg-white rounded-[14px] border border-[var(--jd-line)] overflow-hidden flex flex-col hover-lift"
          :class="item.invalid ? 'opacity-60' : 'cursor-pointer'"
          @click="openDetail(item)"
        >
          <div class="relative overflow-hidden">
            <img
              v-if="item.mainImage"
              :src="item.mainImage"
              :alt="item.title"
              class="w-full aspect-4/3 object-cover bg-gray-100 transition-transform duration-500 group-hover:scale-[1.06]"
              :class="item.invalid ? 'grayscale' : ''"
            />
            <div
              v-else
              class="w-full aspect-4/3 bg-gray-100 flex items-center justify-center text-gray-400 text-sm"
            >
              暂无图片
            </div>
            <span
              v-if="item.invalid"
              class="absolute inset-0 flex items-center justify-center bg-black/40 text-white text-sm"
            >
              {{ item.invalidReason || '暂不可购买' }}
            </span>
          </div>
          <div class="p-3 space-y-1 flex-1 flex flex-col">
            <p class="text-sm text-gray-800 line-clamp-2">{{ item.title }}</p>
            <p class="text-red-600 font-medium text-sm">¥{{ formatPrice(item.price) }}</p>
            <div class="flex items-center justify-between text-xs text-gray-400 pt-1">
              <span>{{ formatTime(item.createdAt) }}</span>
              <el-button
                link
                type="danger"
                size="small"
                :loading="removingId === item.productId"
                :disabled="removingId !== null && removingId !== item.productId"
                :data-testid="`favorites-remove-${item.productId}`"
                @click.stop="remove(item)"
              >
                取消收藏
              </el-button>
            </div>
          </div>
        </article>
      </div>

      <div v-if="total > size" class="flex items-center justify-center">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          layout="prev, pager, next"
          data-testid="favorites-pagination"
          @current-change="onPageChange"
        />
      </div>
    </template>
  </div>
</template>


