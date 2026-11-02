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

### 冷启动后商品库为空是预期行为

`src/docker/init-db.sql` 只创建五个空库，建表由各服务的 `schema.sql` 在启动时执行，分类种子在 `product-service` 的 `data.sql` 里，**没有任何商品种子**。所以从零部署后 `GET /api/products` 返回 `total=0` 是正常现象，不是服务异常。

需要演示数据时手动运行（不随部署自动执行）。卖家口令通过环境变量传入并保持稳定，**同一账号 + 同一口令重复执行才是幂等的**：

```bash
cd src/docker

# verify 部署：网关在 19000（脚本默认值）
DEMO_SELLER_PASSWORD='自己定的演示口令' bash demo-init.sh

# 普通 compose 部署：网关在 9000，必须显式指定（口令沿用上文导出的 DEMO_SELLER_PASSWORD，不在命令行写明文）
bash demo-init.sh --gateway http://127.0.0.1:9000

# 只预检，不写数据
bash demo-init.sh --dry-run
```

口令统一通过环境变量 `DEMO_SELLER_PASSWORD` 传入（可先 `export` / `read -s` 隐藏输入再导出），**不建议把明文口令写在命令行上**——那会留在 shell 历史与进程列表里。

口令未通过环境变量提供时，脚本在交互终端隐藏输入；非交互环境直接报错退出，不会随机生成，也不写 `.env`、不回显口令。管理员配置读同目录 `.env` 的 `ADMIN_BOOTSTRAP_ACCOUNT` / `ADMIN_BOOTSTRAP_PASSWORD`（**`.env` 需自行创建，仓库不含该文件也不含真实凭据**；不打印、不落盘）。依赖 `bash` + `curl` + `python3`；任一请求 HTTP 非 200 或业务 `code` 非 0、必需字段（`data.token` / `data.productId`）缺失都立即以退出码 `2` 停止，日志只留标签与码值、不打印响应体。

脚本只负责**小型演示环境**的初始化：写入前先在单页里数出现有商品数与尚缺演示商品数，**现有 + 尚缺 > 96**（现有 + 尚缺 ≤ 96 < 100，包含新增后仍完整落一页；现有本身超过 100 件）也拒绝，在业务写入前以退出码 `3` 退出，因此已完整初始化的账号重跑仍成功；去重与归属校验只在 `size=100` 的**单页**内做（服务端 `/mine` 按 `ORDER BY created_at DESC` 排序、`created_at` 为秒级 `DATETIME` 且无稳定二级键，跨页比对会漏数据），并校验每次非过滤响应的 `list` 长度与 `total` 一致。账号被冻结（业务码 `20004`）时直接报冻结退出，不会被当作“账号不存在”去注册。审核只针对自己名下带 `[DEMO_SEED]-` 标记的 productId，不会碰到其他卖家的商品，也不会修改管理员账号。**不要并发运行，也不要指向与业务共用的卖家账号。**完整说明见根目录 [README](../README.md) 的「冷启动后商品列表为空是预期行为」。

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
