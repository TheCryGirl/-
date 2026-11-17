/**
 * 售后类型/状态展示与流程步骤，买家页和卖家页共用。
 *
 * #143 增量：CANCELLED/ARBITRATING 状态、原因固定枚举与凭证上传助手。
 */
import http from '../api'

export const afterSaleTypeOptions = [
  { value: 'REFUND_ONLY', label: '仅退款', hint: '卖家同意后直接退款，无需退货' },
  { value: 'RETURN_REFUND', label: '退货退款', hint: '先寄回商品，卖家确认收货后退款' },
  { value: 'EXCHANGE', label: '换货', hint: '先寄回商品，卖家收到后重新发货' },
]

export const afterSaleReasonOptions = [
  { value: 'QUALITY', label: '质量问题' },
  { value: 'NOT_AS_DESCRIBED', label: '与描述不符' },
  { value: 'DAMAGED', label: '运输破损' },
  { value: 'WRONG_ITEM', label: '发错货' },
  { value: 'NO_LONGER_NEEDED', label: '不想要了' },
  { value: 'OTHER', label: '其他' },
]

export const afterSaleStatusOptions = [
  { value: 'PENDING', label: '待卖家处理' },
  { value: 'APPROVED', label: '卖家已同意' },
  { value: 'BUYER_SHIPPED', label: '买家已退货' },
  { value: 'SELLER_RECEIVED', label: '卖家已收货' },
  { value: 'REFUNDED', label: '已退款' },
  { value: 'COMPLETED', label: '换货完成' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已撤销' },
  { value: 'ARBITRATING', label: '平台仲裁中' },
]

const typeLabels = {
  REFUND_ONLY: '仅退款',
  RETURN_REFUND: '退货退款',
  EXCHANGE: '换货',
}

const statusLabels = {
  PENDING: '待卖家处理',
  APPROVED: '卖家已同意',
  BUYER_SHIPPED: '买家已退货',
  SELLER_RECEIVED: '卖家已收货',
  REFUNDED: '已退款',
  COMPLETED: '换货完成',
  REJECTED: '已驳回',
  CANCELLED: '已撤销',
  ARBITRATING: '平台仲裁中',
}

const statusClasses = {
  PENDING: 'bg-yellow-100 text-yellow-700',
  APPROVED: 'bg-blue-50 text-blue-600',
  BUYER_SHIPPED: 'bg-indigo-50 text-indigo-600',
  SELLER_RECEIVED: 'bg-indigo-50 text-indigo-600',
  REFUNDED: 'bg-green-100 text-green-700',
  COMPLETED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-50 text-red-600',
  CANCELLED: 'bg-gray-100 text-gray-500',
  ARBITRATING: 'bg-purple-50 text-purple-600',
}

const reasonLabels = {
  QUALITY: '质量问题',
  NOT_AS_DESCRIBED: '与描述不符',
  DAMAGED: '运输破损',
  WRONG_ITEM: '发错货',
  NO_LONGER_NEEDED: '不想要了',
  OTHER: '其他',
}

const stepMap = {
  REFUND_ONLY: {
    steps: ['提交申请', '卖家处理', '退款完成'],
    index: { PENDING: 0, APPROVED: 1, REFUNDED: 2 },
  },
  RETURN_REFUND: {
    steps: ['提交申请', '卖家同意', '买家退货', '退款完成'],
    index: { PENDING: 0, APPROVED: 1, BUYER_SHIPPED: 2, SELLER_RECEIVED: 3, REFUNDED: 3 },
  },
  EXCHANGE: {
    steps: ['提交申请', '卖家同意', '买家退货', '换货完成'],
    index: { PENDING: 0, APPROVED: 1, BUYER_SHIPPED: 2, COMPLETED: 3 },
  },
}

export function afterSaleTypeLabel(type) {
  return typeLabels[type] || type || '未知类型'
}

export function afterSaleStatusLabel(status) {
  return statusLabels[status] || status || '未知状态'
}

export function afterSaleStatusClass(status) {
  return statusClasses[status] || 'bg-gray-100 text-gray-500'
}

export function afterSaleReasonLabel(code) {
  return reasonLabels[code] || code || '—'
}

export function afterSaleSteps(type) {
  return stepMap[type]?.steps || []
}

export function afterSaleStepIndex(type, status) {
  if (status === 'REJECTED' || status === 'CANCELLED' || status === 'ARBITRATING') return -1
  return stepMap[type]?.index?.[status] ?? 0
}

/**
 * 上传售后凭证（#143）：先上传后引用，返回 { code, message, data: [站内路径] }。
 * 单次最多 3 张、单张不超过 5MB、只允许 jpg/jpeg/png/webp/gif，后端兜底校验。
 */
export function uploadAfterSaleEvidence(files) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  return http.post('/payments/after-sales/attachments/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
