/**
 * 结算金额展示（#136）。
 *
 * 后端计价链：商品小计 → 店铺/平台满减、满折 → 优惠券 → 加运费 → 应付合计，
 * 因此 pay = goods - discount + freight。前端**只做展示与元/分换算**，
 * 金额本身一律以后端预览接口为准，不在这里重新算运费或优惠。
 */

/** 元 → 分，四舍五入到整数分；非法输入按 0 处理。 */
export function toCents(yuan) {
  const value = Number(yuan)
  if (!Number.isFinite(value) || value < 0) return 0
  return Math.round(value * 100)
}

/** 分 → 元，保留两位小数的字符串（仅用于展示与输入框回填）。 */
export function toYuan(cents) {
  const value = Number(cents)
  if (!Number.isFinite(value)) return '0.00'
  return (value / 100).toFixed(2)
}

/**
 * 归一化预览金额：预览返回什么就用什么，缺失时退回「商品金额 = 应付」的老口径，
 * 避免预览失败时把页面算成一个莫名其妙的总价。
 */
export function buildSummary(preview, fallbackGoodsAmount = 0) {
  const goods = Number(preview?.totalAmount ?? fallbackGoodsAmount) || 0
  const freight = Number(preview?.freightAmount ?? 0) || 0
  const discount = Number(preview?.discountAmount ?? 0) || 0
  const pay = Number(preview?.payAmount ?? goods - discount + freight) || 0
  return { goods, freight, discount, pay }
}

/** 结算页四行：商品金额 / 运费 / 优惠 / 应付合计。 */
export function summaryRows(summary) {
  return [
    { key: 'goods', label: '商品金额', amount: summary.goods },
    { key: 'freight', label: '运费', amount: summary.freight },
    { key: 'discount', label: '优惠', amount: summary.discount },
    { key: 'pay', label: '应付合计', amount: summary.pay },
  ]
}
