-- 仅由 Docker Compose 在 MySQL 新数据卷首次初始化时执行。
-- 官方镜像先通过 MYSQL_ROOT_HOST / MYSQL_ROOT_PASSWORD 配置远程 root；
-- 这里保留原有授权，不创建用户、不更改口令。本机建库不执行此文件。
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
