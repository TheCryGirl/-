<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isOk, shopApi, unwrapResult } from '../api'
import PageHero from '../components/store/PageHero.vue'
import { resolveErrorMessage, showError, showSuccess } from '../components/ui/message'
import { formatTotal } from '../utils/format'

const router = useRouter()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 12
const loading = ref(true)
const error = ref('')
const removing = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await shopApi.following({ page: page.value, size })
    if (!isOk(data)) {
      error.value = data?.message || '加载失败'
      list.value = []
      total.value = 0
      return
    }
    const payload = unwrapResult(data)
    list.value = payload?.list || []
    total.value = payload?.total || 0
  } catch (err) {
    error.value = resolveErrorMessage(err, '加载关注的店铺失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function unfollow(shop) {
  if (removing.value) return
  removing.value = shop.shopId
  try {
    const { data } = await shopApi.unfollow(shop.shopId)
    if (!isOk(data)) {
      showError(data?.message || '取消关注失败')
      return
    }
    showSuccess('已取消关注')
    load()
  } catch (err) {
    showError(resolveErrorMessage(err, '取消关注失败'))
  } finally {
    removing.value = ''
  }
}

function goPage(next) {
  page.value = Math.min(totalPages.value, Math.max(1, next))
  load()
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <PageHero
      title="关注的店铺"
      subtitle="关注的店铺更新与暂停营业状态都可以在这里查看"
      icon="heart"
      tone="red"
    >
      <template #meta>
        <span class="text-xs text-[var(--jd-ink-4)]">{{ formatTotal(total, '家') }}</span>
      </template>
    </PageHero>

    <el-card shadow="never">
      <div v-if="loading" class="py-10 text-center text-sm text-gray-400">加载中…</div>
      <el-result v-else-if="error" status="error" title="加载失败" :sub-title="error">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>
      <el-empty v-else-if="!list.length" description="还没有关注任何店铺">
        <el-button type="primary" @click="router.push({ name: 'products' })">去逛逛</el-button>
      </el-empty>

      <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <article
          v-for="shop in list"
          :key="shop.shopId"
          class="flex items-center gap-3 p-3 rounded-lg border border-gray-200 hover:shadow-sm transition-shadow cursor-pointer"
          @click="router.push({ name: 'shop-home', params: { shopId: shop.shopId } })"
        >
          <img
            v-if="shop.logo"
            :src="shop.logo"
            :alt="shop.name"
            class="w-14 h-14 rounded-lg object-cover bg-gray-100"
          />
          <div v-else class="w-14 h-14 rounded-lg bg-gray-100 grid place-items-center text-gray-400 text-xs">店铺</div>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-gray-900 truncate">{{ shop.name }}</p>
            <p class="text-xs text-gray-400 truncate">{{ shop.description || '暂无简介' }}</p>
            <el-tag :type="shop.status === 'SUSPENDED' ? 'danger' : 'success'" size="small" class="mt-1">
              {{ shop.status === 'SUSPENDED' ? '暂停营业' : '正常营业' }}
            </el-tag>
          </div>
          <el-button
            size="small"
            :loading="removing === shop.shopId"
            @click.stop="unfollow(shop)"
          >
            取消关注
          </el-button>
        </article>
      </div>

      <div v-if="totalPages > 1" class="flex justify-center pt-4">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          @current-change="goPage"
        />
      </div>
    </el-card>
  </div>
</template>