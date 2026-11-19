-- 演示账号种子数据（本地演示库专用）
--
-- 作用：别人把项目拉下来、mall_user 库建好之后执行一次，就能直接用下面 5 个账号登录，
--       不需要任何人的真实口令。
-- 目标库：mall_user
-- 演示口令：Demo12345    —— 5 个账号共用，登录页直接输入即可
--
-- 在仓库根目录执行：
--   mysql --host=127.0.0.1 --port=3306 --user=root -p mall_user --execute "source src/docker/seed-demo-accounts.sql"
--
-- 幂等：重复执行只会把演示账号与演示店铺重置为固定状态，不新增重复行，也不碰其它账号。
-- 边界：口令是公开的演示口令，只能用于本地演示。任何真实部署都必须删掉本脚本或改掉口令。

-- 演示买家一号：用于正常下单、购物车、评价等买家流程
INSERT INTO `user` (user_id, account, password_hash, nickname, phone, avatar, role, status,
                    created_at, updated_at, created_by, updated_by, is_deleted)
VALUES ('U-DEMO-0001', 'demo_buyer', '$2a$10$XG9bVaVNu5lznUkewO6SguUhSeJWTDMQF1Gf/LN7atpIKuCm.9cf6',
        '演示买家一号', NULL, NULL, 'BUYER', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM', 0)
ON DUPLICATE KEY UPDATE password_hash = '$2a$10$XG9bVaVNu5lznUkewO6SguUhSeJWTDMQF1Gf/LN7atpIKuCm.9cf6',
                        role = 'BUYER', status = 'ACTIVE', is_deleted = 0, updated_at = NOW();

-- 演示买家二号：用于跨店结算、秒杀限购、并发等需要两个买家的场景
INSERT INTO `user` (user_id, account, password_hash, nickname, phone, avatar, role, status,
                    created_at, updated_at, created_by, updated_by, is_deleted)
VALUES ('U-DEMO-0002', 'demo_buyer2', '$2a$10$XG9bVaVNu5lznUkewO6SguUhSeJWTDMQF1Gf/LN7atpIKuCm.9cf6',
        '演示买家二号', NULL, NULL, 'BUYER', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM', 0)
ON DUPLICATE KEY UPDATE password_hash = '$2a$10$XG9bVaVNu5lznUkewO6SguUhSeJWTDMQF1Gf/LN7atpIKuCm.9cf6',
                        role = 'BUYER', status = 'ACTIVE', is_deleted = 0, updated_at = NOW();

-- 演示卖家一号：店铺已审核通过、正常营业、保证金已登记，可以直接发布商品
INSERT INTO `user` (user_id, account, password_hash, nickname, phone, avatar, role, status,
                    created_at, updated_at, created_by, updated_by, is_deleted)
VALUES ('U-DEMO-0003', 'demo_seller', '$2a$10$XG9bVaVNu5lznUkewO6SguUhSeJWTDMQF1Gf/LN7atpIKuCm.9cf6',
        '演示卖家一号', NULL, NULL, 'SELLER', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM', 0)
ON DUPLICATE KEY UPDATE password_hash = '$2a$10$XG9bVaVNu5lznUkewO6SguUhSeJWTDMQF1Gf/LN7atpIKuCm.9cf6',
                        role = 'SELLER', status = 'ACTIVE', is_deleted = 0, updated_at = NOW();

-- 演示卖家二号：与一号组成两家店，用于跨店合并结算、分店运费/优惠等场景
INSERT INTO `user` (user_id, account, password_hash, nickname, phone, avatar, role, status,
                    created_at, updated_at, created_by, updated_by, is_deleted)
VALUES ('U-DEMO-0004', 'demo_seller2', '$2a$10$XG9bVaVNu5lznUkewO6SguUhSeJWTDMQF1Gf/LN7atpIKuCm.9cf6',
        '演示卖家二号', NULL, NULL, 'SELLER', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM', 0)
ON DUPLICATE KEY UPDATE password_hash = '$2a$10$XG9bVaVNu5lznUkewO6SguUhSeJWTDMQF1Gf/LN7atpIKuCm.9cf6',
                        role = 'SELLER', status = 'ACTIVE', is_deleted = 0, updated_at = NOW();

-- 演示管理员：管理端账号审核、商品审核、售后仲裁、结算与对账都需要它
INSERT INTO `user` (user_id, account, password_hash, nickname, phone, avatar, role, status,
                    created_at, updated_at, created_by, updated_by, is_deleted)
VALUES ('U-DEMO-0005', 'demo_admin', '$2a$10$XG9bVaVNu5lznUkewO6SguUhSeJWTDMQF1Gf/LN7atpIKuCm.9cf6',
        '演示管理员', NULL, NULL, 'ADMIN', 'ACTIVE', NOW(), NOW(), 'SYSTEM', 'SYSTEM', 0)
ON DUPLICATE KEY UPDATE password_hash = '$2a$10$XG9bVaVNu5lznUkewO6SguUhSeJWTDMQF1Gf/LN7atpIKuCm.9cf6',
                        role = 'ADMIN', status = 'ACTIVE', is_deleted = 0, updated_at = NOW();

-- 卖家店铺：审核通过 + 正常营业 + 保证金 1000 元（分），满足发布商品与结算的全部前置条件
INSERT INTO shop (shop_id, seller_id, name, description, status, freight_template_id,
                  apply_status, deposit_amount, created_at, updated_at, is_deleted)
VALUES ('S-DEMO-0001', 'U-DEMO-0003', '演示小店一号', '演示用店铺，可直接发布商品',
        'ACTIVE', NULL, 'APPROVED', 100000, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE status = 'ACTIVE', apply_status = 'APPROVED', deposit_amount = 100000,
                        is_deleted = 0, updated_at = NOW();

INSERT INTO shop (shop_id, seller_id, name, description, status, freight_template_id,
                  apply_status, deposit_amount, created_at, updated_at, is_deleted)
VALUES ('S-DEMO-0002', 'U-DEMO-0004', '演示小店二号', '演示用第二家店，用于跨店合并结算',
        'ACTIVE', NULL, 'APPROVED', 100000, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE status = 'ACTIVE', apply_status = 'APPROVED', deposit_amount = 100000,
                        is_deleted = 0, updated_at = NOW();
