# 云原生微服务架构实践：在线商城系统

河北工业大学《软件工程实验》（2026 版）课程项目，选题六。

以 Nacos 注册发现 + Spring Cloud Gateway + 多服务多库为主线，实现一个包含买家、卖家、平台管理员三类角色的在线商城；重点实践微服务拆分、服务间调用与数据一致性、网关统一鉴权、幂等与补偿。

## 目录结构

```
.
├── docs/                      项目文档（环境、规划、协作规范、接口契约、任务核对、UI 改造）
│   └── ui-plan/               UI 改造方案：开工卡、公共合同、统一规范、五人任务书
├── reports/                   各次实验报告
├── design/                    UML 源文件（uml/）、渲染图（images/）与生成脚本（scripts/）
├── logs/                      本地启动日志（dev.bat 生成，不纳入版本控制）
├── .github/                   PR 模板（8 项自检）
└── src/
    ├── gateway/               API 网关（9000）：统一路由与 JWT 鉴权
    ├── user-service/          用户服务（9001），库 mall_user
    ├── product-service/       商品服务（9002），库 mall_product
    ├── cart-service/          购物车服务（9003），库 mall_cart
    ├── order-service/         订单服务（9004），库 mall_order
    ├── payment-service/       支付服务（9005），库 mall_payment
    ├── web/                   Vue 3 前端（开发端口 5173）
    ├── campusEasy/            既有参考原型（Spring Boot），不作为本系统运行部分
    ├── docker/                docker compose 编排与 init-db.sql（已实测验收，见下文）
    └── scripts/               dev.bat / dev.ps1 本地一键启停
```

## 技术栈

| 层次 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.2.5 / JDK 17 / MyBatis / OpenFeign |
| 注册与发现 | Spring Cloud Alibaba Nacos 2.3.2（单机模式） |
| 网关 | Spring Cloud Gateway + JJWT（网关统一鉴权，注入 `X-User-Id` / `X-User-Role`） |
| 数据库 | MySQL 8.0，一服务一库（五个业务库） |
| 前端 | Vue 3.5 + Vue Router 5 + Vite 8 + Tailwind CSS 4 |
| 组件库 | **Element Plus 2.14**（按需引入，2026-11 引入，改造进行中） |
| 建模 | PlantUML（.puml 源文件在 `design/uml/`） |
| 可观测性 | 全链路 TraceId（网关生成 + 五服务 MDC 透传 + Feign 传递） |
| 部署 | Docker Compose（八容器：六服务 + Nacos + MySQL，已实测） |
| Redis | 未接入；一致性靠 DB 唯一约束、条件更新与定时补偿 |

## 功能概览（按角色）

| 角色 | 已实现 |
| --- | --- |
| 买家 | 注册登录、商品列表与详情、购物车、收藏、浏览足迹、确认下单、支付（MOCK 渠道）、订单列表/详情、取消、确认收货、评价、申请售后（仅退款 / 退货退款）、个人中心与头像 |
| 卖家 | 商品管理（上下架）、发布/编辑商品与 SKU、调库存、待发货与发货、退款审核、售后处理、店铺资料与营业状态 |
| 平台管理员 | 用户管理、店铺管理、商品审核、退款复核、售后仲裁、资金对账、基础风控、系统配置、评价管理、审计日志，**共 10 个页面** |

前端路由见 `src/web/src/router/index.js`：`/products`、`/cart`、`/orders/**` 为买家侧，`/seller/**` 为卖家侧，`/admin/**` 为管理员侧；路由守卫按 `requiresAuth` 与 `role` 拦截，`ADMIN` 可进入卖家页。

## 本地启动

### 方式一：Docker Compose（推荐，已实测）

八个容器一键启动，含 Nacos、MySQL 与六个服务。

```bash
# 1. 启动 dockerd
#    注意：WSL 下 systemd 的 socket 激活方式会导致 daemon 周期性重启，
#    必须用独立进程启动，否则容器会反复重启
wsl -d Ubuntu -u root -- bash -c 'systemctl stop docker.service docker.socket; \
  setsid nohup /usr/bin/dockerd -H unix:///var/run/docker.sock \
  --containerd=/run/containerd/containerd.sock > /var/log/dockerd-standalone.log 2>&1 < /dev/null &'
sleep 12

# 2. 启动全栈（verify overlay 用于避开宿主机 3306 端口占用）
cd src/docker
docker compose -f docker-compose.yml -f docker-compose.verify.yml up -d
# 等待约 1 分钟，确认 Nacos 与 MySQL 健康后再访问

# 3. 前端（网关映射到 19000）
cd src/web
VITE_GATEWAY_TARGET=http://localhost:19000 pnpm dev
```

访问地址：前端 http://localhost:5173 ，网关 http://localhost:19000 ，Nacos 控制台 http://localhost:18848/nacos ，MySQL 端口 13306。

**管理员账号**通过环境变量初始化，不写入默认值：

```bash
# src/docker/.env（不提交）
ADMIN_BOOTSTRAP_ACCOUNT=admin
ADMIN_BOOTSTRAP_PASSWORD=你的口令
```

### 方式二：本地逐个启动

推荐用一键启停脚本，会按 Nacos → gateway → 各业务服务的顺序启动并写日志到 `logs/`：

```bat
cd src\scripts
dev.bat start      :: 启动全部
dev.bat status     :: 查看端口与 Nacos 注册情况
dev.bat stop       :: 停止全部
```

用法与可选组件见 [一键启停说明](src/docs/一键启停说明.md)；手动逐个 `mvn spring-boot:run` 参照 [本地启动指南](src/docs/本地启动指南.md)。

启动前需设置环境变量（配置文件不含默认值）：

```powershell
$env:DB_PASSWORD = "你的 MySQL 口令"
$env:JWT_SECRET  = "至少32位的随机字符串"   # 网关与 user-service 必须一致
```

## 构建与测试

```bash
# 后端全量回归（聚合 pom，一条命令跑完六个模块）
cd src && mvn -B test        # 125 个用例

# 前端构建
cd src/web && pnpm build
```

## 前端改造状态：进行中

前端正在从手写 Tailwind 迁移到 Element Plus，目标是统一视觉与交互。基础层已完成并合入 main：

| 已完成 | 说明 |
| --- | --- |
| Element Plus 接入 | 2.14.7，按需引入，主包体积从全量引入的 1078 kB 降到 70 kB |
| Tailwind 共存 | 显式分层导入跳过 preflight，base 规则用 `:where()` 包裹避免与组件库抢优先级 |
| 共享组件 | `UiTable`、`confirmAction`、`ElMessage` 封装、`usePagination` |
| 状态枚举 | `src/constants/status.js`，订单/商品/角色/售后/支付文案唯一来源 |
| 标准页骨架 | `src/views/_StandardPage.vue`，可直接照抄的页面结构 |

**业务页面改造分四块并行**：管理端 10 页（yy）、买家端 8 页（来财）、卖家端 6 页（韬哥）、登录注册与收藏历史 4 页（fd）。

参与改造请先读 **[开工卡](docs/ui-plan/开工卡.md)**（唯一入口），其余文档当字典查：

| 文档 | 用途 |
| --- | --- |
| [开工卡](docs/ui-plan/开工卡.md) | 分支名、试点页、五个不许、提 PR 前自查命令 |
| [公共合同 v1](docs/ui-plan/公共合同-v1.md) | 共享组件的接口与用法 |
| [前端统一开发规范](docs/ui-plan/前端统一开发规范.md) | UI 风格、状态、接口调用、验收标准 |
| [UI 改造任务规划](docs/10-UI改造任务规划.md) | 总规划与分工 |

## 文档导航

| 文档 | 说明 |
| --- | --- |
| [**项目现状**](docs/项目现状.md) | **当前架构、端口、完成范围与未完成项（新人先看这个）** |
| [**团队协作限制**](docs/04-团队协作限制.md) | 禁止操作、提交流程、服务分工、AI 使用限制（动手前先看） |
| [**接口契约**](docs/05-接口契约.md) | 各服务接口、数据结构、错误码、调用关系（写代码前先看） |
| [**开发规范**](docs/06-开发规范.md) | 项目结构、命名、并发一致性、前端组件与状态规范、审查要点 |
| [一键启停说明](src/docs/一键启停说明.md) | `dev.bat` 的启停命令、可选组件与日志位置 |
| [本地启动指南](src/docs/本地启动指南.md) | 手动逐步启动、环境变量、常见问题、端口速查 |
| [实验环境说明](docs/01-实验环境说明.md) | 建模、开发、部署、AI 工具选型与理由 |
| [总体实验规划](docs/02-总体实验规划.md) | 实验一到四的路线、交付物与验收标准 |
| [Git 协作与版本留痕规范](docs/03-Git协作规范.md) | 分支策略、Commit 规范、Issue 与看板流程 |
| [任务完成情况核对](docs/07-任务完成情况核对.md) | #12 用户鉴权与 #13 前端商城化的逐条验收 |
| [任务核对（#49–#52）](docs/08-任务核对-店铺搜索资料收藏.md) | 店铺营业状态、搜索与分类、地址与资料、收藏与足迹 |
| [任务核对（#56–#58）](docs/09-任务核对-购物车鉴权与评价审计.md) | 购物车鉴权、评价与晒图、操作审计日志 |
| [历史归档](docs/archive/) | 已过期的过程文档，仅作留痕 |

## 验证状态与边界

**已实机跑通**（本地 Docker Compose 全栈，验证记录更新至 2026-11-01）：

- 后端接口链路：`/api/products`、`/api/categories` 等 HTTP 200，响应 10~13 ms
- 注册、登录、网关签发与校验 JWT
- 加购 → 下单 → 创建支付单 → MOCK 支付回调 → 订单状态变 PAID → 库存正确扣减
- 同一幂等键重复下单返回同一订单，50 个并发请求只产生 1 个订单、库存只扣 1 件
- 支付回调幂等（重复回调不重复改单）
- 订单取消与超时释放（定时补偿）、发货、退款与库存回补
- 买家 / 卖家 / 管理员三角色页面在真实浏览器中可访问并完成主要操作
- 全链路 TraceId：请求头带 `X-Trace-Id` 时原样回写，未带时自动生成
- 压力测试：6 个只读场景，P95 延迟 19~101 ms，错误率 0
- 后端 125 个单元/集成测试用例

**尚未完成或未验证**：

- **前端 UI 改造进行中**：基础层已完成，29 个业务页面仍为原生 HTML 实现，改造未铺开
- **购买主链未在浏览器中完整走一遍**：业务页面未改造，页面级下单-支付-发货链路待 UI 改造后补测
- 故障注入未做：补偿与降级逻辑已实现但未用故障场景验证
- 可观测性仅到 TraceId，未接入 Prometheus / Grafana
- 压力测试只覆盖只读接口，写路径吞吐未测
- Redis 未落地：一致性靠 DB 唯一约束/幂等令牌、条件更新与 `order.compensation` 定时补偿实现；契约中的 Redis 方案属规划

> 历史遗留问题的处理状态见 [实验四报告](reports/实验四_测试与部署.docx) 第 4.5 节「遗留问题」表。

## 团队协作

本项目使用 Git 管理版本，任务通过 [GitHub Projects 看板](https://github.com/users/TheCryGirl/projects/1) 跟踪，分支与提交规范见 [Git 协作与版本留痕规范](docs/03-Git协作规范.md)。

提 PR 时 `.github/pull_request_template.md` 会自动带出 8 项自检清单。

## AI 使用声明

本项目在需求梳理、UML 源文件编写、前端骨架搭建、缺陷修复与文档排版等环节使用了 AI 辅助工具（DeepSeek Harness）。所有 AI 生成的代码与文档均经过人工审查与修改，具体使用情况记录在各报告的「AI 使用记录」章节中。
