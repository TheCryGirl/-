-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    user_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100),
    age VARCHAR(10),
    sex VARCHAR(10),
    address VARCHAR(255),
    phone VARCHAR(20),
    password VARCHAR(100),
    create_time VARCHAR(100)
);

-- 商品表
CREATE TABLE IF NOT EXISTS product (
    product_id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50),
    name VARCHAR(100),
    price DOUBLE,
    description TEXT,
    status VARCHAR(20),
    classification VARCHAR(50),
    create_time VARCHAR(100),
    image_url VARCHAR(255),
    pending_buyer_id VARCHAR(50),
    version INT DEFAULT 0
);

-- 订单表
CREATE TABLE IF NOT EXISTS `order` (
    order_id VARCHAR(50) PRIMARY KEY,
    product_id VARCHAR(50),
    seller_id VARCHAR(50),
    buyer_id VARCHAR(50),
    status VARCHAR(20),
    create_time VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS purchase_request (
    request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id VARCHAR(50),
    seller_id VARCHAR(50),
    buyer_id VARCHAR(50),
    status VARCHAR(20),
    create_time VARCHAR(100)
);

-- 聊天记录表 (用于 ChatMemoryService)
CREATE TABLE IF NOT EXISTS chat_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(100),
    role VARCHAR(20),
    content TEXT
);

-- 用户消息表
CREATE TABLE IF NOT EXISTS `message` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id VARCHAR(50),
    receiver_id VARCHAR(50),
    content TEXT,
    create_time VARCHAR(100),
    is_read BOOLEAN DEFAULT FALSE
);
