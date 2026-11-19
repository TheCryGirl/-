-- 在线商城 - 数据库初始化
--
-- 五个服务各用一个独立的库，禁止跨库查询。
-- 依据《05-接口契约》第十节。
--
-- 各服务自己的建表语句放在各自工程的 src/main/resources/schema.sql，
-- 由 Spring Boot 启动时自动执行。这里只负责创建空的数据库。

CREATE DATABASE IF NOT EXISTS mall_user    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_cart    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_order   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS mall_payment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
