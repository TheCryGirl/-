<script setup>
/**
 * 卖家运费设置（#136）
 *
 * 只维护「包邮门槛 + 分地区运费表」，金额在界面按元输入、按分提交；
 * 运费计算不在前端发生，下单预览一律以后端计价链的结果为准。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import PageHero from '../../components/store/PageHero.vue'
import { isOk, shopApi, unwrapResult } from '../../api'
import { resolveErrorMessage, showError, showSuccess } from '../../components/ui/message'
import { toCents, toYuan } from '../../utils/pricing'

const DEFAULT_REGION = 'DEFAULT'
const REGION_OPTIONS = [
  '北京市', '天津市', '上海市', '重庆市',
  '河北省', '山西省', '辽宁省', '吉林省', '黑龙江省', '江苏省', '浙江省', '安徽省',
  '福建省', '江西省', '山东省', '河南省', '湖北省', '湖南省', '广东省', '海南省',
  '四川省', '贵州省', '云南省', '陕西省', '甘肃省', '青海省', '台湾省',
  '内蒙古自治区', '广西壮族自治区', '西藏自治区', '宁夏回族自治区', '新疆维吾尔自治区',
  '香港特别行政区', '澳门特别行政区', DEFAULT_REGION,
]

const form = reactive({
  name: '默认运费模板',
  freeThresholdYuan: '0.00',
  rules: [],
})
const loading = ref(true)
const loadError = ref('')
const saving = ref(false)
const formError = ref('')
const templateId = ref('')
const updatedAt = ref('')

const isUnconfigured = computed(() => !templateId.value)
const hasRules = computed(() => form.rules.length > 0)
const freeThresholdCents = computed(() => toCents(form.freeThresholdYuan))
const freeShippingNote = computed(() =>
  freeThresholdCents.value > 0
    ? `商品小计满 ¥${toYuan(freeThresholdCents.value)} 免运费`
    : '未设置包邮门槛：所有订单按地区规则收运费',
)

function fill(config) {
  templateId.value = config?.templateId || ''
  form.name = config?.name || '默认运费模板'
  form.freeThresholdYuan = toYuan(config?.freeThreshold || 0)
  form.rules = (config?.rules || []).map((rule) => ({
    regionCode: rule.regionCode || DEFAULT_REGION,
    minAmountYuan: toYuan(rule.minAmount || 0),
    feeYuan: toYuan(rule.fee || 0),
  }))
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await shopApi.mineFreight()
    if (!isOk(data)) {
      loadError.value = data?.message || '加载运费配置失败'
      return
    }
    fill(unwrapResult(data) || {})
  } catch (err) {
    loadError.value = resolveErrorMessage(err, '加载运费配置失败')
  } finally {
    loading.value = false
  }
}

function addRule() {
  form.rules.push({ regionCode: DEFAULT_REGION, minAmountYuan: '0.00', feeYuan: '0.00' })
}

function removeRule(index) {
  form.rules.splice(index, 1)
}

/** 校验：地区不能重复到同一档，金额必须是 ≥0 的数字；返回错误文案或空串。 */
function validate() {
  if (!form.name.trim()) return '模板名称不能为空'
  if (form.name.trim().length > 64) return '模板名称最多 64 个字符'
  const threshold = Number(form.freeThresholdYuan)
  if (!Number.isFinite(threshold) || threshold < 0) return '包邮门槛必须是不小于 0 的数字'
  const seen = new Set()
  for (const rule of form.rules) {
    const region = String(rule.regionCode || '').trim()
    if (!region) return '地区不能为空'
    const min = Number(rule.minAmountYuan)
    const fee = Number(rule.feeYuan)
    if (!Number.isFinite(min) || min < 0) return `「${region}」的最低金额必须是不小于 0 的数字`
    if (!Number.isFinite(fee) || fee < 0) return `「${region}」的运费必须是不小于 0 的数字`
    const key = `${region}|${toCents(min)}`
    if (seen.has(key)) return `「${region}」的最低金额有重复档位`
    seen.add(key)
  }
  return ''
}

async function save() {
  if (saving.value) return
  const message = validate()
  formError.value = message
  if (message) {
    showError(message)
    return
  }
  saving.value = true
  try {
    const { data } = await shopApi.saveFreight({
      name: form.name.trim(),
      freeThreshold: toCents(form.freeThresholdYuan),
      rules: form.rules.map((rule) => ({
        regionCode: String(rule.regionCode).trim(),
        minAmount: toCents(rule.minAmountYuan),
        fee: toCents(rule.feeYuan),
      })),
    })
    if (!isOk(data)) {
      showError(data?.message || '保存运费配置失败')
      return
    }
    fill(unwrapResult(data) || {})
    updatedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
    showSuccess('运费配置已保存')
  } catch (err) {
    showError(resolveErrorMessage(err, '保存运费配置失败'))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-4">
    <PageHero
      title="运费设置"
      subtitle="设置包邮门槛与分地区运费，买家结算时按此计算每家店的运费"
      icon="tag"
      tone="teal"
    />

    <el-result v-if="loadError" status="error" title="加载失败" :sub-title="loadError">
      <template #extra>
        <el-button type="primary" data-testid="seller-freight-retry" @click="load">重试</el-button>
      </template>
    </el-result>

    <div v-else-if="loading" v-loading="true" class="min-h-40" />

    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">包邮与模板</span>
            <span class="text-xs text-gray-400" data-testid="seller-freight-state">
              {{ isUnconfigured ? '尚未配置：未配置时运费按 0 计算' : `模板 ${templateId}` }}
            </span>
          </div>
        </template>

        <el-form label-width="120px" @submit.prevent>
          <el-form-item label="模板名称">
            <el-input
              v-model="form.name"
              maxlength="64"
              show-word-limit
              placeholder="例如：全国包邮 / 默认运费"
              data-testid="seller-freight-name"
            />
          </el-form-item>
          <el-form-item label="包邮门槛（元）">
            <el-input v-model="form.freeThresholdYuan" data-testid="seller-freight-threshold" />
            <p class="text-xs text-gray-500 mt-1">
              {{ freeShippingNote }}（0 表示不包邮，门槛按商品小计判断，不含运费）
            </p>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <template #header>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <span class="text-base font-medium">分地区运费表</span>
            <el-button size="small" data-testid="seller-freight-add" @click="addRule">添加地区</el-button>
          </div>
        </template>

        <el-empty
          v-if="!hasRules"
          description="还没有地区规则：不配置时全国运费为 0；需要收费请点「添加地区」"
          :image-size="80"
        />

        <el-table v-else :data="form.rules" size="small" data-testid="seller-freight-table">
          <el-table-column label="地区" min-width="180">
            <template #default="{ row }">
              <el-select
                v-model="row.regionCode"
                filterable
                allow-create
                default-first-option
                placeholder="省份或 DEFAULT"
                :data-testid="`seller-freight-region-${form.rules.indexOf(row)}`"
              >
                <el-option v-for="region in REGION_OPTIONS" :key="region" :label="region" :value="region" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="最低金额（元）" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.minAmountYuan" />
            </template>
          </el-table-column>
          <el-table-column label="运费（元）" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.feeYuan" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="right">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeRule($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <p class="text-xs text-gray-500 mt-3">
          地区填省级名称（如「河北省」），DEFAULT 表示其它地区兜底；同一地区可配多档，
          按「商品小计达到的最高档」取值。
        </p>
      </el-card>

      <div class="flex flex-wrap items-center justify-between gap-3">
        <p v-if="formError" class="text-xs text-red-500" data-testid="seller-freight-error">{{ formError }}</p>
        <p v-else-if="updatedAt" class="text-xs text-gray-400">最近保存：{{ updatedAt }}</p>
        <span v-else class="text-xs text-gray-400">
          保存后立即生效，买家刷新结算页即可看到新的运费
        </span>
        <el-button type="primary" :loading="saving" data-testid="seller-freight-save" @click="save">
          {{ saving ? '保存中…' : '保存运费配置' }}
        </el-button>
      </div>
    </template>
  </div>
</template>
