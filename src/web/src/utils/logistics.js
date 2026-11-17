/**
 * 物流展示的纯函数（#138）。
 *
 * 抽出来的原因：这些判断会被订单详情、卖家补录抽屉复用，
 * 而且它们最容易出错——「未发货」和「已发货但没轨迹」是两种完全不同的状态，
 * 在页面上都表现为列表为空，只有测试能证明两者没有被混成一句「暂无数据」。
 */

/**
 * 时间线按「最新在上」排列。
 *
 * 后端按 occurredAt 正序返回（便于日志与断言），展示时反转；
 * 这里不改原数组，避免父组件同一份数据被渲染两次时顺序错乱。
 */
export function latestFirst(tracks) {
  return Array.isArray(tracks) ? [...tracks].reverse() : []
}

/**
 * 解析物流区块该显示哪一种状态。
 *
 * 返回值是四种互斥状态，调用方据此分别渲染，不允许把它们都当成「空」：
 *   loading → 请求中
 *   error   → 请求失败，要显示原因和重试
 *   pending → 订单还没发货（有 message 说明）
 *   empty   → 已发货但还没有轨迹（「物流更新中」）
 *   ready   → 有轨迹
 */
export function logisticsState({ loading, error, shipped, tracks } = {}) {
  if (loading) return 'loading'
  if (error) return 'error'
  if (!shipped) return 'pending'
  if (!Array.isArray(tracks) || tracks.length === 0) return 'empty'
  return 'ready'
}

/**
 * 自动确认收货的倒计时文案。
 *
 * 后端给的是分钟数（支持把阈值配成 1 分钟做验收），这里折算成人类可读的时长：
 *   14400 → 「10 天」；14340 → 「9 天 23 小时」；180 → 「3 小时」；1 → 「1 分钟」。
 *
 * 为什么保留次级单位而不是只取整：14340 分钟只显示「9 天」会少算近一天，
 * 刚发货的订单几十分钟后就从「10 天」跳成「9 天」，看起来像催用户。
 * 次级单位为 0 时省略（14400 是「10 天」而不是「10 天 0 小时」）。
 *
 * 返回空串表示不该显示倒计时（未发货、已完成或数据缺失），
 * 让调用方用 v-if 判断，而不是显示一个含义不明的 0。
 */
export function autoConfirmText(minutes) {
  if (minutes === null || minutes === undefined) return ''
  const n = Number(minutes)
  if (!Number.isFinite(n) || n < 0) return ''
  if (n === 0) return '不到 1 分钟'

  const days = Math.floor(n / 1440)
  const hours = Math.floor((n % 1440) / 60)
  const mins = n % 60
  if (days > 0) return hours > 0 ? `${days} 天 ${hours} 小时` : `${days} 天`
  if (hours > 0) return mins > 0 ? `${hours} 小时 ${mins} 分钟` : `${hours} 小时`
  return `${n} 分钟`
}

/**
 * 发货表单的本地校验，与后端保持同一套规则。
 *
 * 后端对「缺快递公司」「缺单号」「快递公司不是枚举值」统一返回 10001，
 * 前端提前拦一道只是省一次往返；真正的判定仍在后端，不能只靠这里。
 */
export function validateShipForm({ expressCompany, trackingNo } = {}, allowedCompanies = []) {
  const company = (expressCompany || '').trim()
  const tracking = (trackingNo || '').trim()
  if (!company) return '请选择快递公司'
  if (allowedCompanies.length && !allowedCompanies.includes(company)) return '快递公司不在可选范围内'
  if (!tracking) return '请输入物流单号'
  if (tracking.length > 64) return '物流单号长度不能超过 64'
  return ''
}
