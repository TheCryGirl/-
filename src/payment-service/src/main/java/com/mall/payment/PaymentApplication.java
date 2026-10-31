package com.mall.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 在线商城支付服务。
 *
 * <p>负责支付单创建、第三方回调验签与幂等处理、退款申请与审核。
 * 支付状态只以支付平台回调为准，前端与订单服务不能直接修改。
 * 开启定时任务用于支付成功后的对账补偿（见 PaymentReconcileJob）。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.mall.payment.mapper")
@EnableScheduling
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
