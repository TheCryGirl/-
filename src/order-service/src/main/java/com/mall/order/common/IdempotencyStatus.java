package com.mall.order.common;

public enum IdempotencyStatus {
    /** 已插入幂等记录，尚未取得库存预占的执行权 */
    PROCESSING,
    /** 已抢占库存预占执行权，正在调用 product-service 预占库存 */
    RESERVING,
    /** 库存预占成功且订单已落库，同一幂等键的后续请求直接返回该结果 */
    SUCCESS,
    /** 预占或下单失败，幂等键已释放执行权，允许同一幂等键重试 */
    FAILED
}
