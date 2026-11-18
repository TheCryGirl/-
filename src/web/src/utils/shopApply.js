/**
 * 商家入驻与资质审核（#144）的前端展示规则。
 *
 * 页面只按接口返回的 applyStatus / publishable 显示与禁用，
 * 真正的拦截在后端（未过审对外按「店铺停用」处理），这里不构成权限。
 */
import { formatPrice } from './format.js'

/** 入驻主体类型，与后端 ShopApplyRequest.subjectType 允许的取值一致 */
export const subjectTypeOptions = [
  { value: 'PERSONAL', label: '个人' },
  { value: 'INDIVIDUAL', label: '个体工商户' },
  { value: 'ENTERPRISE', label: '企业' },
]

export function subjectTypeText(subjectType) {
  const hit = subjectTypeOptions.find((opt) => opt.value === subjectType)
  return hit ? hit.label : subjectType || '—'
}

/** applyStatus 为空表示卖家还没提交过入驻申请 */
export function applyStatusText(applyStatus) {
  if (!applyStatus) return '未申请'
  if (applyStatus === 'PENDING') return '待审核'
  if (applyStatus === 'APPROVED') return '审核通过'
  if (applyStatus === 'REJECTED') return '已驳回'
  return applyStatus
}

export function applyStatusTag(applyStatus) {
  if (applyStatus === 'APPROVED') return 'success'
  if (applyStatus === 'PENDING') return 'warning'
  if (applyStatus === 'REJECTED') return 'danger'
  return 'info'
}

/** 保证金仅作记录（#144 组长口径）：金额大于 0 视为已缴，0 或空显示未缴纳 */
export function depositText(depositAmount) {
  if (depositAmount === null || depositAmount === undefined || Number(depositAmount) <= 0) {
    return '保证金未缴纳'
  }
  return `已缴保证金 ¥${formatPrice(depositAmount)}`
}

/**
 * 发布商品入口的禁用理由，返回空串表示允许发布。
 *
 * @param {{applyStatus?: string, publishable?: boolean, rejectReason?: string}} shop
 */
export function publishBlockReason(shop) {
  if (shop?.publishable === true) return ''
  if (!shop?.applyStatus) return '请先提交入驻申请，审核通过后才能发布商品'
  if (shop.applyStatus === 'PENDING') return '入驻申请正在审核中，通过后才能发布商品'
  if (shop.applyStatus === 'REJECTED') {
    return shop.rejectReason
      ? `入驻审核已驳回：${shop.rejectReason}，请修改资质后重新提交`
      : '入驻审核未通过，请修改资质后重新提交'
  }
  if (shop.status === 'SUSPENDED') return '店铺已停用，暂不能发布或上架商品'
  if (shop.applyStatus === 'APPROVED' && shop.depositPaid === false) {
    return '保证金尚未登记，请联系管理员确认后再发布或上架商品'
  }
  return '当前店铺不能发布商品'
}

/** 卖家能否维护店铺资料：只有审核通过后后端才接受 PUT /shops/mine */
export function canEditShopInfo(shop) {
  return !!shop && shop.applyStatus === 'APPROVED'
}
