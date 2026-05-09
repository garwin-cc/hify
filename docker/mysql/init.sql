-- MySQL Docker 镜像已根据 MYSQL_DATABASE/MYSQL_USER/MYSQL_PASSWORD 环境变量
-- 创建数据库和用户，此脚本只补充字符集设置。
ALTER DATABASE hify CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
