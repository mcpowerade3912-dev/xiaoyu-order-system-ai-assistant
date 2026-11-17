# 不汐汐 Order System

详细操作请查看：[使用说明](使用说明.md)。

A full-featured e-commerce order management system built with Spring Boot 2.7, MyBatis-Plus, and JWT authentication. It supports a complete shopping workflow including user registration, product browsing, cart management, order placement, and an admin dashboard for managing products, orders, and users.

## Key Features

**User Side**
- User registration, login, and logout with JWT token authentication
- Product browsing with pagination and keyword search
- Shopping cart management (add, update quantity, view)
- Multiple shipping addresses with default address support
- Order lifecycle: create, pay, cancel, ship, confirm receipt, and view detail
- Simulated payment plus automatic closure of unpaid orders after the configured timeout

**Admin Side**
- Product CRUD with shelf status management (on/off)
- Order management with detail lookup, delivery operations, and real-time statistics
- User management with account freeze/unfreeze capability
- Read-only AI operations assistant for natural-language order, stock, and sales analysis
- Integrated administrator web workspace for overview, orders, inventory, users, and AI

**Technical Highlights**
- JWT-based stateless authentication with role-based access control (user vs admin)
- Atomic conditional updates for stock deduction and order state transitions
- ThreadLocal-based user context propagation across the request lifecycle
- Global exception handling with unified JSON response format
- Complete, repeatable MySQL schema initialization
- Optional OpenAI Responses API integration with a zero-key local fallback mode

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 17 |
| Framework | Spring Boot | 2.7.18 |
| ORM | MyBatis-Plus | 3.4.1 |
| Database | MySQL | 8.x |
| Connection Pool | Druid | 1.1.22 |
| Authentication | JWT (jjwt) | 0.11.5 |
| Password Encryption | Spring Security Crypto | - |
| Parameter Validation | Hibernate Validator | - |
| Build Tool | Maven | 3.6+ |
| Utility | Lombok | 1.18.46 |

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      Client Layer                        │
│              (Postman / Frontend / cURL)                 │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP
┌────────────────────────▼────────────────────────────────┐
│                   Interceptor Layer                       │
│            JwtAuthInterceptor (JWT Auth + RBAC)           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ 1. Extract JWT from Authorization header             │ │
│  │ 2. Validate token & check user logout status         │ │
│  │ 3. Role check: /admin/** requires role=1             │ │
│  │ 4. Store userId & role in ThreadLocal UserContext    │ │
│  └─────────────────────────────────────────────────────┘ │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                   Controller Layer                        │
│  ┌──────────┬──────────┬──────────┬──────────┬────────┐ │
│  │  User    │  Goods   │  Cart    │ Address  │ Order  │ │
│  │Controller│Controller│Controller│Controller│Controller│ │
│  └──────────┴──────────┴──────────┴──────────┴────────┘ │
│  ┌──────────────────────────────────────────────────┐   │
│  │  AdminController │ AdminOrder │ AdminUser         │   │
│  └──────────────────────────────────────────────────┘   │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                    Service Layer                          │
│       (Business logic, transactions, validation)          │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                     DAO Layer                             │
│            (MyBatis-Plus Mapper Interface)                │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                   MySQL (Druid Pool)                      │
│  ┌────────┬───────┬──────────┬───────────┬────────────┐ │
│  │sys_user│ goods │ user_cart│user_address│ order_main │ │
│  │        │       │          │           │ order_item │ │
│  └────────┴───────┴──────────┴───────────┴────────────┘ │
└─────────────────────────────────────────────────────────┘
```

## Project Structure

```
order_system/
├── pom.xml                                          # Maven dependencies
├── fix_schema.sql                                   # Database initialization script
├── README.md
├── docs/
│   └── Address接口测试文档.md
├── test-toolkit/
│   ├── postman/                                     # Postman collection
│   ├── junit5/                                      # JUnit 5 test cases
│   └── jmeter/                                      # JMeter stress test config
└── src/
    ├── main/
    │   ├── java/com/xiaoyu/
    │   │   ├── OrderSystemApplication.java          # Spring Boot entry point
    │   │   │
    │   │   ├── common/                              # Common components
    │   │   │   ├── Code.java                        #   Response status codes
    │   │   │   ├── Result.java                      #   Unified response wrapper
    │   │   │   ├── BusinessException.java           #   Transaction-safe business error
    │   │   │   └── GlobalExceptionHandler.java      #   Global exception handler
    │   │   │
    │   │   ├── config/                              # Configuration
    │   │   │   ├── SpringMvcConfig.java             #   MVC config & interceptor registration
    │   │   │   ├── MybatisplusConfig.java           #   Pagination & optimistic lock
    │   │   │   ├── PasswordConfig.java              #   BCrypt password encoder bean
    │   │   │
    │   │   ├── interceptor/
    │   │   │   └── JwtAuthInterceptor.java          # JWT auth + role-based access control
    │   │   │
    │   │   ├── controller/                          # REST API controllers
    │   │   │   ├── UserController.java              #   /user - login, register, logout
    │   │   │   ├── GoodsController.java             #   /goods - product browsing
    │   │   │   ├── CartController.java              #   /cart - shopping cart
    │   │   │   ├── AddressController.java           #   /address - shipping addresses
    │   │   │   ├── UserOrderController.java         #   /order - user order operations
    │   │   │   ├── AdminController.java             #   /admin - product management
    │   │   │   ├── AdminOrderController.java        #   /admin/order - order management
    │   │   │   └── AdminUserController.java         #   /admin/user - user management
    │   │   │
    │   │   ├── service/                             # Business logic
    │   │   │   ├── UserService.java
    │   │   │   ├── GoodsService.java
    │   │   │   ├── CartService.java
    │   │   │   ├── AddressService.java
    │   │   │   ├── OrderService.java
    │   │   │   ├── AdminUserService.java
    │   │   │   └── impl/                            #   Service implementations
    │   │   │
    │   │   ├── Dao/                                 # Data access (MyBatis-Plus Mappers)
    │   │   │   ├── UserDao.java
    │   │   │   ├── GoodsDao.java
    │   │   │   ├── CartDao.java
    │   │   │   ├── UserAddressDao.java
    │   │   │   ├── OrderMainDao.java
    │   │   │   └── OrderItemDao.java
    │   │   │
    │   │   ├── pojo/                                # Database entity classes
    │   │   │   ├── User.java                        #   sys_user table
    │   │   │   ├── Goods.java                       #   goods table
    │   │   │   ├── UserCart.java                     #   user_cart table
    │   │   │   ├── UserAddress.java                 #   user_address table
    │   │   │   ├── Order_main.java                  #   order_main table
    │   │   │   └── Order_item.java                  #   order_item table
    │   │   │
    │   │   ├── dto/                                 # Data Transfer Objects (request)
    │   │   │   ├── LoginDto.java
    │   │   │   ├── UserRegisterDto.java
    │   │   │   ├── GoodsDto.java
    │   │   │   ├── CartDto.java
    │   │   │   ├── AddressDTO.java
    │   │   │   ├── OrderMainDto.java
    │   │   │   ├── OrderItemDto.java
    │   │   │   └── OrderQueryDto.java
    │   │   │
    │   │   ├── vo/                                  # View Objects (response)
    │   │   │   ├── LoginVo.java
    │   │   │   ├── CartVo.java
    │   │   │   ├── OrderCreateVO.java
    │   │   │   ├── OrderDetailVO.java
    │   │   │   └── OrderStatisticsVO.java
    │   │   │
    │   │   └── utils/
    │   │       ├── JwtUtil.java                     # JWT token generation & parsing
    │   │       └── UserContext.java                 # ThreadLocal user context
    │   │
    │   └── resources/
    │       └── application.yml                      # Application configuration
    │
    └── test/
        └── java/com/xiaoyu/
            ├── OrderSystemApplicationTests.java
            ├── OrderClosureIntegrationTest.java
            ├── OrderSystemFullTest.java
            └── controller/
                ├── AddressControllerTest.java
                └── AuthControllerTest.java
```

## Database Design

### Entity Relationship

```
sys_user (1) ──────── (N) user_cart (N) ──────── (1) goods
    │
    ├── (1) ──────── (N) user_address
    │
    └── (1) ──────── (N) order_main (1) ──────── (N) order_item (N) ──────── (1) goods
```

### Table Definitions

#### sys_user

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK) | User ID (snowflake) |
| username | VARCHAR | Unique username |
| password | VARCHAR | BCrypt encrypted password |
| nickname | VARCHAR | Display name |
| phone | VARCHAR | Phone number |
| role | INT | 0=Normal user, 1=Admin |
| status | INT | 0=Frozen, 1=Active |
| create_time | DATETIME | Registration time |
| logout_time | DATETIME | Last logout time (null if logged in) |

#### goods

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK) | Product ID |
| goods_name | VARCHAR | Product name |
| price | DECIMAL(10,2) | Unit price |
| stock | INT | Available stock |
| pic_url | VARCHAR | Product image URL |
| sales | INT | Total sales count |
| version | INT | Optimistic lock version |
| status | INT | 0=Off-shelf, 1=On-shelf |
| create_time | DATETIME | Creation time |
| update_time | DATETIME | Last update time |

#### user_cart

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK) | Cart item ID |
| user_id | BIGINT (FK) | Owner user ID |
| goods_id | BIGINT (FK) | Product ID |
| buy_num | INT | Quantity |
| create_time | DATETIME | Added time |

#### user_address

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK) | Address ID |
| user_id | BIGINT (FK) | Owner user ID |
| name | VARCHAR | Recipient name |
| phone | VARCHAR | Recipient phone |
| address | VARCHAR | Full address |
| is_default | INT | 0=No, 1=Default address |
| create_time | DATETIME | Created time |

#### order_main

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK) | Order ID |
| order_no | VARCHAR | Unique order number |
| user_id | BIGINT (FK) | Buyer user ID |
| total_amount | DECIMAL | Total amount |
| pay_amount | DECIMAL | Actual payment amount |
| status | INT | Order status (see below) |
| pay_time | DATETIME | Payment time |
| delivery_time | DATETIME | Delivery time |
| finish_time | DATETIME | Completion time |
| address_name | VARCHAR | Recipient name (snapshot) |
| address_phone | VARCHAR | Recipient phone (snapshot) |
| address_detail | VARCHAR | Shipping address (snapshot) |
| version | VARCHAR | Optimistic lock version |
| create_time | DATETIME | Order creation time |
| update_time | DATETIME | Last update time |

#### order_item

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK) | Order item ID |
| user_id | BIGINT (FK) | Buyer user ID |
| order_no | VARCHAR | Parent order number |
| goods_id | BIGINT (FK) | Product ID |
| goods_name | VARCHAR | Product name (snapshot) |
| goods_price | DECIMAL | Unit price at purchase (snapshot) |
| buy_num | INT | Quantity purchased |
| create_time | DATETIME | Created time |

### Order Status Flow

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│ 0=待付款  │────▶│ 1=待发货  │────▶│ 2=已发货  │────▶│ 3=已完成  │
│ Pending   │     │ Paid     │     │ Shipped  │     │ Finished │
└────┬─────┘     └──────────┘     └──────────┘     └──────────┘
     │
     │ (user cancels)
     ▼
┌──────────┐
│ 4=已取消  │
│ Cancelled│
└──────────┘
```

| Status | Code | Description | Available Actions |
|--------|------|-------------|-------------------|
| 待付款 | 0 | Order created, awaiting payment | User: cancel |
| 待发货 | 1 | Payment confirmed | Admin: deliver |
| 已发货 | 2 | Goods shipped | User: confirm receipt |
| 已完成 | 3 | Transaction complete | - |
| 已取消 | 4 | Cancelled by user | - |

## Quick Start

### Prerequisites

- **JDK 17+**
- **Maven 3.6+**
- **MySQL 8.0+**

### Step 1: Clone the Repository

```bash
git clone https://github.com/mcpowerade3912-dev/xiaoyu.git
cd xiaoyu
```

### Step 2: Set Up the Database

Create the database and all tables using the provided script:

```bash
mysql -u root -p < fix_schema.sql
```

### Step 3: Configure Database Connection

The connection is configured through environment variables, with local development defaults:

```bash
export DB_URL='jdbc:mysql://localhost:3306/order_manager?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf-8'
export DB_USERNAME='root'
export DB_PASSWORD='your_password'
export JWT_SECRET='replace-with-a-random-secret-of-at-least-64-characters'
export SETTINGS_ENCRYPTION_KEY='replace-with-a-random-settings-encryption-key'
# Optional. Without this key, the AI assistant runs in local summary mode.
export OPENAI_API_KEY='your-openai-api-key'
export OPENAI_MODEL='gpt-4.1-mini'
```

### Step 4: Build and Run

```bash
# Build the project (skip tests for faster startup)
mvn clean package -DskipTests

# Run with java
java -jar target/order_system-0.0.1-SNAPSHOT.jar

# Or run directly with Maven
mvn spring-boot:run
```

The application starts at **http://localhost:8080** by default.

### Step 5: Verify

```bash
# Check if the server is running
curl http://localhost:8080/goods/page?pageNum=1&pageSize=10
```

### Step 6: Open the Admin Web UI

Open `http://localhost:8080/` in a browser and log in with an account whose
`sys_user.role` is `1`.

The front end is bundled as static resources and does not require Node.js.
The workspace includes:

- Business overview and recent order/stock risk panels
- Order filtering, detail drawer, and delivery actions
- Product statistics, status/low-stock filters, search, creation, editing,
  shelf status, and protected deletion with inventory maintenance
- User search, freeze, and unfreeze actions
- Customer address-book search, filtering, pagination, and read-only detail lookup
- Persistent system settings for branding, order timeouts, stock alerts, and AI API access
- AI assistant with quick prompts and visible read-only query evidence

A clearly labelled interface preview is also available at:

```
http://localhost:8080/?preview=1
```

Preview mode uses front-end demo data and does not call the real AI endpoint.
The normal login page uses the real backend for authentication, AI status,
business queries, and audit logging.

## API Reference

All API responses follow the unified format:

```json
{
  "code": "200",
  "data": {},
  "msg": "Operation description"
}
```

**Status Codes:**

| Code | Meaning |
|------|---------|
| 200 | Success |
| 500 | Business error |
| 40001 | Parameter validation failed |
| 40002 | Username already exists |
| 40003 | User does not exist |
| 40004 | Incorrect password |
| 40005 | Account status abnormal (frozen) |
| 401 | Not authenticated (missing/invalid token) |
| 403 | Insufficient permissions (non-admin accessing admin endpoints) |
| 50001 | Internal server error |

### Public Endpoints (No Auth Required)

#### User Registration

```
POST /user/register
Content-Type: application/json
```

**Request:**
```json
{
  "username": "zhangsan",
  "password": "123456",
  "phone": "13800138000"
}
```

**Response (Success):**
```json
{
  "code": "200",
  "data": {
    "userId": 2122250573444435970,
    "username": "zhangsan"
  },
  "msg": "注册成功"
}
```

**Response (Username Exists):**
```json
{
  "code": "40002",
  "data": null,
  "msg": "用户名已存在"
}
```

#### User Login

```
POST /user/login
Content-Type: application/json
```

**Request:**
```json
{
  "username": "zhangsan",
  "password": "123456"
}
```

**Response (Success):**
```json
{
  "code": "200",
  "data": {
    "userId": 2122250573444435970,
    "username": "zhangsan",
    "nickname": "张三",
    "role": 0,
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEsInJvbGUiOjAsInVzZXJuYW1lIjoiemhhbmdzYW4ifQ.xxxxx"
  },
  "msg": "登录成功"
}
```

**Response (Wrong Password):**
```json
{
  "code": "40004",
  "data": null,
  "msg": "密码错误"
}
```

#### Browse Products (No Auth)

```
GET /goods/page?pageNum=1&pageSize=10&goodsName=keyword
```

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| pageNum | Integer | No | 1 | Page number |
| pageSize | Integer | No | 10 | Items per page |
| goodsName | String | No | - | Filter by product name keyword |

**Response:**
```json
{
  "code": "200",
  "data": {
    "records": [
      {
        "id": 1,
        "goodsName": "iPhone 15",
        "price": 5999.00,
        "stock": 100,
        "pic_url": "https://example.com/iphone15.jpg",
        "sales": 50,
        "status": 1,
        "createTime": "2024-01-15 10:30:00"
      }
    ],
    "total": 50,
    "size": 10,
    "current": 1,
    "pages": 5
  },
  "msg": "查询成功"
}
```

### Authenticated Endpoints

All endpoints below require the `Authorization` header:

```
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.xxxxx
```

#### User Logout

```
GET /user/logout
Authorization: Bearer <token>
```

**Response:**
```json
{
  "code": "200",
  "data": null,
  "msg": "登出成功"
}
```

> Note: After logout, the token becomes invalid server-side. Subsequent requests with the same token will return 401.

---

#### Shopping Cart

**Add to Cart:**
```
POST /cart/add
Authorization: Bearer <token>
Content-Type: application/json

{
  "goodsId": 1,
  "buyNum": 2
}
```

**View Cart:**
```
GET /cart/get
Authorization: Bearer <token>
```

**Response:**
```json
{
  "code": "200",
  "data": [
    {
      "cartId": 1,
      "goodsId": 1,
      "goodsName": "iPhone 15",
      "price": 5999.00,
      "picUrl": "https://example.com/iphone15.jpg",
      "buyNum": 2,
      "stock": 100,
      "status": 1,
      "subtotal": 11998.00
    }
  ],
  "msg": "查询成功"
}
```

**Update Quantity:**
```
PUT /cart/update
Authorization: Bearer <token>
Content-Type: application/json

{
  "goodsId": 1,
  "buyNum": 5
}
```

Delete a cart item:

```
DELETE /cart/{goodsId}
Authorization: Bearer <token>
```

---

#### Shipping Addresses

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/address/list` | List all my addresses |
| GET | `/address/{id}` | Get address detail |
| POST | `/address/add` | Add new address |
| PUT | `/address/update` | Edit address |
| PUT | `/address/default/{id}` | Set as default address |
| DELETE | `/address/{id}` | Delete address |

**Add Address:**
```json
POST /address/add
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Zhang San",
  "phone": "13800138000",
  "address": "Beijing, Haidian District, Zhongguancun Street No.1"
}
```

---

#### User Orders

**Create Order:**
```
POST /order/create
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "addressName": "Zhang San",
  "addressPhone": "13800138000",
  "addressDetail": "Beijing, Haidian District, Zhongguancun Street No.1",
  "goodsList": [
    { "goodsId": 1, "buyNum": 2 },
    { "goodsId": 3, "buyNum": 1 }
  ]
}
```

**Response (Success):**
```json
{
  "code": "200",
  "data": {
    "orderId": 2122250574035832833,
    "orderNo": "ORD2122250574035832833",
    "payAmount": 11998.00,
    "status": 0,
    "paymentDeadline": "2024-01-15T11:00:00"
  },
  "msg": "下单成功"
}
```

**Response (Insufficient Stock):**
```json
{
  "code": "500",
  "data": null,
  "msg": "商品iPhone 15库存不足"
}
```

> The order creation process uses optimistic locking for stock deduction. Under high concurrency, stock conflicts will return a "please refresh" message.

**Pay Order (simulation):**
```
POST /order/pay/{orderId}
Authorization: Bearer <token>
```

> Payment is atomic and only succeeds for the order owner while it is pending and within the payment window. An order can only transition from status `0` to `1` once.

**My Orders (Paginated):**
```
GET /order/page?pageNum=1&pageSize=10&status=0
Authorization: Bearer <token>
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| pageNum | Integer | No | Page number (default: 1) |
| pageSize | Integer | No | Page size (default: 10) |
| status | Integer | No | Filter by order status (0-4) |

**Order Detail:**
```
GET /order/detail/{orderId}
Authorization: Bearer <token>
```

**Response:**
```json
{
  "code": "200",
  "data": {
    "id": 1,
    "orderNo": "ORD20240115103000001",
    "totalAmount": 17997.00,
    "payAmount": 17997.00,
    "status": 0,
    "addressName": "Zhang San",
    "addressPhone": "13800138000",
    "addressDetail": "Beijing, Haidian District",
    "itemList": [
      {
        "goodsId": 1,
        "goodsName": "iPhone 15",
        "goodsPrice": 5999.00,
        "buyNum": 2
      },
      {
        "goodsId": 3,
        "goodsName": "AirPods Pro",
        "goodsPrice": 1999.00,
        "buyNum": 1
      }
    ]
  },
  "msg": "查询成功"
}
```

**Cancel Order:**
```
PUT /order/cancel/{orderId}
Authorization: Bearer <token>
```

> Only orders with status `0 (Pending)` can be cancelled. Stock is automatically restored upon cancellation.

> Repeated cancellation requests are idempotent and will not restore stock a second time.

**Confirm Receipt:**
```
PUT /order/confirm/{orderId}
Authorization: Bearer <token>
```

> Only orders with status `2 (Shipped)` can be confirmed.

---

### Admin Endpoints

> All `/admin/**` endpoints require the user to have `role=1` (admin). Non-admin users will receive a `403 Forbidden` response.

#### Product Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/admin/page` | List products (paginated) |
| POST | `/admin/add` | Add new product |
| PUT | `/admin/update` | Edit product |
| PUT | `/admin/status/{goodsId}` | Toggle shelf status |

**Add Product:**
```json
POST /admin/add
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "goodsName": "MacBook Pro 14",
  "price": 14999.00,
  "stock": 50,
  "pic_url": "https://example.com/macbook.jpg"
}
```

**Toggle Shelf Status:**
```
PUT /admin/status/1?status=0     # Take off shelf
PUT /admin/status/1?status=1     # Put on shelf
```

#### Order Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin/order/page` | List all orders (paginated) |
| GET | `/admin/order/detail/{orderId}` | Get any order with item details |
| PUT | `/admin/order/deliver/{orderId}` | Mark order as delivered |
| GET | `/admin/order/statistics` | Get sales statistics |

**Sales Statistics Response:**
```json
{
  "code": "200",
  "data": {
    "todayOrderCount": 23,
    "todayTotalAmount": 156800.00,
    "totalOrderCount": 1520,
    "totalAmount": 8950000.00
  },
  "msg": "查询成功"
}
```

**Deliver Order:**
```
PUT /admin/order/deliver/{orderId}
Authorization: Bearer <admin-token>
```

> Only orders with status `1 (Paid)` can be delivered.

#### AI Operations Assistant

The assistant uses read-only, fixed business tools. It can query business statistics, orders, low-stock products, and sales trends, but cannot execute arbitrary SQL or modify business data.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin/ai/status` | Check whether OpenAI or local mode is active |
| POST | `/admin/ai/assistant` | Ask a natural-language operations question |

**Ask the assistant:**
```json
POST /admin/ai/assistant
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "message": "列出待发货订单，并告诉我哪些商品库存低于10件"
}
```

**Response:**
```json
{
  "code": "200",
  "data": {
    "answer": "当前有 3 笔待发货订单...",
    "mode": "openai",
    "model": "gpt-4.1-mini",
    "configured": true,
    "durationMs": 1840,
    "evidence": [
      {
        "name": "query_orders",
        "arguments": { "status": 1, "limit": 10 },
        "result": {
          "total": 3,
          "orders": []
        }
      }
    ]
  },
  "msg": "查询成功"
}
```

When `OPENAI_API_KEY` is absent, or the remote service is temporarily unavailable, the same endpoint returns a deterministic local business summary with `mode: "local"`. Every request is recorded in `ai_operation_log` for audit and troubleshooting.

#### System Settings

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/settings/public` | Read the public system name and support contact |
| GET | `/admin/settings` | Read administrator settings |
| PUT | `/admin/settings` | Update system and AI configuration |

AI API Key is encrypted before storage and is never returned as plaintext. Saving a blank
API Key keeps the existing key; use `clearAiApiKey=true` to remove it explicitly.

#### User Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin/user/page` | List users (paginated) |
| PUT | `/admin/user/status/{userId}` | Freeze/unfreeze user |

**List Users:**
```
GET /admin/user/page?pageNum=1&pageSize=10&username=zhang
```

**Freeze/Unfreeze User:**
```
PUT /admin/user/status/1?status=0    # Freeze user
PUT /admin/user/status/1?status=1    # Unfreeze user
```

> Frozen users cannot log in. If a logged-in user is frozen, their subsequent requests will be rejected.

## Security

### Authentication Flow

```
Client                          Server
  │                                │
  │  POST /user/login              │
  │  {username, password}          │
  │ ──────────────────────────────▶│
  │                                │ 1. Validate credentials
  │                                │ 2. Generate JWT (contains userId, role, username)
  │  ◀────────────────────────────│ 3. Return token
  │  {token: "eyJhbG..."}         │
  │                                │
  │  GET /order/page               │
  │  Authorization: Bearer <token> │
  │ ──────────────────────────────▶│
  │                                │ 1. JwtAuthInterceptor intercepts
  │                                │ 2. Parse & validate JWT
  │                                │ 3. Check user.logoutTime == null
  │                                │ 4. For /admin/**: check role == 1
  │                                │ 5. Store userId in ThreadLocal
  │  ◀────────────────────────────│ 6. Forward to controller
  │  {data: [...]}                 │
```

### Key Security Mechanisms

- **Password Encryption:** Passwords are hashed using BCrypt (via Spring Security Crypto). Plaintext passwords are never stored.
- **JWT Stateless Auth:** Tokens are self-contained with `userId`, `role`, and `username`. No server-side session storage.
- **Logout Invalidation:** On logout, a `logoutTime` is recorded in the database. The interceptor checks this field on every request, effectively invalidating the token without maintaining a blacklist.
- **Role-Based Access Control:** Admin endpoints (`/admin/**`) are protected at the interceptor level. Non-admin users receive `403 Forbidden`.
- **Optimistic Locking:** `@Version` annotation on `goods.version` and `order_main.version` prevents race conditions during concurrent stock deduction and order status updates.

## Testing

The project includes a comprehensive test toolkit under `test-toolkit/`:

### JUnit 5 Tests

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=OrderSystemFullTest
```

### Postman Collection

Import the collection file into Postman:

```
test-toolkit/postman/order_system_api_test.postman_collection.json
```

The collection includes pre-configured requests for all endpoints with environment variables for the base URL and auth token.

### JMeter Stress Test

See `test-toolkit/jmeter/JMeter压测配置指南.md` for detailed load testing instructions.

## Deployment

### Building a Production JAR

```bash
mvn clean package -DskipTests
```

The JAR file will be generated at `target/order_system-0.0.1-SNAPSHOT.jar`.

### Running in Production

```bash
# Basic
java -jar order_system-0.0.1-SNAPSHOT.jar

# With custom port
java -jar order_system-0.0.1-SNAPSHOT.jar --server.port=9090

# With custom database
java -jar order_system-0.0.1-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:mysql://your-db-host:3306/order_manager \
  --spring.datasource.username=prod_user \
  --spring.datasource.password=secure_password

# Background mode with log output
nohup java -jar order_system-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
```

### Recommended Production Configuration

Add to `application.yml` for production:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

jwt:
  secret: ${JWT_SECRET}
  expiration-ms: 7200000

settings:
  encryption-key: ${SETTINGS_ENCRYPTION_KEY}

ai:
  openai:
    api-key: ${OPENAI_API_KEY:}
    model: ${OPENAI_MODEL:gpt-4.1-mini}
    timeout-seconds: 60
  rate-limit-per-minute: 30

order:
  payment-timeout-minutes: 30

app:
  cors:
    allowed-origin-patterns: http://localhost:3000,http://localhost:5173

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl  # Disable SQL logging in production
```

## Troubleshooting

**Database connection failed on startup**
- Verify MySQL is running: `systemctl status mysql` or `brew services list`
- Check credentials in `application.yml`
- Ensure the `order_manager` database exists

**Table 'order_manager.xxx' doesn't exist**
- Run `fix_schema.sql` to initialize all tables: `mysql -u root -p < fix_schema.sql`

**Port 8080 already in use**
- Change the port: add `server.port=8081` to `application.yml`
- Or kill the process: `lsof -i :8080` then `kill -9 <PID>`

**Token returns 401 after login**
- Ensure the header format is `Authorization: Bearer <token>` (with "Bearer " prefix)
- Check if the user has been logged out (logout invalidates the token server-side)
- Verify the token has not been tampered with

**Order creation fails with "库存不足" (Insufficient stock)**
- The requested quantity exceeds available stock
- Under high concurrency, another user may have purchased the last items
- Retry the request after refreshing

**Admin endpoints return 403**
- The logged-in user does not have admin privileges (role != 1)
- Update the user's role in the database: `UPDATE sys_user SET role = 1 WHERE username = 'your_admin';`

## Contact

- **Email:** mcpowerade3912@gmail.com
- **GitHub:** [mcpowerade3912-dev](https://github.com/mcpowerade3912-dev)

## License

This project is for educational and learning purposes only.
