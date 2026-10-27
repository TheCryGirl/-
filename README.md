# 云原生微服务架构实践：在线商城系统

河北工业大学《软件工程实验》（2026 版）课程项目，选题六。

以 Nacos 注册发现 + Spring Cloud Gateway + 多服务多库为主线，实现一个包含买家、卖家、平台管理员三类角色的在线商城；重点实践微服务拆分、服务间调用与数据一致性、网关统一鉴权、幂等与补偿。

## 目录结构

```
.
├── docs/                      项目文档（环境、规划、协作规范、接口契约、任务核对）
├── reports/                   各次实验报告
├── design/                    UML 源文件（uml/）、渲染图（images/）与生成脚本（scripts/）
├── logs/                      本地启动日志（dev.bat 生成，不纳入版本控制）
└── src/
    ├── gateway/               API 网关（9000）：统一路由与 JWT 鉴权
    ├── user-service/          用户服务（9001），库 mall_user
    ├── product-service/       商品服务（9002），库 mall_product
    ├── cart-service/          购物车服务（9003），库 mall_cart
    ├── order-service/         订单服务（9004），库 mall_order
    ├── payment-service/       支付服务（9005），库 mall_payment
    ├── web/                   Vue 3 前端（开发端口 5173）
    ├── campusEasy/            既有参考原型（Spring Boot），不作为本系统运行部分
    ├── docker/                docker-compose.yml 与 init-db.sql（部署规划，未验收）
    └── scripts/               dev.bat / dev.ps1 本地一键启停
```

## 技术栈

| 层次 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.2.5 / JDK 17 / MyBatis / OpenFeign |
| 注册与发现 | Spring Cloud Alibaba Nacos 2.3.2（单机模式） |
| 网关 | Spring Cloud Gateway + JJWT（网关统一鉴权，注入 `X-User-Id` / `X-User-Role`） |
| 数据库 | MySQL 8.4.9，一服务一库（五个业务库） |
| 前端 | Vue 3 + Vue Router + Vite + Tailwind CSS 4 |
| 建模 | PlantUML（.puml 源文件在 `design/uml/`） |
| 待验收的部署规划 | Docker Compose、Redis（均未落地，见下文「边界」） |

## 功能概览（按角色）

| 角色 | 已实现 |
| --- | --- |
| 买家 | 注册登录、商品列表与详情、购物车、确认下单、支付（MOCK 渠道）、订单列表/详情、取消、确认收货、申请退款、个人中心 |
| 卖家 | 商品管理（上下架）、发布/编辑商品与 SKU、调库存、待发货与发货、退款审核 |
| 平台管理员 | 用户管理、店铺管理、商品审核、退款复核、售后仲裁、资金对账、基础风控、系统配置，共 8 个页面 |

前端路由见 `src/web/src/router/index.js`：`/products`、`/cart`、`/orders/**` 为买家侧，`/seller/**` 为卖家侧，`/admin/**` 为管理员侧；路由守卫按 `requiresAuth` 与 `role` 拦截，`ADMIN` 可进入卖家页。

## 本地启动

推荐用一键启停脚本，会按 Nacos → gateway → 各业务服务的顺序启动并写日志到 `logs/`：

```bat
cd src\scripts
dev.bat start      :: 启动全部
dev.bat status     :: 查看端口与 Nacos 注册情况
dev.bat stop       :: 停止全部
```

用法与可选组件见 [一键启停说明](src/docs/一键启停说明.md)；需要手动逐个 `mvn spring-boot:run` 时，参照 [本地启动指南](src/docs/本地启动指南.md)。

启动后可访问：前端 http://localhost:5173 ，网关健康检查 http://localhost:9000/actuator/health ，Nacos 控制台 http://localhost:8848/nacos 。

启动前需自行设置环境变量（配置文件不含默认值）：

```powershell
$env:DB_PASSWORD = "你的 MySQL 口令"
$env:JWT_SECRET  = "至少32位的随机字符串"   # 网关与 user-service 必须一致
```

变量清单与初始管理员账号初始化方式见 [本地启动指南](src/docs/本地启动指南.md) 第四节。

## 文档导航

| 文档 | 说明 |
| --- | --- |
| [**团队协作限制**](docs/04-团队协作限制.md) | 禁止操作、提交流程、服务分工、AI 使用限制（动手前先看） |
| [**接口契约**](docs/05-接口契约.md) | 各服务接口、数据结构、错误码、调用关系（写代码前先看） |
| [**开发规范**](docs/06-开发规范.md) | 项目结构、命名、并发一致性写法、数据库、日志、审查要点 |
| [一键启停说明](src/docs/一键启停说明.md) | `dev.bat` 的启停命令、可选组件与日志位置 |
| [本地启动指南](src/docs/本地启动指南.md) | 手动逐步启动、环境变量、常见问题、端口速查 |
| [实验环境说明](docs/01-实验环境说明.md) | 建模、开发、部署、AI 工具选型与理由 |
| [总体实验规划](docs/02-总体实验规划.md) | 实验一到四的路线、交付物与验收标准 |
| [Git 协作与版本留痕规范](docs/03-Git协作规范.md) | 分支策略、Commit 规范、Issue 与看板流程 |
| [任务完成情况核对](docs/07-任务完成情况核对.md) | #12 用户鉴权与 #13 前端商城化的逐条验收记录及后续跟进 |
| [实验一：需求规格说明书](reports/实验一_需求规格说明书.docx) | 需求分析与迭代规划报告正文 |

## 验证状态与边界

**已实机跑通**（本地 Nacos + MySQL + 六个后端服务 + 前端，验证记录更新至 2026-10-27）：

- 注册与登录，网关签发与校验 JWT
- 加购 → 下单 → 创建支付单 → MOCK 支付回调
- 支付回调幂等（重复回调不重复改单）
- 订单取消与超时释放（定时补偿）
- 发货、退款与库存回补
- 买家 / 卖家 / 管理员三角色页面可访问并完成主要操作

**尚未完成或未验证**：

- 单元测试未整体跑通，**不宣称全部单测通过**；部分改动以静态检查、接口联调与页面验收为主，前端 `pnpm build` 已单独执行通过
- Redis 未落地接入：当前运行不依赖 Redis，一致性靠 DB 唯一约束/幂等令牌、条件更新（乐观版本或状态条件）与 `order.compensation` 定时补偿实现；契约中的 Redis 方案属规划
- Docker Compose 与容器化部署未验收，`src/docker/` 仅作部署规划
- 管理端「系统配置」页仅保存在浏览器 localStorage，不是配置中心，接入 Nacos 配置中心前不改动真实服务配置
- 卖家退款列表尚未按 `sellerId` 过滤，卖家侧会看到全平台退款单

## 团队协作

本项目使用 Git 管理版本，任务通过 [GitHub Projects 看板](https://github.com/users/TheCryGirl/projects/1) 跟踪，分支与提交规范见 [Git 协作与版本留痕规范](docs/03-Git协作规范.md)。

## AI 使用声明

本项目在需求梳理、UML 源文件编写、前端骨架搭建、缺陷修复与文档排版等环节使用了 AI 辅助工具（DeepSeek Harness）。所有 AI 生成的代码与文档均经过人工审查与修改，具体使用情况记录在各报告的「AI 使用记录」章节中。
