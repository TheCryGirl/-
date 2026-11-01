/**
 * 全站状态枚举与文案的唯一来源。
 *
 * 约定（全组必须遵守）：
 * - 页面里不得自建状态映射表，一律从这里 import
 * - 用相对路径引用，项目未配置 @ 别名：
 *     import { ORDER_STATUS_MAP, statusText } from '../../constants/status'
 * - 遇到未知状态时保留原值显示，不要吞成空字符串
 *
 * 取值来源：已逐一核对 src/views 下现有代码与后端返回，非推测。
 */

/* ---------------- 订单状态 ---------------- */
export const ORDER_STATUS = {
  PENDING_PAYMENT: 'PENDING_PAYMENT',
  PAID: 'PAID',
  SHIPPED: 'SHIPPED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
  REFUNDED: 'REFUNDED',
}

export const ORDER_STATUS_MAP = {
  PENDING_PAYMENT: '待支付',
  // 从买家视角看，订单已付但尚未发货即「待发货」
  PAID: '待发货',
  SHIPPED: '待收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDED: '已退款',
}

/** 订单状态对应的 el-tag type */
export const ORDER_STATUS_TAG = {
  PENDING_PAYMENT: 'warning',
  PAID: 'primary',
  SHIPPED: 'primary',
  COMPLETED: 'success',
  CANCELLED: 'info',
  REFUNDED: 'danger',
}

/* ---------------- 商品状态 ---------------- */
export const PRODUCT_STATUS = {
  PENDING: 'PENDING',
  ON_SALE: 'ON_SALE',
  OFF_SHELF: 'OFF_SHELF',
  REJECTED: 'REJECTED',
}

export const PRODUCT_STATUS_MAP = {
  PENDING: '待审核',
  ON_SALE: '在售',
  OFF_SHELF: '已下架',
  REJECTED: '已驳回',
}

export const PRODUCT_STATUS_TAG = {
  PENDING: 'warning',
  ON_SALE: 'success',
  OFF_SHELF: 'info',
  REJECTED: 'danger',
}

/* ---------------- 用户角色 ---------------- */
export const ROLE = {
  BUYER: 'BUYER',
  SELLER: 'SELLER',
  ADMIN: 'ADMIN',
}

export const ROLE_MAP = {
  BUYER: '买家',
  SELLER: '卖家',
  ADMIN: '管理员',
}

export const ROLE_TAG = {
  BUYER: 'info',
  SELLER: 'success',
  ADMIN: 'danger',
}

/* ---------------- 售后 ---------------- */
export const AFTER_SALE_TYPE = {
  REFUND_ONLY: 'REFUND_ONLY',
  RETURN_REFUND: 'RETURN_REFUND',
}

export const AFTER_SALE_TYPE_MAP = {
  REFUND_ONLY: '仅退款',
  RETURN_REFUND: '退货退款',
}

export const AFTER_SALE_STATUS_MAP = {
  PENDING: '待处理',
  APPROVED: '已同意',
  REJECTED: '已拒绝',
  REFUNDED: '已退款',
  BUYER_SHIPPED: '买家已寄回',
  SELLER_RECEIVED: '卖家已收货',
  CANCELLED: '已取消',
}

export const AFTER_SALE_STATUS_TAG = {
  PENDING: 'warning',
  APPROVED: 'primary',
  REJECTED: 'danger',
  REFUNDED: 'success',
  BUYER_SHIPPED: 'primary',
  SELLER_RECEIVED: 'primary',
  CANCELLED: 'info',
}

/* ---------------- 支付 ---------------- */
export const PAYMENT_STATUS_MAP = {
  PENDING: '待支付',
  SUCCESS: '支付成功',
  FAILED: '支付失败',
  REFUNDED: '已退款',
}

export const PAYMENT_STATUS_TAG = {
  PENDING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
  REFUNDED: 'info',
}

/* ---------------- 通用取值函数 ---------------- */

/**
 * 按映射表取文案；未知状态返回原值，避免把有效数据吞成空白。
 * @param {Object} map 映射表
 * @param {string} code 状态码
 * @param {string} fallback 状态为空时的兜底文案
 */
export function statusText(map, code, fallback = '-') {
  if (code === null || code === undefined || code === '') return fallback
  return map[code] ?? String(code)
}

/** 按映射表取 el-tag type，未知状态回退 info */
export function statusTag(map, code, fallback = 'info') {
  if (!code) return fallback
  return map[code] ?? fallback
}
