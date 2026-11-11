<script setup>
import PageHero from '../../components/store/PageHero.vue'
import { computed, onMounted, ref } from 'vue'
import { isOk, paymentApi, productApi, unwrapResult, userApi } from '../../api'
import { resolveErrorMessage } from '../../components/ui/message'
import { formatTotal } from '../../utils/format'
import { pageCoverage } from '../../utils/adminFinance'

/**
 * 基础风控：在明确标注的首100条数据样本中应用可解释规则，列出需要人工关注的记录。
 *
 * 规则都是「看数据就能判断」的，不引入黑盒模型，便于答辩解释：
 * 1. 高频退款：同一买家退款单数 ≥ 阈值
 * 2. 大额退款：单笔退款金额 ≥ 阈值
 * 3. 商品价格异常：单价 ≥ 阈值（可能是标错价格）
 * 4. 库存告罄：在售但可售库存为 0
 * 5. 冻结账号仍有在售商品
 */
/* ---------- 1. 状态 ---------- */
const loading = ref(false)
const loadError = ref('')
const coverage = ref(null)
const refunds = ref([])
const products = ref([])
const users = ref([])

/** 规则阈值，可按课程要求调整（展示口径不做任何重算） */
const THRESHOLD = {
  refundCount: 2, // 同一买家退款 ≥ 2 笔
  refundAmount: 50000, // 单笔退款 ≥ 500 元（单位：分）
  productPrice: 100000, // 商品单价 ≥ 1000 元
}

/* ---------- 2. 计算属性 ---------- */
const risks = computed(() => {
  const list = []

  // 规则 1：同一买家高频退款
  const buyerRefund = new Map()
  for (const r of refunds.value) {
    if (r.status === 'REJECTED') continue
    const key = r.buyerId || '未知'
    const cur = buyerRefund.get(key) || { count: 0, amount: 0 }
    cur.count++
    cur.amount += r.amount || 0
    buyerRefund.set(key, cur)
  }
  for (const [buyerId, v] of buyerRefund) {
    if (v.count >= THRESHOLD.refundCount) {
      list.push({
        level: '中',
        rule: '高频退款',
        target: buyerId,
        detail: `样本内 ${v.count} 笔退款，合计 ¥${(v.amount / 100).toFixed(2)}`,
        action: '建议核查是否存在恶意退款',
      })
    }
  }

  // 规则 2：单笔大额退款
  for (const r of refunds.value) {
    if ((r.amount || 0) >= THRESHOLD.refundAmount) {
      list.push({
        level: '高',
        rule: '大额退款',
        target: r.refundId,
        detail: `退款金额 ¥${(r.amount / 100).toFixed(2)}，订单 ${r.orderId}`,
        action: '建议人工复核退款理由',
      })
    }
  }

  // 规则 3：商品价格异常偏高
  for (const p of products.value) {
    if ((p.price || 0) >= THRESHOLD.productPrice) {
      list.push({
        level: '低',
        rule: '价格异常',
        target: p.productId,
        detail: `单价 ¥${(p.price / 100).toFixed(2)}（${p.title}）`,
        action: '确认是否为标价错误',
      })
    }
  }

  // 规则 4：在售但无库存
  for (const p of products.value) {
    if (p.status === 'ON_SALE' && (p.stock || 0) <= 0) {
      list.push({
        level: '低',
        rule: '库存告罄',
        target: p.productId,
        detail: `在售商品「${p.title}」可售库存为 0`,
        action: '提醒卖家补货或下架',
      })
    }
  }

  // 规则 5：冻结账号仍有在售商品
  const frozen = new Set(users.value.filter((u) => u.status !== 'ACTIVE').map((u) => u.userId))
  for (const p of products.value) {
    if (p.status === 'ON_SALE' && frozen.has(p.sellerId)) {
      list.push({
        level: '高',
        rule: '冻结账号在售',
        target: p.productId,
        detail: `卖家 ${p.sellerId} 已冻结，商品「${p.title}」仍在售`,
        action: '下架该商品',
      })
    }
  }

  return list
})

const stats = computed(() => ({
  total: risks.value.length,
  high: risks.value.filter((r) => r.level === '高').length,
  mid: risks.value.filter((r) => r.level === '中').length,
  low: risks.value.filter((r) => r.level === '低').length,
}))

/* ---------- 3. 方法 ---------- */
async function load() {
  if (loading.value) return
  loading.value = true
  loadError.value = ''
  try {
    const [rRes, pRes, uRes] = await Promise.all([
      paymentApi.refunds({ page: 1, size: 100 }),
      productApi.list({ status: 'ON_SALE', page: 1, size: 100 }),
      userApi.list({ page: 1, size: 100 }),
    ])
    const failed = [rRes, pRes, uRes].find((res) => !isOk(res.data))
    if (failed) {
      loadError.value = failed.data?.message || '加载风控数据失败'
      coverage.value = null
      refunds.value = []
      products.value = []
      users.value = []
      return
    }
    const pick = (d) => {
      const p = unwrapResult(d)
      return Array.isArray(p) ? p : p?.list ?? []
    }
    coverage.value = {
      refunds: pageCoverage(unwrapResult(rRes.data)),
      products: pageCoverage(unwrapResult(pRes.data)),
      users: pageCoverage(unwrapResult(uRes.data)),
    }
    refunds.value = pick(rRes.data)
    products.value = pick(pRes.data)
    users.value = pick(uRes.data)
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载风控数据失败')
    coverage.value = null
    refunds.value = []
    products.value = []
    users.value = []
  } finally {
    loading.value = false
  }
}

/** 级别 → el-tag 类型；未知级别回退 info */
function levelTag(level) {
  if (level === '高') return 'danger'
  if (level === '中') return 'warning'
  return 'info'
}

/* ---------- 4. 生命周期 ---------- */
onMounted(load)
</script>

<template>
  <div class="space-y-4">

    <PageHero
      title="基础风控"
      subtitle="退款、在售商品与用户样本风险提示（展示扫描覆盖范围）"
      icon="shield"
      tone="red"
    />
    <el-result v-if="loadError" status="error" icon="error" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>

    <template v-else>
      <el-alert v-if="coverage" type="info" :closable="false" show-icon
        :title="`扫描覆盖：退款 ${coverage.refunds.loaded}/${coverage.refunds.total}；在售商品 ${coverage.products.loaded}/${coverage.products.total}；用户 ${coverage.users.loaded}/${coverage.users.total}`"
        description="每类仅扫描接口返回的前100条，非持续监控或全平台统计；高频退款只在样本内累计，冻结账号匹配也仅覆盖已加载用户。未加载记录不代表没有风险。" />
      <el-card shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">风险概览</span>
            <el-button :loading="loading" @click="load">重新扫描</el-button>
          </div>
        </template>

        <div v-loading="loading" class="grid grid-cols-2 lg:grid-cols-4 gap-3 min-h-16">
          <div class="rounded-lg border border-gray-200 p-3">
            <p class="text-xs text-gray-500">风险项</p>
            <p class="text-xl font-semibold text-gray-900">{{ stats.total }}</p>
          </div>
          <div class="rounded-lg border border-gray-200 p-3">
            <p class="text-xs text-gray-500">高危</p>
            <p class="text-xl font-semibold text-red-600">{{ stats.high }}</p>
          </div>
          <div class="rounded-lg border border-gray-200 p-3">
            <p class="text-xs text-gray-500">中危</p>
            <p class="text-xl font-semibold text-orange-500">{{ stats.mid }}</p>
          </div>
          <div class="rounded-lg border border-gray-200 p-3">
            <p class="text-xs text-gray-500">提示</p>
            <p class="text-xl font-semibold text-gray-500">{{ stats.low }}</p>
          </div>
        </div>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-base font-medium">风险明细</span>
            <span v-if="!loadError" class="text-xs text-gray-400">{{ formatTotal(risks.length, '条') }}</span>
          </div>
        </template>

        <el-table v-loading="loading" :data="risks" border style="width: 100%">
          <el-table-column label="级别" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="levelTag(row.level)" size="small">{{ row.level }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="rule" label="规则" width="140" />
          <el-table-column prop="target" label="涉及对象" min-width="180" />
          <el-table-column prop="detail" label="详情" min-width="240" />
          <el-table-column prop="action" label="建议" min-width="200" />
          <template #empty>
            <el-empty description="当前样本未发现异常（不代表全平台无风险）" :image-size="80" />
          </template>
        </el-table>
      </el-card>
    </template>

    <el-card shadow="never">
      <template #header>
        <span class="text-sm font-medium text-gray-800">当前启用的规则与阈值</span>
      </template>
      <div class="text-xs text-gray-500 space-y-1">
        <p>1. 高频退款：同一买家退款 ≥ {{ THRESHOLD.refundCount }} 笔 → 中危</p>
        <p>2. 大额退款：单笔退款 ≥ ¥{{ (THRESHOLD.refundAmount / 100).toFixed(0) }} → 高危</p>
        <p>3. 价格异常：商品单价 ≥ ¥{{ (THRESHOLD.productPrice / 100).toFixed(0) }} → 提示</p>
        <p>4. 库存告罄：在售但可售库存 = 0 → 提示</p>
        <p>5. 冻结账号在售：卖家已冻结仍有在售商品 → 高危</p>
      </div>
    </el-card>
  </div>
</template>
