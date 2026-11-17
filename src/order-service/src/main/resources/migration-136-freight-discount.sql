-- #136 运费与优惠计价：order-service 侧增量脚本
-- 说明：schema.sql 已包含这两列（新库直接生效）；既有库请手工执行本脚本一次。
-- 重复执行会报 Duplicate column，先确认列是否已存在。

ALTER TABLE `order`
    ADD COLUMN freight_amount BIGINT NOT NULL DEFAULT 0 COMMENT '运费，单位分（#136）' AFTER total_amount;

ALTER TABLE `order`
    ADD COLUMN discount_amount BIGINT NOT NULL DEFAULT 0 COMMENT '优惠抵扣，单位分（#136）' AFTER freight_amount;

-- 历史订单：pay_amount 保持不变（当时未计运费与优惠），两列取默认 0，
-- 因此「订单金额 = 商品总额 + 运费 - 优惠」对历史数据同样成立。
