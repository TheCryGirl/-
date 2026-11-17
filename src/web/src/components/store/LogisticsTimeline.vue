<script setup>
import { computed } from 'vue'
import { formatTime } from '../../utils/format'
import { latestFirst } from '../../utils/logistics'
import { LOGISTICS_STATUS_MAP, statusText } from '../../constants/status'

/**
 * 物流时间线（#138）。
 *
 * 只负责渲染，不发起请求：数据由父页面调用 orderApi.logistics 拿到后传进来。
 * 抽成组件是因为买家订单详情与卖家补录抽屉都要展示同一份轨迹，
 * 两处各写一遍必然在「最新一条高亮」「异常标红」上出现两种样式。
 *
 * 三个状态必须分开，不能都画成「暂无数据」：
 *   loading  → 骨架/加载提示
 *   error    → 失败原因 + 重试按钮
 *   空轨迹    → 父页面传入 message，明确说明「已发货，物流更新中」
 */
const props = defineProps({
  tracks: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  /** 无轨迹时的提示文案，由后端返回，前端不自行编造 */
  emptyMessage: { type: String, default: '暂无物流信息' },
})

const emit = defineEmits(['retry'])

/**
 * 最新的轨迹排在视觉最上方：后端按时间正序返回（便于断言与日志阅读），
 * 展示时反转，符合「最新节点高亮」的验收要求。
 * 反转移位由 utils/logistics.js 统一提供，卖家抽屉与这里共用同一份实现。
 */
const ordered = computed(() => latestFirst(props.tracks))

const hasTracks = computed(() => ordered.value.length > 0)
</script>

<template>
  <div>
    <div v-if="loading" class="py-6 text-center text-sm text-gray-400">
      正在加载物流信息…
    </div>

    <el-result v-else-if="error" status="error" title="加载物流信息失败" :sub-title="error">
      <template #extra>
        <el-button type="primary" size="small" data-testid="logistics-retry" @click="emit('retry')">
          重试
        </el-button>
      </template>
    </el-result>

    <el-empty
      v-else-if="!hasTracks"
      :description="emptyMessage"
      :image-size="64"
      data-testid="logistics-empty"
    />

    <el-timeline v-else class="pl-1">
      <el-timeline-item
        v-for="(track, index) in ordered"
        :key="track.trackId"
        :timestamp="formatTime(track.occurredAt)"
        placement="top"
        :type="track.abnormal ? 'danger' : index === 0 ? 'primary' : 'info'"
        :hollow="index !== 0"
        size="normal"
        :data-testid="`logistics-track-${track.trackId}`"
      >
        <div
          class="rounded border px-3 py-2"
          :class="[
            index === 0 ? 'border-blue-200 bg-blue-50' : 'border-gray-100 bg-white',
            track.abnormal ? 'border-red-200 bg-red-50' : '',
          ]"
        >
          <div class="flex flex-wrap items-center gap-2">
            <el-tag
              size="small"
              :type="track.abnormal ? 'danger' : index === 0 ? 'primary' : 'info'"
            >
              {{ statusText(LOGISTICS_STATUS_MAP, track.status, track.statusLabel) }}
            </el-tag>
            <span v-if="index === 0" class="text-xs text-blue-600">最新</span>
          </div>
          <p
            class="mt-1 text-sm break-words"
            :class="track.abnormal ? 'text-red-700' : 'text-gray-800'"
          >
            {{ track.description }}
          </p>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>
