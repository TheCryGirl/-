# 在线商城 · 前端工程

本目录是 `--main/src/web` 前端工程的**完整独立副本**（源码 + 静态资源 + 测试 + 配置），
已包含本轮「优化并美化」的全部改动。不含 `node_modules` 与 `dist`，二者可由锁文件与构建命令复现。

## 技术栈

- Vue 3（`<script setup>`）+ Vue Router
- Vite 8 + Tailwind CSS 4（跳过 preflight，与 Element Plus 共存）
- Element Plus（unplugin 按需引入）
- 原生 `node:test` 回归测试（不依赖浏览器）

## 运行

```bash
# 1. 安装依赖（Node 22.12+ 或 24）
pnpm install --frozen-lockfile

# 2. 启动开发服务器（默认 5173，代理 /api 等到网关）
pnpm dev

# 3. 生产构建 / 预览
pnpm build
pnpm preview

# 4. 回归测试（67 个用例，纯 node，无需起服务）
node --test tests/*.test.mjs
```

开发期网关地址默认 `http://localhost:9000`，可用环境变量覆盖（Docker 部署为 19000）：

```bash
VITE_GATEWAY_TARGET=http://localhost:19000 pnpm dev
```

> 前端只负责界面；登录、商品、购物车等数据来自后端网关。单独运行本目录时，
> 需先按仓库 `docs/演示一键启动.md` 把后端全栈拉起，否则页面会提示接口不可用。

## 优化与美化要点

### 一、结构与性能

1. **移动端头部紧凑化**：390px 下头部由 4 行压缩为 2 行（分类按钮收成图标、
   消息/购物车只留图标与角标、登录/注册收紧），搜索框独占整行；
   导航条单行横滑不折行，右端渐隐遮罩提示可继续滑动。
2. **购物车移动端卡片化**：窄屏隐藏表格，改为「图 + 标题 + 规格 + 价格 + 数量 + 删除」卡片，
   复用同一批勾选/数量/删除逻辑与 `data-testid`；结算条贴底、按钮全宽。
3. **全局响应式兜底层**：为购物车/订单/售后/个人中心/工作区等内页补齐断点——
   卡片内边距、表格横滑、筛选表单整行堆叠、对话框近全宽、描述列表单栏、步骤条缩字等。
4. **构建分包**：`vue`/`vue-router` 与 `element-plus` 拆为独立长缓存 chunk，业务更新不必重下框架。
5. **图片压缩**：`public/media` 下 JPG 统一限边 1600px、quality 82 渐进式，总体积约 -12%。

### 二、鲜艳视觉重塑（`src/styles/coral.css`）

1. **明亮多色系统**：暖白纸底 `#fff6ec` + 高饱和重音——珊瑚 `#ff4d6d`、
   橙 `#ff7a45`、琥珀 `#ffb703`、紫 `#8b5cf6`、天蓝 `#38bdf8`。
2. **Hero 三套鲜亮渐变**：珊瑚→橙→琥珀 / 紫→蓝 / 橙→琥珀。
   琥珀主题用「亮黄底 + 深墨字」海报式高对比（黑底黄字 CTA），红/紫主题用白字。
3. **杂志式巨型排版**：Bebas Neue（拉丁/数字）+ 自托管 Noto Sans SC 900 字重子集（中文标题）
   + Inter 正文；标题按语义断行，`text-wrap: balance` 消除窄屏孤字。
4. **内页标题区（PageHero）换肤**：撤掉原先压在实拍照片上的深墨蒙版
   （`rgba(24,20,32,.82)`，是个人中心/我的订单发灰的根因），改为鲜亮渐变；
   四种 tone 各配一套高饱和配色，标题区内的计数文字转白、
   单选按钮选中态改深墨底白字，避免珊瑚压珊瑚。
5. **灰调清扫**：Element Plus 主色与 Tailwind 的 gray/red/blue 色标统一映射到鲜艳 palette；
   卡片头、表头、图片占位、骨架屏、分店小计等冷灰面改暖纸/珊瑚浅底；
   空态纸箱插画整组换珊瑚粉调（覆盖 `--el-empty-fill-color-*` 并直接给 SVG 形状上色）。
6. **页脚与登录页**：页脚紫→天蓝渐变、热线用 Bebas 琥珀数字；
   登录品牌区珊瑚→橙→紫渐变；管理端顶栏紫→珊瑚渐变。
7. **字体自托管**：`public/fonts/` 共 313 KB（Bebas Neue、Inter 五字重、
   Noto Sans SC 900 子集），附 OFL 许可证，不依赖外网 CDN。

### 三、顺带修掉的两个既有缺陷

1. **路由过渡卡死导致整页内容隐形**：`App.vue` 的
   `<transition name="page-fade" mode="out-in">` 在某些时序下会留下不结束的
   opacity 过渡——过渡类已移除但计算值仍停在 `0`，整页只剩残影。
   该残留由动画本身持有，纯 CSS 兜底压不住，已在 `@after-enter` /
   `@enter-cancelled` / `@after-leave` 钩子里取消残留动画并复位。
2. **个人中心卡片嵌套**：「我的服务」原本嵌在「个人资料」卡片内部，
   出现双层卡头，已提为同级卡片。

## 目录

```
src/
  api/          接口封装（axios，Bearer 鉴权）
  components/   通用与前台/卖家/管理组件
  composables/  复用逻辑
  constants/    状态枚举
  data/         静态兜底展示数据
  directives/   v-reveal 滚动渐显
  router/       路由与角色守卫
  stores/       登录态
  utils/        格式化/角色/店铺入驻等纯函数
  views/        买家/卖家/管理页面
  style.css     全局样式入口（设计令牌 + 响应式层）
public/         favicon、图标、商品/背景图
tests/          node:test 回归
```
