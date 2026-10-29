# Issue #56 / #57 / #58 改动说明（fd）

本文档记录本次全部**新增**与**改动**，覆盖后端 6 个模块（cart / order / product / user / payment / gateway）与前端 web。

- 实现原则：**只做新增功能与必要调用适配，不改各服务原有业务行为**；原有测试全部保留并通过。
- 代码根目录：`D:\1111\-`，服务位于 `src/<服务名>`。
- 提交状态：已按团队规范拆成 3 个本地分支提交（见第七节），**尚未推送远端**；本文档随 #57 分支提交在仓库根目录 `README-issue56-58.md`。

---

## 一、验证结果（实测）

| 模块 | 自动化测试 | 结果 |
| --- | --- | --- |
| cart-service | 27 条 | 全绿 |
| order-service | 29 条（原有 20 + 新增 9） | 全绿 |
| product-service | 9 条 | 全绿 |
| user-service | 6 条 | 全绿 |
| payment-service | 36 条 | 全绿 |
| gateway | 10 条（原有 9 + 新增 1） | 全绿 |
| web | `npm run build` | 构建成功（vite 8，含新增页面 chunk） |

> 说明：前端仅做了构建验证，浏览器端到端联调需要 Nacos + 六个服务同时运行，未在本次环境内执行。

---

## 二、Issue #56：内部调用鉴权补齐（X-Internal-Token）

### 2.1 问题与方案

- order-service 创建订单时需要读/删购物车，原调用的是 cart-service 的公开接口 `/cart/**`。
- 网关对 `/api/**` 统一 `StripPrefix=1`，公开路径与内部路径若共用 `/cart`，无法区分“来自网关的普通用户”与“来自订单服务的内部调用”。
- **方案**：cart-service 新增独立的内部接口 `/internal/cart/**`（不经网关），校验 `X-Internal-Token`；order-service 改为调用内部接口并携带令牌。

### 2.2 新增文件（cart-service，fd 本人服务）

| 文件 | 说明 |
| --- | --- |
| `src/main/java/com/mall/cart/config/InternalTokenVerifier.java` | 读取 `cart.internal-token`，空白时放行（开发期），配置后不匹配抛 10003 |
| `src/main/java/com/mall/cart/controller/InternalCartController.java` | `GET /internal/cart`、`DELETE /internal/cart/items/{cartItemId}` |
| `src/test/java/com/mall/cart/controller/InternalCartControllerTest.java` | 5 条：令牌正确 / 错误 / 缺失 / 删除条目 / 用户隔离 |
| `src/test/java/com/mall/cart/controller/InternalCartControllerOpenTest.java` | 2 条：未配置令牌时的开发期放行行为 |

### 2.3 修改文件（order-service，必要适配）

| 文件 | 改动 |
| --- | --- |
| `client/CartClient.java` | 改调 `/internal/cart` 与 `/internal/cart/items/{cartItemId}`，新增 `X-Internal-Token` 请求头（参数放第一位） |
| `service/OrderService.java` | 调用 `cartClient.getCart(internalToken, buyerId)` / `deleteItem(internalToken, buyerId, cartItemId)` |
| `test/.../OrderServiceTest.java` | Mock 签名适配（`anyString(), anyString()` 等） |
| `test/.../Issue48OrderConcurrencyTest.java` | 同上 |

### 2.4 接口约定

```
GET    /internal/cart                        Header: X-Internal-Token, X-User-Id
DELETE /internal/cart/items/{cartItemId}     Header: X-Internal-Token, X-User-Id
```

- 令牌来自各服务配置 `*.internal-token: ${INTERNAL_TOKEN:}`，与现有内部接口保持一致。
- `/internal/**` 不在网关路由中，外部无法访问。

---

## 三、Issue #57：订单评价与晒图（MVP）

### 3.1 业务规则

- 只有**订单买家**、且订单状态为 `COMPLETED` 才能评价；**一单一评**（`order_id` 唯一约束兜底并发重复提交）。
- 评分 1~5 星必填；文字 ≤ 500 字；图片 ≤ 3 张；**文字与图片至少有一项**。
- 图片地址以 `/` 或 `http(s)` 开头；后端以 CSV 存储（`images` 字段）。
- 匿名评价公开显示“匿名用户”；非匿名显示掩码 `U2***01`（前 2 + `***` + 后 2）；公开接口不返回真实 `buyerId`，管理员接口返回。
- 管理员可隐藏/恢复评价；隐藏后商品详情页列表与平均分统计同步剔除，操作写入审计日志（`EVALUATION_HIDE` / `EVALUATION_SHOW`）。
- 商品维度通过 `order_item.product_id` 子查询关联，支持一单多商品。

### 3.2 新增文件（order-service）

| 文件 | 说明 |
| --- | --- |
| `common/EvaluationStatus.java` | `VISIBLE` / `HIDDEN` |
| `entity/OrderEvaluation.java` | 评价实体 |
| `mapper/OrderEvaluationMapper.java` + `resources/mapper/OrderEvaluationMapper.xml` | insert / selectByOrderId / selectById / 商品分页与统计 / 管理端分页 / updateStatus |
| `dto/request/CreateEvaluationRequest.java` | rating 1~5、content ≤500、images ≤3、anonymous |
| `dto/response/EvaluationResponse.java` | 公开字段 + 仅管理端返回的 buyerId |
| `dto/response/EvaluationStatsResponse.java` | total、average |
| `service/EvaluationService.java` | 创建、订单维度查询、商品公开列表、统计、管理端列表、隐藏/恢复 |
| `controller/OrderEvaluationController.java` | 订单维度接口 |
| `controller/EvaluationController.java` | 商品维度 / 管理端接口 |
| `test/.../EvaluationServiceIntegrationTest.java` | 5 条集成测试 |

### 3.3 修改文件（order-service）

| 文件 | 改动 |
| --- | --- |
| `common/ErrorCode.java` | 新增 `EVALUATION_EXISTS=50005`、`EVALUATION_NOT_FOUND=50006` |
| `resources/schema.sql` | 追加 `order_evaluation` 建表 |
| `test/resources/schema-h2.sql` | 同步 H2 建表（DROP + CREATE） |

### 3.4 接口清单

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/orders/{orderId}/evaluations` | 买家本人 | 创建评价（一单一评） |
| GET | `/api/orders/{orderId}/evaluations` | 买家/卖家/管理员 | 订单维度评价 |
| GET | `/api/evaluations/products/{productId}` | 公开 | 商品评价分页（仅 VISIBLE） |
| GET | `/api/evaluations/products/{productId}/stats` | 公开 | 平均分与条数 |
| GET | `/api/evaluations?status=&productId=&page=&size=` | 管理员 | 评价管理分页 |
| PUT | `/api/evaluations/{evaluationId}/hide` | 管理员 | 隐藏（写审计） |
| PUT | `/api/evaluations/{evaluationId}/show` | 管理员 | 恢复（写审计） |

> order-service 现有约定：Controller 同时映射带 `/api` 与不带 `/api` 的路径。

### 3.5 网关改动

| 文件 | 改动 |
| --- | --- |
| `gateway/src/main/resources/application.yaml` | 新增路由 `order-evaluations`：`/api/evaluations/**` → `lb://order-service`，`StripPrefix=1` |
| `gateway/src/main/java/com/mall/gateway/filter/JwtAuthFilter.java` | `PUBLIC_GET` 增加 `/api/evaluations/products/**`（游客可看商品评价） |
| `gateway/src/test/.../JwtAuthFilterWhiteListTest.java` | 新增 1 条：评价公开路径放行、评价管理/审计日志必须登录 |

### 3.6 前端改动（web）

| 文件 | 改动 |
| --- | --- |
| `src/api/index.js` | 新增 `evaluationApi`（create / byOrder / byProduct / productStats / adminList / hide / show） |
| `src/views/OrderDetail.vue` | 新增“我的评价”展示；买家在已完单且未评价时出现“评价晒图”表单：星级、文字、最多 3 图上传（复用 `productApi.uploadImage`）、匿名选项 |
| `src/views/ProductDetail.vue` | 新增商品评价卡片：平均分、评价列表（星级/文字/晒图）、分页；游客可见 |
| `src/views/admin/AdminEvaluations.vue` | **新增**评价管理页：按状态/商品筛选、隐藏/恢复、分页 |
| `src/router/index.js` | 新增 `/admin/evaluations`（ADMIN） |
| `src/components/AppHeader.vue` | 管理菜单新增“评价管理” |

---

## 四、Issue #58：操作审计日志

### 4.1 方案

- **order-service 作为审计中心**：统一 `audit_log` 通用表，避免每个服务各建一张表、管理端多源查询。
- 其他服务通过 Feign 调用 `POST /internal/audit-logs` 上报；**best-effort**：`try/catch` 只记 warn，审计中心不可用不影响业务结果；`audit.enabled=false` 可整体关闭上报（测试环境使用）。
- 管理端查询 `GET /api/audit-logs` 仅 ADMIN，支持操作人、动作、日期区间、分页筛选。
- 埋点统一放在 **Controller 层**（业务成功后调用），不侵入各服务 Service，避免影响既有逻辑与测试。

### 4.2 新增文件（order-service）

| 文件 | 说明 |
| --- | --- |
| `entity/AuditLog.java` | 审计实体 |
| `dto/request/AuditLogRequest.java` | 上报请求体 |
| `mapper/AuditLogMapper.java` + `resources/mapper/AuditLogMapper.xml` | insert / 动态条件分页 |
| `service/AuditLogService.java` | 落库（校验 operatorId/action，result 仅 SUCCESS/FAIL）、分页查询（日期支持 `yyyy-MM-dd` 与 ISO） |
| `controller/AuditLogController.java` | 内部写入 + 管理员查询 |
| `test/.../AuditLogServiceIntegrationTest.java` | 4 条：筛选、分页倒序、日期区间、非法参数 |

### 4.3 修改文件（order-service）

| 文件 | 改动 |
| --- | --- |
| `controller/PlatformConfigController.java` | 配置更新成功后上报 `CONFIG_UPDATE`（target `CONFIG/mall-platform.yml`，detail 为配置项列表） |
| `resources/schema.sql` / `test/resources/schema-h2.sql` | 追加 `audit_log` 建表 / H2 同步 |

### 4.4 其他服务的上报客户端（product / user / payment 各一套）

每个服务新增 3 个文件（包名随服务）：

| 文件 | 说明 |
| --- | --- |
| `client/dto/AuditLogRequest.java` | 上报 DTO |
| `client/AuditClient.java` | `@FeignClient(name="order-service", url="${services.order-url:}", contextId="auditClient")`，`POST /internal/audit-logs`，带 `X-Internal-Token` |
| `service/AuditLogPublisher.java` | 开关 `${audit.enabled:true}`；异常只 warn，不影响业务；令牌取各服务 `${xxx.internal-token:}` |

埋点位置（均为业务成功后一行调用）：

| 服务 | 位置 | action | target | detail |
| --- | --- | --- | --- | --- |
| product | `ProductController.audit` | `PRODUCT_AUDIT` | `PRODUCT/{productId}` | 审核通过/驳回 + 备注 |
| user | `UserController.updateStatus` | `USER_STATUS` | `USER/{userId}` | 状态变更为 ACTIVE/FROZEN |
| user | `UserController.updateRole` | `USER_ROLE` | `USER/{userId}` | 角色变更为 BUYER/SELLER/ADMIN |
| payment | `PaymentController.auditRefund` | `REFUND_AUDIT` | `REFUND/{refundId}` | 通过/驳回退款 + 备注 |
| order | `PlatformConfigController.update` | `CONFIG_UPDATE` | `CONFIG/mall-platform.yml` | 配置项列表 |
| order | `EvaluationService.hide/show` | `EVALUATION_HIDE` / `EVALUATION_SHOW` | `EVALUATION/{evaluationId}` | 隐藏/恢复说明 |

### 4.5 接口清单

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/internal/audit-logs` | 内部令牌 | 供其他服务写入（不经网关） |
| GET | `/api/audit-logs?operatorId=&action=&from=&to=&page=&size=` | 管理员 | 审计日志分页查询 |

### 4.6 配置改动汇总

| 服务 | 配置文件 | 新增项 |
| --- | --- | --- |
| product | `src/main/resources/application.yaml` | `services.order-url: ${ORDER_SERVICE_URL:}`、`audit.enabled: ${AUDIT_ENABLED:true}` |
| user | 同上 | 同上 |
| payment | 同上 | `audit.enabled: ${AUDIT_ENABLED:true}`（`services.order-url` 原有） |
| product | `src/test/resources/application-test.yaml` | `services.order-url: http://127.0.0.1:1`、`audit.enabled: false` |
| user | `src/test/resources/application.yaml` | 同上 |
| payment | `src/test/resources/application.yaml` | `audit.enabled: false` |

### 4.7 网关改动

| 文件 | 改动 |
| --- | --- |
| `gateway/src/main/resources/application.yaml` | 新增路由 `order-audit-logs`：`/api/audit-logs/**` → `lb://order-service`，`StripPrefix=1` |

### 4.8 前端改动（web）

| 文件 | 改动 |
| --- | --- |
| `src/api/index.js` | 新增 `auditApi.list` |
| `src/views/admin/AdminAuditLogs.vue` | **新增**审计日志页：操作人 / 动作 / 起止日期筛选、表格（操作人、角色、动作、对象、详情、结果）、分页 |
| `src/router/index.js` | 新增 `/admin/audit-logs`（ADMIN） |
| `src/components/AppHeader.vue` | 管理菜单新增“审计日志” |

---

## 五、数据库变更

order-service 的 `schema.sql` 追加两张表（存量数据库需手动执行）：

```sql
CREATE TABLE IF NOT EXISTS order_evaluation (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    evaluation_id VARCHAR(32)  NOT NULL,
    order_id      VARCHAR(32)  NOT NULL,
    buyer_id      VARCHAR(32)  NOT NULL,
    seller_id     VARCHAR(32)  NOT NULL,
    rating        TINYINT      NOT NULL,
    content       VARCHAR(500) DEFAULT NULL,
    images        VARCHAR(1024) DEFAULT NULL,
    anonymous     TINYINT(1)   NOT NULL DEFAULT 0,
    status        VARCHAR(16)  NOT NULL DEFAULT 'VISIBLE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_evaluation_id (evaluation_id),
    UNIQUE KEY uk_evaluation_order (order_id),
    KEY idx_evaluation_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单评价';

CREATE TABLE IF NOT EXISTS audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    audit_id      VARCHAR(32)  NOT NULL,
    operator_id   VARCHAR(32)  NOT NULL,
    operator_role VARCHAR(16)  DEFAULT NULL,
    action        VARCHAR(32)  NOT NULL,
    target_type   VARCHAR(32)  DEFAULT NULL,
    target_id     VARCHAR(64)  DEFAULT NULL,
    detail        VARCHAR(1000) DEFAULT NULL,
    result        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_audit_id (audit_id),
    KEY idx_audit_operator (operator_id, created_at),
    KEY idx_audit_action (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台关键操作审计日志';
```

文件位置：`src/order-service/src/main/resources/schema.sql`；测试库同步在 `src/order-service/src/test/resources/schema-h2.sql`。

---

## 六、对他人代码的修改（必要适配，均为加法/兼容）

| 模块 | 文件 | 改动性质 |
| --- | --- | --- |
| order-service | `client/CartClient.java`、`service/OrderService.java`、2 个测试类 | #56 调用适配，行为不变 |
| order-service | `common/ErrorCode.java` | 追加 2 个错误码 |
| order-service | `controller/PlatformConfigController.java` | 新增依赖 + 成功后审计上报（异常吞掉） |
| order-service | `schema.sql`、`schema-h2.sql` | 追加两张表 |
| gateway | `application.yaml`、`JwtAuthFilter.java`、白名单测试 | 追加 2 条路由、1 条公开 GET 规则、1 条测试 |
| product | `ProductController.java` | 新增 1 个依赖字段 + 审核成功后 2 行上报 + 1 个私有 `suffix` 方法 |
| product | `application.yaml`、测试 yaml | 追加配置项 |
| user | `UserController.java` | 新增 1 个依赖字段 + 两个操作各 2 行上报 |
| user | `application.yaml`、测试 yaml | 追加配置项 |
| payment | `PaymentController.java` | 新增 1 个依赖字段 + 审核成功后 2 行上报 + 1 个私有 `suffix` 方法 |
| payment | `application.yaml`、测试 yaml | 追加配置项 |
| web | `api/index.js`、`router/index.js`、`AppHeader.vue`、`OrderDetail.vue`、`ProductDetail.vue` | 追加 API、路由、菜单、评价展示/表单区块；原有逻辑未改 |

**未修改**：`docs/`、`reports/`、仓库根 `README.md`（按团队规范仅组长可改）；其他服务的业务 Service 与既有测试断言。

---

## 七、建议的提交与合并方式

按团队规范 `docs/03-Git协作规范.md`：一 Issue 一分支，顺序合并，禁止 force push，本次仅做本地提交、不推送。

**分支为叠层结构（基于上一个分支继续开发）**：

```
main
 └─ feature/56-cart-internal-token   (#56，1 commit)
     └─ feature/58-audit-log         (#58，1 commit)
         └─ feature/57-order-evaluation (#57，1 commit，当前 HEAD)
```

| 顺序 | 分支 | Issue | 范围 |
| --- | --- | --- | --- |
| 1 | `feature/56-cart-internal-token` | #56 | cart-service 4 个新文件；order-service CartClient/OrderService + 2 个测试 |
| 2 | `feature/58-audit-log` | #58 | order-service 审计中心 7 个新文件 + PlatformConfigController/审计表；product/user/payment 的 Client/Publisher/Controller 埋点与配置；gateway 审计路由；web 审计页、路由/菜单/API |
| 3 | `feature/57-order-evaluation` | #57 | order-service 评价相关 12 个新文件 + ErrorCode/评价表；gateway 评价路由与白名单；web 评价入口、商品页评价区、AdminEvaluations、路由/菜单/API；本 README |

**为什么 #58 先于 #57 合并**：评价的隐藏/恢复需要写审计日志，`EvaluationService` 依赖 #58 的 `AuditLogService`；若 #57 先合并会编译失败。因此合并顺序为 **#56 → #58 → #57**。

> 共享文件（`gateway/application.yaml`、`web/api/index.js`、`web/router/index.js`、`web/AppHeader.vue`、`order-service/schema*.sql`）已在各分支中按功能分段提交：审计部分随 #58，评价部分随 #57，合并时按顺序不会冲突。

提交信息（符合 `<type>(<scope>): <描述> (#Issue)`）：

```
feat(cart): 新增内部购物车接口并校验 X-Internal-Token (#56)
feat(admin): 操作审计日志中心与各服务上报 (#58)
feat(order): 订单评价与晒图，新增商品评价展示与管理端 (#57)
```

本地验证命令：

```bash
# 各服务目录下
mvn -B test
# web 目录
npm install
npm run build
```

---

## 八、运行/联调注意

1. **内部令牌**：product / user / payment / cart / order 的 `INTERNAL_TOKEN` 必须配置为同一个值；留空时开发期不校验（生产必须配置）。
2. **审计上报地址**：`ORDER_SERVICE_URL` 留空时通过 Nacos 服务名发现；本地直连可配置 `http://127.0.0.1:9004`。`AUDIT_ENABLED=false` 可关闭上报。
3. **数据库**：启动 order-service 前，存量库需执行第五节的两条建表语句。
4. **评价图片**：复用商品图片上传接口（`POST /api/products/images`，返回 `/images/xxx`），静态访问与商品图一致。
5. **退款审核人**：`REFUND_AUDIT` 由卖家或管理员触发，审计记录中保存实际操作人与其角色。

---

## 九、已知边界

- 评价为**订单维度**（MVP）：一单多商品不拆分单品评分。
- 晒图复用商品图片目录，未接入内容审核。
- 审计上报是 best-effort：审计中心不可用时该条日志会丢失（设计取舍，优先保证业务可用），仅记录 warn 日志。
- 审计管理页为只读 + 筛选；审计日志不提供删除/导出接口。
- 前端仅完成构建验证，端到端浏览器联调未执行。
