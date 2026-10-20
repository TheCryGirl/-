# 本地运行说明

本项目当前包含两个可运行部分：Vue 3 前端（`src/web`）与 Spring Boot 参考后端（`src/campusEasy`）。

## 一、前端（`src/web`）

### 环境要求

- Node.js 18+（本项目实测 24.18.0）
- pnpm 9+（本项目实测 11.21.0）

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

开发期由 Vite 代理转发到后端，规避浏览器跨域（见 `vite.config.js`）：

| 前端路径 | 转发到 |
| --- | --- |
| `/api/*` | `http://localhost:8080/api/*` |
| `/images/*` | `http://localhost:8080/images/*` |

后端未启动时，页面会显示「Request failed with status code 502」，属于预期现象。

### 页面结构

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/products` | 商品列表 | 搜索、分类筛选、价格区间、排序、分页 |
| `/products/:id` | 商品详情 | — |
| `/login` | 登录 | — |
| `/register` | 注册 | — |
| `/sell` | 发布商品 | 需登录，含图片上传 |
| `/my/products` | 我的商品 | 需登录，上下架、删除 |
| `/my/orders` | 我的订单 | 需登录，买家/卖家视角切换 |
| `/messages` | 消息 | 需登录，私聊 |

## 二、后端（`src/campusEasy`）

### 环境要求

- JDK 17（`pom.xml` 中 `java.version` 为 17）
- MySQL 8.0
- Redis（可选，用于缓存）
- Maven（或使用自带的 `mvnw.cmd`）

### 必须配置的环境变量

数据库口令与 JWT 密钥**不入库**，运行前需设置：

| 变量 | 说明 | 示例 |
| --- | --- | --- |
| `DB_PASSWORD` | MySQL 的 root 口令 | 你的本机口令 |
| `JWT_SECRET` | JWT 签名密钥，生产环境务必改掉默认值 | 一段足够长的随机字符串 |

PowerShell 中设置：

```powershell
$env:DB_PASSWORD = "你的数据库口令"
$env:JWT_SECRET = "一段随机字符串"
```

### 启动

```bash
cd src/campusEasy
./mvnw.cmd spring-boot:run
```

默认端口 8080。数据库连接串在 `src/main/resources/application.yaml`，库名 `campus_chat`，首次启动时 `schema.sql` 会自动建表。

### 说明

`src/campusEasy` 是课程前序实践留下的参考原型（校园二手交易平台），作为业务代码与技术栈起点参考。它与本课程选题六「在线商城系统」在业务模型上并不完全对应——缺少购物车、支付与订单状态机，这些是本项目需要新设计与实现的部分。详见 `docs/02-总体实验规划.md` 实验三章节。

## 三、设计文件的重新生成

UML 图由 PlantUML 源文件渲染，源文件在 `design/uml/`。

### 环境要求

- Java 11+（本项目实测 JDK 24）
- `plantuml.jar`（本项目实测 1.2024.7，未入库，需自行下载）

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

## 四、实验报告

报告在 `reports/`。其中 `实验一_需求规格说明书.docx` 由 `design/scripts/` 下的两个脚本处理版式（图片缩放与分页、题注编号、目录生成），处理链为：

```bash
python design/scripts/postprocess_docx.py reports/实验一_需求规格说明书.docx
python design/scripts/restructure_template.py reports/实验一_需求规格说明书.docx
```

脚本只调整版式，不改正文文字。正文修改请直接在 WPS / Word 中进行。
