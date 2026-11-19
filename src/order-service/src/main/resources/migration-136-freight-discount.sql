-- #136 运费与优惠计价：order-service 侧增量脚本
-- 说明：schema.sql 已包含这两列（新库直接生效）；既有库请手工执行本脚本一次。
-- 逐列检查，兼容两列都没有、仅一列存在以及重复执行。

SET @mall_136_freight_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
           AND TABLE_NAME='order' AND COLUMN_NAME='freight_amount'),
    'SELECT 1',
    'ALTER TABLE `order` ADD COLUMN freight_amount BIGINT NOT NULL DEFAULT 0 COMMENT ''运费，单位分（#136）'' AFTER total_amount'
);
PREPARE mall_136_freight_stmt FROM @mall_136_freight_ddl;
EXECUTE mall_136_freight_stmt;
DEALLOCATE PREPARE mall_136_freight_stmt;

SET @mall_136_discount_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
           AND TABLE_NAME='order' AND COLUMN_NAME='discount_amount'),
    'SELECT 1',
    'ALTER TABLE `order` ADD COLUMN discount_amount BIGINT NOT NULL DEFAULT 0 COMMENT ''优惠抵扣，单位分（#136）'' AFTER freight_amount'
);
PREPARE mall_136_discount_stmt FROM @mall_136_discount_ddl;
EXECUTE mall_136_discount_stmt;
DEALLOCATE PREPARE mall_136_discount_stmt;

-- 历史订单：pay_amount 保持不变（当时未计运费与优惠），两列取默认 0，
-- 因此「订单金额 = 商品总额 + 运费 - 优惠」对历史数据同样成立。
