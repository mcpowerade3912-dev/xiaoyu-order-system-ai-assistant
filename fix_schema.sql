-- XiaoYu Order System - MySQL 8.x schema
-- Safe for a fresh database. Existing tables are not dropped.

CREATE DATABASE IF NOT EXISTS order_manager
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE order_manager;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NULL,
    phone VARCHAR(11) NULL,
    role TINYINT NOT NULL DEFAULT 0 COMMENT '0=user, 1=admin',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0=frozen, 1=active',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logout_time DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS goods (
    id BIGINT NOT NULL AUTO_INCREMENT,
    goods_name VARCHAR(200) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    pic_url VARCHAR(255) NULL,
    sales INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0=off shelf, 1=on shelf',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_goods_name (goods_name),
    KEY idx_goods_status_create (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_address (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    phone VARCHAR(11) NOT NULL,
    address VARCHAR(255) NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_address_user (user_id, is_default, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_cart (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    goods_id BIGINT NOT NULL,
    buy_num INT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_goods (user_id, goods_id),
    KEY idx_cart_goods (goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_main (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    pay_amount DECIMAL(10, 2) NOT NULL,
    status TINYINT NOT NULL COMMENT '0=pending,1=paid,2=shipped,3=finished,4=cancelled',
    pay_time DATETIME NULL,
    delivery_time DATETIME NULL,
    finish_time DATETIME NULL,
    address_name VARCHAR(50) NOT NULL,
    address_phone VARCHAR(11) NOT NULL,
    address_detail VARCHAR(255) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_main_order_no (order_no),
    KEY idx_order_user_create (user_id, create_time),
    KEY idx_order_status_create (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL,
    goods_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    goods_name VARCHAR(200) NOT NULL,
    goods_price DECIMAL(10, 2) NOT NULL,
    buy_num INT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_item_order_no (order_no),
    KEY idx_order_item_goods (goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    question VARCHAR(500) NOT NULL,
    answer TEXT NULL,
    mode VARCHAR(20) NOT NULL,
    model VARCHAR(100) NULL,
    tool_calls TEXT NULL,
    success TINYINT NOT NULL DEFAULT 1,
    error_message VARCHAR(500) NULL,
    duration_ms BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_log_admin_create (admin_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_setting (
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT NULL,
    is_secret TINYINT NOT NULL DEFAULT 0,
    updated_by BIGINT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
