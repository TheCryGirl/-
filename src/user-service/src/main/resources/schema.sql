-- 用户服务数据库表
-- 依据《05-接口契约》第二节与第十节。

CREATE TABLE IF NOT EXISTS `user` (
    user_id       VARCHAR(32)  NOT NULL COMMENT '用户ID',
    account       VARCHAR(20)  NOT NULL COMMENT '登录账号，全局唯一',
    password_hash VARCHAR(128) NOT NULL COMMENT 'BCrypt 加盐哈希后的密码',
    nickname      VARCHAR(20)  NOT NULL COMMENT '昵称，全局唯一',
    phone         VARCHAR(11)  DEFAULT NULL COMMENT '手机号',
    avatar        VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    role          VARCHAR(16)  NOT NULL DEFAULT 'BUYER' COMMENT '角色：BUYER/SELLER/ADMIN',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/FROZEN',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    updated_by    VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    is_deleted    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_account (account),
    UNIQUE KEY uk_nickname (nickname),
    KEY idx_role_status (role, status, created_at),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS address (
    address_id  VARCHAR(32)  NOT NULL COMMENT '地址ID',
    user_id     VARCHAR(32)  NOT NULL COMMENT '所属用户ID',
    receiver    VARCHAR(32)  NOT NULL COMMENT '收货人姓名',
    phone       VARCHAR(11)  NOT NULL COMMENT '收货人手机号',
    province    VARCHAR(32)  NOT NULL COMMENT '省/直辖市',
    city        VARCHAR(32)  NOT NULL COMMENT '市/区',
    district    VARCHAR(32)  DEFAULT NULL COMMENT '区/县',
    detail      VARCHAR(255) NOT NULL COMMENT '详细地址',
    is_default  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否默认：0否 1是',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (address_id),
    KEY idx_user_id (user_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';
