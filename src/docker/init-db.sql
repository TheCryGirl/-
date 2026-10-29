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

-- 关于 root 账号：
--   官方镜像在首次启动时会用 MYSQL_ROOT_PASSWORD 建好 root@'%'，口令与 compose 中的
--   ${DB_PASSWORD} 一致，容器内其他服务可直接连上。
--   这里不再执行 ALTER USER ... IDENTIFIED BY '...'——那会把口令固定成写死的值，
--   覆盖 MYSQL_ROOT_PASSWORD，导致 DB_PASSWORD 设成别的值时各服务连不上。
--
-- 仅授予权限（幂等，不涉及口令）：
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
