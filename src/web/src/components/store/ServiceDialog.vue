<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import StoreIcon from './StoreIcon.vue'

/**
 * 帮助中心弹窗。
 *
 * 顶栏「客户服务」、页脚的配送/支付/发票等说明项都通过 window 事件
 * `help:open` 打开这里，避免出现「点了没反应」或跳到无关页面的假链接。
 */
const open = ref(false)
const topic = ref('all')

const sections = [
  {
    key: 'delivery',
    icon: 'truck',
    title: '配送与时效',
    lines: [
      '本平台为课程实验环境，订单由卖家在后台「待发货」中确认后视为已发货。',
      '演示环境不接入真实物流，物流单号由卖家自行填写，仅用于流程展示。',
      '满 99 元包邮为界面展示规则，实际金额以订单结算页为准。',
    ],
  },
  {
    key: 'payment',
    icon: 'coupon',
    title: '支付与发票',
    lines: [
      '当前只接入 MOCK 模拟支付通道，点击支付不会产生任何真实扣款。',
      '同一幂等键重复提交会返回同一订单，避免重复下单与重复扣库存。',
      '发票信息暂未接入，实验报告中作为后续规划说明。',
    ],
  },
  {
    key: 'after-sales',
    icon: 'refresh',
    title: '售后与退款',
    lines: [
      '确认收货后可在订单详情申请售后：仅退款、退货退款或换货。',
      '卖家审核通过后进入退款流程，管理员可在「售后仲裁」处理争议。',
      '退款到账时间取决于支付渠道，演示环境为即时到账。',
    ],
  },
  {
    key: 'contact',
    icon: 'headset',
    title: '联系客服',
    lines: [
      '服务时间：每天 9:00 - 22:00。',
      '本平台没有真实客服坐席，问题请在课程群或实验报告中反馈。',
      '涉及账号权限（卖家、管理员）请联系课程负责人分配角色。',
    ],
  },
]

const ordered = computed(() => {
  const list = [...sections]
  if (topic.value === 'all') return list
  return [...list].sort((a, b) => (a.key === topic.value ? -1 : b.key === topic.value ? 1 : 0))
})

function onHelp(event) {
  topic.value = event?.detail?.topic || 'all'
  open.value = true
}

onMounted(() => window.addEventListener('help:open', onHelp))
onBeforeUnmount(() => window.removeEventListener('help:open', onHelp))
</script>

<template>
  <el-dialog
    v-model="open"
    width="640px"
    align-center
    append-to-body
    class="help-dialog"
    :show-close="true"
  >
    <template #header>
      <div class="help-dialog__head">
        <span class="help-dialog__badge">
          <StoreIcon name="headset" :size="18" />
        </span>
        <div>
          <h2>帮助中心</h2>
          <p>配送、支付、售后与客服问题的集中说明</p>
        </div>
      </div>
    </template>

    <div class="help-dialog__body">
      <section
        v-for="section in ordered"
        :key="section.key"
        class="help-dialog__section"
        :class="{ 'is-highlight': section.key === topic }"
      >
        <header>
          <StoreIcon :name="section.icon" :size="16" />
          <h3>{{ section.title }}</h3>
        </header>
        <ul>
          <li v-for="line in section.lines" :key="line">{{ line }}</li>
        </ul>
      </section>
    </div>

    <template #footer>
      <div class="help-dialog__footer">
        <RouterLink :to="{ name: 'orders' }" class="jd-link-btn" @click="open = false">
          查看我的订单
        </RouterLink>
        <RouterLink :to="{ name: 'after-sales' }" class="jd-link-btn" @click="open = false">
          去售后中心
        </RouterLink>
        <el-button type="primary" @click="open = false">我知道了</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.help-dialog__head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.help-dialog__badge {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(140deg, #ff6a4d, var(--jd-red));
  color: #fff;
  box-shadow: 0 8px 18px -12px rgba(225, 37, 27, 0.8);
}
.help-dialog__head h2 {
  font-size: 17px;
  font-weight: 800;
  color: var(--jd-ink);
}
.help-dialog__head p {
  font-size: 12px;
  color: var(--jd-ink-4);
  margin-top: 2px;
}
.help-dialog__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 4px;
}
.help-dialog__section {
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--jd-line);
  background: #fbfbfc;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}
.help-dialog__section.is-highlight {
  border-color: #ffd4cf;
  background: linear-gradient(120deg, #fff6f4, #fff);
}
.help-dialog__section header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--jd-red);
}
.help-dialog__section h3 {
  font-size: 14px;
  font-weight: 700;
  color: var(--jd-ink);
}
.help-dialog__section ul {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
  padding-left: 2px;
}
.help-dialog__section li {
  position: relative;
  padding-left: 14px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--jd-ink-3);
}
.help-dialog__section li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #f3b6ae;
}
.help-dialog__footer {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
}
</style>