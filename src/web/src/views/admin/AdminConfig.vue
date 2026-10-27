<script setup>
import { computed, onMounted, ref } from 'vue'
import { productApi, unwrapResult } from '../../api'

/**
 * 系统配置：展示平台关键参数，支持管理员在前端调整并保存在本机。
 *
 * 说明：这些参数目前不对应后端配置中心，改动保存在 localStorage，
 * 用于演示配置项的结构与管理流程。接 Nacos 配置中心后，
 * 把这里的读写换成配置中心接口即可，页面结构不用动。
 */
const STORAGE_KEY = 'mall.sysConfig'

const defaults = {
  orderTimeoutMinutes: 30, // 订单支付超时（分钟）
  autoConfirmDays: 15, // 发货后自动确认收货（天）
  maxCartQuantity: 999, // 单个 SKU 购物车数量上限
  maxSkuPerProduct: 20, // 单个商品 SKU 数量上限
  maxImageMb: 5, // 商品图片大小上限（MB）
  lowStockThreshold: 5, // 库存预警线
  enableMockPayment: true, // 是否启用模拟支付
  enableSellerSelfAudit: false, // 卖家发布是否免审核
}

const config = ref({ ...defaults })
const saved = ref(false)
const error = ref('')
const categories = ref([])

const groups = [
  {
    title: '交易规则',
    items: [
      {
        key: 'orderTimeoutMinutes',
        label: '订单支付超时',
        unit: '分钟',
        hint: '超时后系统自动取消订单并释放预占库存（BR-16）',
        type: 'number',
      },
      {
        key: 'autoConfirmDays',
        label: '自动确认收货',
        unit: '天',
        hint: '发货后超过该天数未确认，系统自动确认收货（BR-19）',
        type: 'number',
      },
    ],
  },
  {
    title: '商品与库存',
    items: [
      {
        key: 'maxCartQuantity',
        label: '购物车单 SKU 上限',
        unit: '件',
        hint: '同时受可售库存约束（BR-10）',
        type: 'number',
      },
      {
        key: 'maxSkuPerProduct',
        label: '单商品 SKU 上限',
        unit: '个',
        hint: '限制规格组合数量（BR-05）',
        type: 'number',
      },
      {
        key: 'maxImageMb',
        label: '图片大小上限',
        unit: 'MB',
        hint: '超过该大小拒绝上传',
        type: 'number',
      },
      {
        key: 'lowStockThreshold',
        label: '库存预警线',
        unit: '件',
        hint: '低于该值时在风控页提示补货',
        type: 'number',
      },
    ],
  },
  {
    title: '功能开关',
    items: [
      {
        key: 'enableMockPayment',
        label: '启用模拟支付',
        hint: '关闭后仅保留真实支付渠道',
        type: 'boolean',
      },
      {
        key: 'enableSellerSelfAudit',
        label: '卖家发布免审核',
        hint: '关闭时商品需经管理员审核才能上架（BR-07）',
        type: 'boolean',
      },
    ],
  },
]

function load() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      config.value = { ...defaults, ...JSON.parse(raw) }
    }
  } catch {
    config.value = { ...defaults }
  }
}

function save() {
  error.value = ''
  // 简单校验：数值必须为正
  for (const g of groups) {
    for (const item of g.items) {
      if (item.type !== 'number') continue
      const v = Number(config.value[item.key])
      if (!Number.isFinite(v) || v <= 0) {
        error.value = `「${item.label}」必须是大于 0 的数字`
        return
      }
      config.value[item.key] = v
    }
  }
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(config.value))
    saved.value = true
    setTimeout(() => (saved.value = false), 2000)
  } catch {
    error.value = '保存失败，浏览器可能禁用了本地存储'
  }
}

function reset() {
  config.value = { ...defaults }
  save()
}

async function loadCategories() {
  try {
    const { data } = await productApi.categories()
    const payload = unwrapResult(data)
    categories.value = Array.isArray(payload) ? payload : payload?.list || []
  } catch {
    categories.value = []
  }
}

const flatCategories = computed(() =>
  categories.value.map((c) => ({
    id: c.categoryId,
    name: c.name,
    parent: c.parentId || '（顶级）',
  })),
)

onMounted(() => {
  load()
  loadCategories()
})
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-gray-900">系统配置</h1>
      <div class="flex gap-2">
        <button
          class="px-4 py-2 text-sm rounded-md border border-gray-300 text-gray-600 hover:bg-gray-50"
          @click="reset"
        >
          恢复默认
        </button>
        <button
          class="px-4 py-2 text-sm rounded-md bg-blue-600 text-white hover:bg-blue-700"
          @click="save"
        >
          保存配置
        </button>
      </div>
    </div>

    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
    <p v-if="saved" class="text-sm text-green-600">配置已保存</p>

    <section
      v-for="g in groups"
      :key="g.title"
      class="bg-white rounded-lg border border-gray-200 p-4 space-y-3"
    >
      <h2 class="text-sm font-medium text-gray-800">{{ g.title }}</h2>

      <div
        v-for="item in g.items"
        :key="item.key"
        class="flex items-start justify-between gap-4 py-1.5 border-b border-gray-50 last:border-0"
      >
        <div class="flex-1">
          <p class="text-sm text-gray-800">{{ item.label }}</p>
          <p class="text-xs text-gray-400 mt-0.5">{{ item.hint }}</p>
        </div>

        <div class="flex items-center gap-2 shrink-0">
          <template v-if="item.type === 'number'">
            <input
              v-model.number="config[item.key]"
              type="number"
              min="1"
              class="w-24 px-2 py-1 text-sm text-right border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <span class="text-xs text-gray-500 w-10">{{ item.unit }}</span>
          </template>
          <template v-else>
            <button
              class="w-12 h-6 rounded-full transition-colors relative"
              :class="config[item.key] ? 'bg-blue-600' : 'bg-gray-300'"
              @click="config[item.key] = !config[item.key]"
            >
              <span
                class="absolute top-0.5 w-5 h-5 bg-white rounded-full transition-transform"
                :class="config[item.key] ? 'translate-x-6' : 'translate-x-0.5'"
              ></span>
            </button>
            <span class="text-xs text-gray-500 w-10">
              {{ config[item.key] ? '开' : '关' }}
            </span>
          </template>
        </div>
      </div>
    </section>

    <section class="bg-white rounded-lg border border-gray-200 p-4">
      <h2 class="text-sm font-medium text-gray-800 mb-3">商品分类</h2>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead class="bg-gray-50 text-gray-600">
            <tr>
              <th class="text-left px-3 py-2 font-medium">分类 ID</th>
              <th class="text-left px-3 py-2 font-medium">名称</th>
              <th class="text-left px-3 py-2 font-medium">上级分类</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-for="c in flatCategories" :key="c.id">
              <td class="px-3 py-2 text-gray-500 text-xs">{{ c.id }}</td>
              <td class="px-3 py-2 text-gray-800">{{ c.name }}</td>
              <td class="px-3 py-2 text-gray-500 text-xs">{{ c.parent }}</td>
            </tr>
            <tr v-if="!flatCategories.length">
              <td colspan="3" class="px-3 py-6 text-center text-gray-400">暂无分类</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="text-xs text-gray-400 mt-2">
        分类由 product-service 建表时初始化（契约 9.5），此处只读展示。
      </p>
    </section>

    <p class="text-xs text-gray-400">
      说明：以上参数目前保存在浏览器本地，用于演示配置项结构；
      接入 Nacos 配置中心后改为读写远端配置即可。
    </p>
  </div>
</template>
