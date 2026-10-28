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

-- 店铺资料与营业状态（#49）。一个卖家一个店铺，seller_id 唯一
CREATE TABLE IF NOT EXISTS shop (
    shop_id        VARCHAR(32)  NOT NULL COMMENT '店铺ID',
    seller_id      VARCHAR(32)  NOT NULL COMMENT '所属卖家用户ID',
    name           VARCHAR(64)  NOT NULL COMMENT '店铺名称',
    logo           VARCHAR(255) DEFAULT NULL COMMENT '店铺头像URL',
    description    VARCHAR(255) DEFAULT NULL COMMENT '店铺简介',
    contact_phone  VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    contact_wechat VARCHAR(64)  DEFAULT NULL COMMENT '在线联系方式（微信/旺旺）',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '营业状态：ACTIVE正常/SUSPENDED已停用',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删 1已删',
    PRIMARY KEY (shop_id),
    UNIQUE KEY uk_shop_seller (seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卖家店铺表';

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

-- 收藏夹（#52）。同一用户同一商品只有一条，重复收藏靠唯一索引保证幂等
CREATE TABLE IF NOT EXISTS favorite (
    favorite_id VARCHAR(32) NOT NULL COMMENT '收藏ID',
    user_id     VARCHAR(32) NOT NULL COMMENT '收藏人用户ID',
    product_id  VARCHAR(32) NOT NULL COMMENT '商品ID（不跨库外键，展示时调 product 接口）',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (favorite_id),
    UNIQUE KEY uk_favorite_user_product (user_id, product_id),
    KEY idx_favorite_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品收藏表';

-- 浏览足迹（#52）。同一商品只保留最后一次浏览时间，实现去重
CREATE TABLE IF NOT EXISTS browse_history (
    history_id VARCHAR(32) NOT NULL COMMENT '足迹ID',
    user_id    VARCHAR(32) NOT NULL COMMENT '用户ID',
    product_id VARCHAR(32) NOT NULL COMMENT '商品ID',
    visited_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次浏览时间',
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_history_user_product (user_id, product_id),
    KEY idx_history_user_time (user_id, visited_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品浏览足迹表';
