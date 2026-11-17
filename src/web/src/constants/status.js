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

/* ---------------- 物流履约（#138） ---------------- */

/**
 * 快递公司枚举。
 *
 * 与后端 ExpressCompany 一一对应：发货只接受这里的编码，后端也按编码校验，
 * 两边都不接受自由文本。新增快递公司必须同时改后端枚举，否则发货会被 10001 拒绝。
 */
export const EXPRESS_COMPANIES = [
  { code: 'SF', label: '顺丰速运' },
  { code: 'JD', label: '京东物流' },
  { code: 'ZTO', label: '中通快递' },
  { code: 'YTO', label: '圆通速递' },
  { code: 'STO', label: '申通快递' },
  { code: 'YUNDA', label: '韵达速递' },
  { code: 'JTSD', label: '极兔速递' },
  { code: 'EMS', label: '中国邮政EMS' },
]

export const EXPRESS_COMPANY_MAP = Object.fromEntries(
  EXPRESS_COMPANIES.map((item) => [item.code, item.label]),
)

/** 物流轨迹状态：abnormal 的节点在时间线上标红 */
export const LOGISTICS_STATUS_MAP = {
  COLLECTED: '已揽收',
  IN_TRANSIT: '运输中',
  OUT_FOR_DELIVERY: '派送中',
  SIGNED: '已签收',
  EXCEPTION: '异常件',
}

export const LOGISTICS_STATUS_TAG = {
  COLLECTED: 'info',
  IN_TRANSIT: 'primary',
  OUT_FOR_DELIVERY: 'warning',
  SIGNED: 'success',
  EXCEPTION: 'danger',
}

/** 录入轨迹时可选的顺序，异常件放最后，避免误点 */
export const LOGISTICS_STATUS_OPTIONS = [
  'COLLECTED',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'SIGNED',
  'EXCEPTION',
]

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
