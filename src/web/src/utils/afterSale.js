/**
 * 售后类型/状态展示与流程步骤，买家页和卖家页共用。
 */

export const afterSaleTypeOptions = [
  { value: 'REFUND_ONLY', label: '仅退款', hint: '卖家同意后直接退款，无需退货' },
  { value: 'RETURN_REFUND', label: '退货退款', hint: '先寄回商品，卖家确认收货后退款' },
  { value: 'EXCHANGE', label: '换货', hint: '先寄回商品，卖家收到后重新发货' },
]

export const afterSaleStatusOptions = [
  { value: 'PENDING', label: '待卖家处理' },
  { value: 'APPROVED', label: '卖家已同意' },
  { value: 'BUYER_SHIPPED', label: '买家已退货' },
  { value: 'SELLER_RECEIVED', label: '卖家已收货' },
  { value: 'REFUNDED', label: '已退款' },
  { value: 'COMPLETED', label: '换货完成' },
  { value: 'REJECTED', label: '已驳回' },
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
}

const statusClasses = {
  PENDING: 'bg-yellow-100 text-yellow-700',
  APPROVED: 'bg-blue-50 text-blue-600',
  BUYER_SHIPPED: 'bg-indigo-50 text-indigo-600',
  SELLER_RECEIVED: 'bg-indigo-50 text-indigo-600',
  REFUNDED: 'bg-green-100 text-green-700',
  COMPLETED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-50 text-red-600',
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

export function afterSaleSteps(type) {
  return stepMap[type]?.steps || []
}

export function afterSaleStepIndex(type, status) {
  if (status === 'REJECTED') return -1
  return stepMap[type]?.index?.[status] ?? 0
}
