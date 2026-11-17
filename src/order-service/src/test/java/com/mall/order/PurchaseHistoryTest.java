package com.mall.order;

import com.mall.order.service.PurchaseHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 最近购买行为（#141）：只统计已支付之后的订单，给推荐链路排除「已购买」用。
 */
@SpringBootTest
@ActiveProfiles("test")
class PurchaseHistoryTest {

    @Autowired
    private PurchaseHistoryService purchaseHistoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM `order`");
    }

    @Test
    void onlyPaidOrdersCountAsPurchasesAndProductIsDeduplicated() {
        insertOrder("O-PAID", "U-BUYER", "PAID", 1);
        insertOrderItem("O-PAID", "P-HOT", "U-BUYER-PAID-1");
        insertOrder("O-SHIPPED", "U-BUYER", "SHIPPED", 2);
        insertOrderItem("O-SHIPPED", "P-HOT", "U-BUYER-SHIPPED-2");
        insertOrderItem("O-SHIPPED", "P-OTHER", "U-BUYER-SHIPPED-3");
        insertOrder("O-PENDING", "U-BUYER", "PENDING_PAYMENT", 3);
        insertOrderItem("O-PENDING", "P-UNPAID", "U-BUYER-PENDING");
        insertOrder("O-CANCELLED", "U-BUYER", "CANCELLED", 4);
        insertOrderItem("O-CANCELLED", "P-CANCELLED", "U-BUYER-CANCELLED");
        insertOrder("O-OTHER-BUYER", "U-OTHER", "PAID", 5);
        insertOrderItem("O-OTHER-BUYER", "P-FOREIGN", "U-OTHER-PAID");

        List<String> productIds = purchaseHistoryService.recentPurchasedProductIds("U-BUYER", 20);

        assertThat(productIds).containsExactlyInAnyOrder("P-HOT", "P-OTHER");
        assertThat(productIds).doesNotContain("P-UNPAID", "P-CANCELLED", "P-FOREIGN");
    }

    @Test
    void emptyBuyerReturnsEmptyList() {
        assertThat(purchaseHistoryService.recentPurchasedProductIds(" ", 20)).isEmpty();
        assertThat(purchaseHistoryService.recentPurchasedProductIds("U-NOBODY", 20)).isEmpty();
    }

    private void insertOrder(String orderId, String buyerId, String status, int minutesAgo) {
        Timestamp createdAt = Timestamp.valueOf(LocalDateTime.now().minusMinutes(minutesAgo));
        jdbcTemplate.update("""
                INSERT INTO `order`(order_id, order_no, buyer_id, seller_id, status, total_amount, pay_amount,
                                    receiver, receiver_phone, address_full, expire_at, created_at, updated_at,
                                    is_deleted, inventory_released)
                VALUES (?, ?, ?, 'U-SELLER', ?, 1000, 1000, '张三', '13800000000', '天津市河北区',
                        ?, ?, ?, 0, 0)
                """, orderId, "NO-" + orderId, buyerId, status, createdAt, createdAt, createdAt);
    }

    private void insertOrderItem(String orderId, String productId, String skuId) {
        jdbcTemplate.update("""
                INSERT INTO order_item(order_id, sku_id, product_id, product_title, spec_name, price_snapshot,
                                       quantity, subtotal, seller_id, created_at, is_deleted)
                VALUES (?, ?, ?, '商品', '默认', 1000, 1, 1000, 'U-SELLER', ?, 0)
                """, orderId, skuId, productId, Timestamp.valueOf(LocalDateTime.now()));
    }
}
