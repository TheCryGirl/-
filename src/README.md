# 本地运行说明

本项目包含以下可运行部分：

- **前端**：`src/web`（Vue 3 + Element Plus，改造进行中）
- **后端**：六个 Spring Boot 微服务（gateway / user / product / cart / order / payment）
- **参考原型**：`src/campusEasy`（课程前序实践留下，仅作技术栈参考，不参与本系统运行）

---

## 一、前端（`src/web`）

### 环境要求

- Node.js 18+（本项目实测 24.18.0）
- pnpm 9+（本项目实测 11.21.0，**用 pnpm，不要用 npm**，仓库只有 `pnpm-lock.yaml`）

### 启动

```bash
cd src/web
pnpm install
pnpm dev
```

访问 http://localhost:5173/

### 构建

```bash
pnpm build      # 产物输出到 dist/
pnpm preview    # 本地预览构建产物
```

### 接口代理

开发期由 Vite 代理转发到网关，规避浏览器跨域（见 `vite.config.js`）：

| 前端路径 | 转发到 |
| --- | --- |
| `/api/*` | 网关（默认 `http://localhost:9000`） |
| `/images/*` | 网关 |
| `/avatars/*` | 网关 |

网关地址可用环境变量覆盖，**Docker 部署时网关映射到 19000**：

```bash
VITE_GATEWAY_TARGET=http://localhost:19000 pnpm dev
```

后端未启动时页面会显示 502，属于预期现象。

### 技术栈

| 项 | 版本 |
| --- | --- |
| Vue | 3.5 |
| Vue Router | 5.4 |
| Vite | 8.3 |
| Tailwind CSS | 4.3 |
| Element Plus | 2.14（按需引入） |

### 目录结构

```
src/web/src/
├── api/index.js           接口封装（禁改，页面直接调用）
├── stores/auth.js         登录态（禁改）
├── router/index.js        路由与守卫（禁改）
├── constants/status.js    状态枚举与文案唯一来源
├── components/
│   ├── AppHeader.vue      顶部导航
│   ├── ProductCard.vue    商品卡片
│   └── ui/                共享组件：UiTable / confirm / message
├── composables/
│   └── usePagination.js   分页约定
├── views/                 业务页面（29 个）
│   ├── _StandardPage.vue  ★ 标准页骨架，改造时照抄
│   ├── admin/             管理端 10 页
│   └── seller/            卖家端 6 页
└── style.css              全局样式（分层导入，禁改）
```

### 主要路由

| 路由 | 页面 | 角色 |
| --- | --- | --- |
| `/products` | 商品列表 | 公开 |
| `/products/:id` | 商品详情 | 公开 |
| `/login`、`/register` | 登录、注册 | 公开 |
| `/cart` | 购物车 | 买家 |
| `/orders/confirm` | 确认订单 | 买家 |
| `/orders` | 我的订单 | 买家 |
| `/orders/:id` | 订单详情 | 买家 |
| `/orders/:id/pay` | 支付 | 买家 |
| `/after-sales` | 售后 | 买家 |
| `/profile` | 个人中心 | 买家 |
| `/favorites` | 收藏 | 买家 |
| `/history` | 浏览足迹 | 买家 |
| `/seller/**` | 卖家端 | 卖家 |
| `/admin/**` | 管理端 | 管理员 |

路由守卫按 `requiresAuth` 与 `role` 拦截，`ADMIN` 可进入卖家页。

### 前端改造须知

前端正在从手写 Tailwind 迁移到 Element Plus。**参与改造前必读 [开工卡](../docs/ui-plan/开工卡.md)**，里面有一屏讲完的分支名、试点页、禁止项和自查命令。

---

## 二、后端（六个微服务）

### 环境要求

- JDK 17
- Maven 3.8+
- MySQL 8.0（一服务一库）
- Nacos 2.3.2（单机模式）

### 必须配置的环境变量

口令与密钥**不入库**，运行前需设置：

| 变量 | 说明 |
| --- | --- |
| `DB_PASSWORD` | MySQL root 口令 |
| `JWT_SECRET` | JWT 签名密钥，网关与 user-service 必须一致 |
| `ADMIN_BOOTSTRAP_ACCOUNT` / `ADMIN_BOOTSTRAP_PASSWORD` | 初始管理员，不配置则不创建 |

### 启动

推荐 Docker Compose（八容器一键起）：

```bash
cd src/docker
docker compose -f docker-compose.yml -f docker-compose.verify.yml up -d
```

注意 WSL 下 dockerd 需用独立进程方式启动，详见根目录 [README](../README.md) 的「本地启动」一节。

本地逐个启动用 `src/scripts/dev.bat`，见 [一键启停说明](docs/一键启停说明.md)。

### 构建与测试

```bash
cd src
mvn -B test          # 聚合构建，125 个用例
mvn -B -pl order-service test   # 单个模块
```

### 服务与端口

| 服务 | 端口 | 数据库 |
| --- | --- | --- |
| gateway | 9000 | — |
| user-service | 9001 | mall_user |
| product-service | 9002 | mall_product |
| cart-service | 9003 | mall_cart |
| order-service | 9004 | mall_order |
| payment-service | 9005 | mall_payment |

---

## 三、参考原型（`src/campusEasy`）

课程前序实践留下的参考原型（校园二手交易平台），作为技术栈起点参考。

它与本课程选题六「在线商城系统」在业务模型上并不完全对应——缺少购物车、支付与订单状态机，这些是本项目新设计与实现的部分。**不参与本系统运行**，构建时也已被聚合 pom 排除。

详见 [总体实验规划](../docs/02-总体实验规划.md) 实验三章节。

---

## 四、设计文件的重新生成

UML 图由 PlantUML 源文件渲染，源文件在 `design/uml/`。

### 环境要求

- Java 11+（本项目实测 JDK 24）
- `plantuml.jar`（实测 1.2024.7，未入库，需自行下载）

### 渲染

```bash
# 把 plantuml.jar 放到项目根目录的 _tools/ 下
python design/scripts/render_uml.py
```

脚本会把 PNG 输出到 `design/images/`。注意：PlantUML 在含中文的路径下会静默不产出文件，脚本已做临时目录中转处理。

### 用户故事地图

```bash
python design/scripts/gen_storymap.py
```

用 Pillow 绘制 `design/images/fig1-1_story_map_buyer.png` 与 `fig1-2_story_map_seller.png`。

---

## 五、实验报告

报告在 `reports/`。其中 `实验一_需求规格说明书.docx` 由 `design/scripts/` 下的两个脚本处理版式（图片缩放与分页、题注编号、目录生成）：

```bash
python design/scripts/postprocess_docx.py reports/实验一_需求规格说明书.docx
python design/scripts/restructure_template.py reports/实验一_需求规格说明书.docx
```

脚本只调整版式，不改正文文字。正文修改请直接在 WPS / Word 中进行。
