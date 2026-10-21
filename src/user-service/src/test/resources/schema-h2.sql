-- H2 测试用表结构，与主 schema.sql 保持字段一致
-- 注意：H2 不支持 MySQL 的 UNIQUE KEY 写法，统一用 CONSTRAINT ... UNIQUE

CREATE TABLE IF NOT EXISTS `user` (
    user_id       VARCHAR(32)  NOT NULL,
    account       VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    nickname      VARCHAR(20)  NOT NULL,
    phone         VARCHAR(11)  DEFAULT NULL,
    avatar        VARCHAR(255) DEFAULT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'BUYER',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    VARCHAR(32)  DEFAULT NULL,
    updated_by    VARCHAR(32)  DEFAULT NULL,
    is_deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_account UNIQUE (account),
    CONSTRAINT uk_nickname UNIQUE (nickname)
);

CREATE TABLE IF NOT EXISTS address (
    address_id  VARCHAR(32)  NOT NULL,
    user_id     VARCHAR(32)  NOT NULL,
    receiver    VARCHAR(32)  NOT NULL,
    phone       VARCHAR(11)  NOT NULL,
    province    VARCHAR(32)  NOT NULL,
    city        VARCHAR(32)  NOT NULL,
    district    VARCHAR(32)  DEFAULT NULL,
    detail      VARCHAR(255) NOT NULL,
    is_default  TINYINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (address_id)
);
