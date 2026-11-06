/**
 * 幂等键生成。
 *
 * 后端下单、支付、售后接口都要求 `Idempotency-Key` 请求头。浏览器只有在
 * secure context（https 或 localhost）下才提供 `crypto.randomUUID`；通过
 * 局域网 IP 访问 dev server 时该 API 不存在，裸调用会抛 TypeError，导致
 * handler 在发出请求前就中断、页面看起来「点了没反应」。
 *
 * 因此统一走这个函数：有 randomUUID 就用，没有就退回时间戳+随机数，
 * 两者都满足后端「非空且足够唯一」的要求。
 */
export function createIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
