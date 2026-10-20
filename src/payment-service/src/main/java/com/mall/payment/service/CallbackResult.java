package com.mall.payment.service;

import com.mall.payment.entity.Payment;
import lombok.Getter;

/**
 * 回调本地处理结果。success 为 false 时由控制器转成业务错误码返回，
 * 不抛异常，保证金额不一致等审计流水不会被事务回滚。
 */
@Getter
public class CallbackResult {

    private final boolean success;
    private final boolean duplicate;
    private final boolean newlyPaid;
    private final int code;
    private final String message;
    private final Payment payment;

    private CallbackResult(boolean success, boolean duplicate, boolean newlyPaid,
                           int code, String message, Payment payment) {
        this.success = success;
        this.duplicate = duplicate;
        this.newlyPaid = newlyPaid;
        this.code = code;
        this.message = message;
        this.payment = payment;
    }

    public static CallbackResult newlyPaid(Payment payment) {
        return new CallbackResult(true, false, true, 0, "success", payment);
    }

    public static CallbackResult duplicate(Payment payment) {
        return new CallbackResult(true, true, false, 0, "success", payment);
    }

    public static CallbackResult fail(int code, String message, Payment payment) {
        return new CallbackResult(false, false, false, code, message, payment);
    }
}
