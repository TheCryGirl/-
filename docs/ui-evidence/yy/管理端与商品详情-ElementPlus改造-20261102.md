# #79 管理端 10 页 + 商品详情 Element Plus 改造 —— 验收证据

> 验收人：yynqycq｜日期：2026-11-02 10:05 CST
> 分支：`feature/ui-yy-admin`
> 基线提交：`7978202`（docs: 新增真实后端闭环冒烟证据（2026-11-02）；改造首版基于 `6d1ae51`，后已 rebase 到 `7978202`）
> 提交范围：`f3a51ed`、`eac145e`、`56e09e1`、`30e30f7`、`8888539`、`824888b`、`0d75217`

---

## 一、交付范围

| 任务ID | 文件 | 提交 | 说明 |
| --- | --- | --- | --- |
| Y01 | `src/web/src/views/admin/AdminUsers.vue` | `f3a51ed` | 试点页：用户列表、角色变更、冻结/解冻 |
| Y02 | `src/web/src/views/admin/AdminShops.vue` | `eac145e` | 店铺列表、停用/恢复营业 |
| Y03 | `src/web/src/views/admin/AdminProducts.vue` | `eac145e` | 待审核商品（保留卡片结构） |
| Y04 | `src/web/src/views/admin/AdminEvaluations.vue` | `56e09e1` | 评价列表、隐藏/恢复 |
| Y05 | `src/web/src/views/admin/AdminRefunds.vue` | `56e09e1` | 退款单列表、复核裁定 |
| Y06 | `src/web/src/views/admin/AdminAfterSales.vue` | `56e09e1` | 售后仲裁（保留卡片结构） |
| Y07 | `src/web/src/views/admin/AdminAuditLogs.vue` | `30e30f7` | 审计日志、日期筛选、分页 |
| Y08 | `src/web/src/views/admin/AdminConfig.vue` | `30e30f7` | 超时配置、整数边界、配置历史 |
| Y09 | `src/web/src/views/admin/AdminFinance.vue` | `30e30f7` | 财务统计、按日对账 |
| Y10 | `src/web/src/views/admin/AdminRisk.vue` | `30e30f7` | 风控统计、风险明细 |
| Y11 | `src/web/src/views/ProductDetail.vue` | `8888539` | 商品详情、SKU、加购、收藏 |

列表总量文案（#95 后续要求）单独一个提交：`0d75217`，见第三节之后的「补充：#95 列表总量文案」。

diff 统计：代码部分 `11 files changed, 1696 insertions(+), 1186 deletions(-)`（相对 `7978202`），仅上述 11 个文件；加上证据目录后总计 69 个文件（57+9 张截图 + 1 个 md），无 `src/api/`、`src/stores/`、`src/router/`、`package.json`、`pnpm-lock.yaml`、公共组件改动。

---

## 二、环境

| 项 | 值 |
| --- | --- |
| 操作系统 | Windows（DSH 沙箱，workspace-write） |
| Node | v24.14.0 |
| Vite | 8.3.5 |
| Vue | 3.5.44 |
| Element Plus | 2.14.7 |

### 关于验收后端（重要，请先读这段）

本机**没有可用的 Docker 守护进程**（`docker version` 连接 `dockerDesktopLinuxEngine` 失败），无法按 `docs/ui-evidence/kunu/集成验收-20261101.md` 的步骤拉起 8 个容器；`pnpm install` 也因沙箱拒绝 pnpm 的全局 store 锁目录（`ERR_PNPM_STORE_DIR_OPEN_OPERATION_LOCK`，os error 5）无法执行，改用 `npm install` 安装同样的 `package.json` 依赖。

因此本次 UI 验收使用的是一份**本地临时 mock 网关**（Node 原生 `http`，监听 `127.0.0.1:9000`，不在仓库内、不随分支提交），通过 `VITE_GATEWAY_TARGET` 把 Vite 的 `/api` 代理指向它。它按 `src/api/index.js` 的调用约定返回 `{ code, message, data }`，并记录每一次请求的 method / path / query / body。

**这只证明了「前端发出的请求字段、目标 ID、状态流转判断」是对的，不等于真实后端的权限与业务校验已通过。** 权限拦截、真实事务、库存扣减、支付回调等仍需在 #78 全栈环境下复验，见第八节「未测项」。

mock 数据规模（仅供截图可读性）：12 用户、8 店铺、30 商品（3 条待审核）、6 评价、8 退款单（4 条待审核）、26 条审计日志、25 条配置历史、30 订单。

---

## 三、逐页验收

截图目录：`docs/ui-evidence/yy/screenshots/`。所有截图均为 1280×720 视口、真实浏览器渲染（Edge，内置 browser 工具）。

### Y01 用户管理（`AdminUsers.vue`）

| 场景 | 截图 |
| --- | --- |
| 正常列表 | `yy-p01-users-normal-v2.png` |
| 角色下拉展开 | `yy-02-role-select-open.png` |
| 角色变更确认框 | `yy-03-role-confirm.png` |
| 取消后下拉回退 | `yy-04-role-cancel-reverted.png` |
| 角色变更成功 | `yy-05-role-success.png` |
| 冻结确认框 | `yy-06-freeze-confirm.png` |
| 冻结成功 | `yy-07-freeze-success.png` |
| 空态 | `yy-e01-users-empty.png` |
| 失败态 | `yy-f01-users-fail-v2.png` |

保留行为核对：

- 目标用户为 `userId`（请求路径 `/api/users/u-002/role`），不是索引或行号。
- 角色下拉保留原三值 `BUYER` / `SELLER` / `ADMIN`，提交 `PUT /api/users/{userId}/role {"role":"SELLER"}`，与原实现字段一致。
- 冻结/解冻提交 `PUT /api/users/{userId}/status {"status":"FROZEN"}`（解冻为 `ACTIVE`），与原实现一致。
- 本人账号（`auth.user.userId`）角色下拉禁用，并显示「本人」标签，未新增删除或批量操作。
- 取消确认后下拉回退到原角色：原实现靠 `event.target.value = user.role` 强改 DOM，本版改为 `roleDraft` 草稿值，取消/失败/异常都清草稿，避免受控组件状态与视图不一致。

### Y02 店铺管理（`AdminShops.vue`）

| 场景 | 截图 |
| --- | --- |
| 正常列表 | `yy-p02-shops-normal.png` |
| 停用确认框 | `yy-s02-shops-suspend-confirm.png` |
| 空态 | `yy-e02-shops-empty.png` |
| 失败态 | `yy-f02-shops-fail.png` |

- 保留原查询条件（关键词 + 营业状态 `ACTIVE`/`SUSPENDED`）与原操作集合（停用店铺 / 恢复营业），未凭空添加「停用」以外的动作。
- 停用文案与原实现一致：「停用后「示例店铺 8」的在售商品会立即对买家隐藏，且买家无法下单。确认停用？」，`danger` 红色确定按钮。
- 行级 `operating` 忙状态，避免同一时刻对多行重复提交。

### Y03 商品审核（`AdminProducts.vue`）

| 场景 | 截图 |
| --- | --- |
| 正常卡片列表 | `yy-p03-products-normal.png` |
| 通过审核确认框 | `yy-d03-products-audit-confirm.png` |
| 审核成功 | `yy-d03-products-audit-success.png` |
| 空态 | `yy-e03-products-empty.png` |
| 失败态 | `yy-f03-products-fail.png` |

- **保留卡片结构**，未改成表格，未加批量审核。
- 审核提交 `PUT /api/products/p-003/audit {"approved":true,"remark":""}`，字段与原实现一致（`remark` 仍为空串）。
- 状态文案取 `PRODUCT_STATUS_MAP`，`el-tag` 展示。
- 长标题在卡片内换行，不撑破容器（见 360px 截图 `yy-w360-products.png`）。

### Y04 评价管理（`AdminEvaluations.vue`）

| 场景 | 截图 |
| --- | --- |
| 正常列表 | `yy-p04-evaluations-normal.png` |
| 隐藏确认框 | `yy-v04-evaluations-hide-confirm-v2.png` |
| 隐藏成功 | `yy-v04-evaluations-hide-success.png` |
| 空态 | `yy-e04-evaluations-empty.png` |
| 失败态 | `yy-f04-evaluations-fail.png` |

- 目标为 `evaluationId`：`PUT /api/evaluations/e-001/hide`。
- 隐藏语义与文案保留：「确定隐藏该评价？隐藏后商品详情页与评分统计都不再展示。」
- 长文本列 `tooltip:false` + `line-clamp-2` 换行截断；匿名买家（`buyerId` 为空）显示「匿名」标签。

### Y05 退款复核（`AdminRefunds.vue`）

| 场景 | 截图 |
| --- | --- |
| 正常列表 | `yy-p05-refunds-normal.png` |
| 支持退款确认框 | `yy-r05-refunds-confirm.png` |
| 空态 | `yy-e05-refunds-empty.png` |
| 失败态 | `yy-f05-refunds-fail-v2.png` |

- 确认框：「确定对退款单「r-007」裁定支持退款？金额 ¥229.00 将退回买家。」——**目标 ID 与行一致，金额已由分转元**（后端 22900 分）。
- 提交 `PUT /api/payments/refunds/{refundId}/audit {"approved":true,"remark":"管理员复核通过"}`，与原实现文案一致。
- 仅 `PENDING` 行显示驳回/支持退款，其余行显示「已处理」或「—」，保留管理员既有能力边界。

### Y06 售后仲裁（`AdminAfterSales.vue`）

| 场景 | 截图 |
| --- | --- |
| 正常卡片列表 | `yy-p06-aftersales-normal-v2.png` |
| 手工单号确认框 | `yy-b06-aftersales-manual-confirm.png` |
| 裁定成功 | `yy-b06-aftersales-arbitrate-success.png` |
| 空态 | `yy-e06-aftersales-empty.png` |
| 失败态 | `yy-f06-aftersales-fail.png` |

- **保留卡片与「按退款单号裁定」结构**，未新建审批状态流。
- 提交 `PUT /api/payments/refunds/r-007/audit {"approved":true,"remark":"ADMIN_ARBITRATION"}`，`remark` 与原实现逐字一致。
- **修正一处原实现的成功判定错误**：原代码用 `unwrapResult(data)` 判断成功，而 `auditRefund` 成功时 `data` 为 `null`，会被当成失败并吞掉成功提示；改为 `isOk(data)`（`code === 0` 即成功），符合《公共合同 v1》。
- 类型/状态用 `el-tag`（仅退款 / 退货退款、待处理等），文案取 `constants/status.js`。

### Y07 审计日志（`AdminAuditLogs.vue`）

| 场景 | 截图 |
| --- | --- |
| 正常列表 | `yy-p07-auditlogs-normal.png` |
| 日期区间筛选结果 | `yy-a07-auditlogs-date-range.png` |
| 重置后清空 | `yy-a07-auditlogs-cleared-input.png` |
| 空态 | `yy-e07-auditlogs-empty.png` |
| 失败态 | `yy-f07-auditlogs-fail.png` |

日期与分页格式（本次重点核对）：

- 日期控件用 `value-format="YYYY-MM-DD"`、`format="YYYY-MM-DD"`，绑定的就是字符串，**不经过 `Date` 对象、不做 UTC 转换**，与改造前 `<input type="date">` 提交的值完全一致。
- 实测请求：`GET /api/audit-logs?page=1&size=20&from=2026-10-20&to=2026-10-25`（纯字符串）。
- 翻页：`GET /api/audit-logs?page=2&size=20&from=2026-10-20&to=2026-10-25`，筛选条件随翻页保留，**只发一次请求**。
- 重置：回到 `GET /api/audit-logs?page=1&size=20`，`from`/`to` 不再出现在 query 里（空字符串不传参）。
- 分页起点仍为 1、默认 `size=20`，`page-sizes` 为 `[20,50,100]`。

### Y08 系统配置（`AdminConfig.vue`）

| 场景 | 截图 |
| --- | --- |
| 正常页面 | `yy-p08-config-normal-v2.png` |
| 下边界 1 / 500 提交成功 | `yy-c08-config-boundary-success.png` |
| 上边界 1440 / 1 | `yy-c08-config-boundary-1440-1.png` |
| minutes=0 越界拦截 | `yy-c08-config-invalid-0-v2.png` |
| 1441 / 501 双越界拦截 | `yy-c08-config-invalid-overflow.png` |
| 加载失败态 | `yy-f08-config-fail.png` |

边界与 payload：

- `order.timeout.minutes`：1–1440；`order.timeout.batch-size`：1–500；两者都要求整数。校验在 `validate()` 中按 `Number.isInteger` 与 `min/max` 逐项判断，错误写在 `fieldErrors[key]` 上，由 `el-form-item :error` 渲染红框，不合法时**不发出任何请求**。
- 实测越界：`minutes=0` → 红框「需为 1~1440 之间的整数」+ 顶部 `el-alert`「请先修正标红的配置项」，`requests.jsonl` 无 `PUT`。
- 实测边界：`1/500` 与 `1440/1` 均提交成功，请求体为
  `PUT /api/config {"order.timeout.minutes":"1","order.timeout.batch-size":"500"}` 和
  `{"order.timeout.minutes":"1440","order.timeout.batch-size":"1"}`——**值仍是字符串**，与改造前 `String(values[key])` 一致。
- **注意**：`el-input-number` 上没有加 `:min` / `:max` / `step-strictly`。原因是组件会在输入时把越界值静默夹回边界，导致「越界被拒绝」这条行为无法验证、也会掩盖真实输入；边界统一由提交前校验负责，错误提示更明确。
- 配置历史仍为只读 20 条：`configApi.history(20)`，无编辑/回滚入口。

### Y09 财务统计（`AdminFinance.vue`）

| 场景 | 截图 |
| --- | --- |
| 正常页面 | `yy-p09-finance-normal.png` |
| 空态 | `yy-e09-finance-empty.png` |
| 失败态 | `yy-f09-finance-fail.png` |

- 统计卡与明细金额统一用 `formatMoney(cents)`（分 → 元，两位小数），未改动口径：`PAID_STATUS = ['PAID','SHIPPED','COMPLETED','REFUNDED']`、GMV / 已收 / 已退 / 净入账 / 按日对账的聚合逻辑逐行未改。
- 未新增图表、未改统计项。
- 订单状态分布用 `statusText(ORDER_STATUS_MAP, code, code)`，未知状态回退显示原值。

### Y10 风控（`AdminRisk.vue`）

| 场景 | 截图 |
| --- | --- |
| 正常页面 | `yy-p10-risk-normal.png` |
| 空态（未发现异常） | `yy-e10-risk-empty.png` |
| 失败态 | `yy-f10-risk-fail.png` |

- 阈值一字未改：`{ refundCount: 2, refundAmount: 50000, productPrice: 100000 }`，5 条规则的判定条件与原实现一致，未重算等级阈值。
- 等级用 `el-tag`（高 danger / 中 warning / 低 info）。
- 加载失败（`el-result`）与「未发现异常」（`el-empty`）严格分离，不会把失败当成空数据。

### Y11 商品详情（`ProductDetail.vue`）

| 场景 | 截图 |
| --- | --- |
| 正常页面 | `yy-p11-product-detail-normal-v2.png` |
| SKU 售罄置灰 | `yy-k11-product-detail-sku-soldout.png` |
| 加购成功 | `yy-k11-product-detail-cart-success.png` |
| 加购失败 | `yy-k11-product-detail-cart-fail.png` |
| 加载失败态 | `yy-f11-product-detail-fail.png` |

- 保留原布局（面包屑 / 主图 / 标题 / 价格 / 分类 / 卖家 / 收藏 / SKU / 数量 / 购买区 / 评价区），仅把交互控件换成 Element Plus。
- 商品 ID 显示 `product.productId || route.params.id`；加购提交 `POST /api/cart/items {"productId":"p-001","skuId":"sku-1","quantity":1}`，商品与 SKU 的取值来源未变。
- 库存边界：`quantityMax = max(1, min(999, stockAvailable ?? 999))`，切换 SKU 时数量自动收敛；`stockAvailable <= 0` 时两个购买按钮置灰（截图 `yy-k11-product-detail-sku-soldout.png` 中「豪华版 / 白色」库存 0）。
- 无 SKU 的商品显示 `el-alert`「该商品暂无可售规格」，不进入加购流程，不出现假成功。
- 未登录仍走原 `ensureLogin()` + `redirectAfterLogin` 逻辑，登录要求未放宽。
- 价格用 `formatPrice` 分转元；评价区时间仍为 `{{ ev.createdAt || '—' }}`，与改造前逐字一致（未改格式，避免引入时区问题）。
- 加购/立即购买用 `cartBusy` / `buyBusy` 防重：slow 模式下连点 3 次「加入购物车」，`requests.jsonl` 只有 1 条 `POST`。

---

### 补充：#95 列表总量文案（提交 `0d75217`）

issue #79 最新评论（`TheCryGirl`，2026-11-01）要求按 #95 / PR #100 的处理方式，把列表总量文案改成如实标注可见范围。`src/web/src/utils/format.js` 已由基础层提供：

```js
formatTotal(total, unit = '条', shown)  // shown >= total → 「共 N 单位」
                                        // 否则 → 「共 N 单位，当前显示前 M 单位」
```

9 个含列表的管理页统一改为 `formatTotal`，位置仍在 `el-card` header 右侧：

| 页面 | 量词 | 调用 |
| --- | --- | --- |
| `AdminUsers.vue` | 条 | `formatTotal(total, '条')`（有分页，不传 shown） |
| `AdminShops.vue` | 家 | `formatTotal(total, '家')`（有分页） |
| `AdminProducts.vue` | 件 | `formatTotal(total, '件', products.length)`（无分页，`size:100` 一次取完） |
| `AdminEvaluations.vue` | 条 | `formatTotal(total, '条')`（有分页） |
| `AdminRefunds.vue` | 笔 | `formatTotal(total, '笔', refunds.length)` |
| `AdminAfterSales.vue` | 单 | `formatTotal(total, '单', tickets.length)` |
| `AdminAuditLogs.vue` | 条 | `formatTotal(total, '条')`（有分页） |
| `AdminFinance.vue` | 天 | `formatTotal(daily.length, '天')` |
| `AdminRisk.vue` | 条 | `formatTotal(risks.length, '条')` |

- `AdminConfig.vue` 没有列表，未改。
- `AdminProducts` / `AdminRefunds` / `AdminAfterSales` 原来只显示 `list.length`（当前可见条数），现补 `total` 状态：加载成功取 `payload?.total ?? list.length`，失败/异常分支置 0，避免把「只取到前 N 条」说成「共 N 条」。
- 有分页的页面只传 `total`：`total` 是后端返回的总数，当前页条数不代表可见范围，传 `shown` 反而会误导。
- 浏览器实测 header 文案：users「共 12 条」、shops「共 8 家」、products「共 3 件」、evaluations「共 6 条」、refunds「共 8 笔」、after-sales「共 4 单」、audit-logs「共 26 条」、finance「共 9 天」、risk「共 10 条」；空数据时 users 显示「共 0 条」。
- 截图：`yy-t01-users-total-v3.png`、`yy-t02-shops-total-v3.png`、`yy-t03-products-total-v3.png`、`yy-t04-evaluations-total-v3.png`、`yy-t05-refunds-total-v3.png`、`yy-t06-aftersales-total-v3.png`、`yy-t07-auditlogs-total-v3.png`、`yy-t09-finance-total-v3.png`、`yy-t10-risk-total-v3.png`。
- 量词单测（Node 直接 import `utils/format.js`）：`formatTotal(8,'笔',8)` → 「共 8 笔」；`formatTotal(128,'笔',50)` → 「共 128 笔，当前显示前 50 笔」；`formatTotal(0,'条',0)` → 「共 0 条」。

---

## 四、敏感操作验证汇总

| 页面 | 目标 ID | 取消 | 重复点击 | 失败 |
| --- | --- | --- | --- | --- |
| Y01 用户 | `PUT /api/users/u-002/role`、`/u-002/status` | 取消后无写请求、下拉回退 | 连点 3 次确定 → 1 条 `PUT` + 1 条 `GET` | `code=500` → `showError`，`busyId` 在 `finally` 复位 |
| Y02 店铺 | `PUT /api/shops/s-008/status` | 取消后无写请求 | 行级 `operating` 互斥 | 同上 |
| Y03 商品 | `PUT /api/products/p-003/audit` | 取消后无写请求 | `processing` 单值锁 | 同上 |
| Y04 评价 | `PUT /api/evaluations/e-001/hide` | 取消后无写请求 | 行级 `processing` | 同上 |
| Y05 退款 | `PUT /api/payments/refunds/r-007/audit` | 取消后无写请求 | 按钮 `disabled` + `processing` | 同上 |
| Y06 售后 | `PUT /api/payments/refunds/r-007/audit` | 取消后无写请求 | `processing` 全局互斥 | 同上 |
| Y08 配置 | `PUT /api/config`（只有合法时才发） | 无确认框，校验拦截 | `saving` loading | `loadError` / `showError` 分开 |

成功判定：全部写操作用 `isOk(data)`（`code === 0`，`data` 可为 `null`），没有一处用 `unwrapResult` 判成功。

---

## 五、宽度与溢出

用同源 iframe 探针在 11 个页面 × 4 个宽度下测量 `document.documentElement.scrollWidth` 与 `window.innerWidth`：

| 宽度 | 结果 |
| --- | --- |
| 360px | 11 页 `docW = 394`、`overflow = 34`；溢出元素**全部**是全局 Header 右下角「平台管理员 ▾」（`src/components` 公共组件，不在本次白名单内，未改） |
| 768px | 全部 `overflow ∈ {0, -15}`（-15 为滚动条宽度差） |
| 1440px | 全部 `overflow ∈ {0, -15}` |
| 1920px | 全部 `overflow ∈ {0, -15}` |

页面自身内容在 360px 下无整页横向溢出：卡片流式收窄，表格在 `el-scrollbar` 容器内横向滚动，`el-table__header` 的绝对宽度不会撑大 `documentElement`。视觉抽查：`yy-w360-users.png`、`yy-w360-products.png`、`yy-w768-users.png`。

**已知边界**：360px 时全局 Header 的导航文字被挤成竖排、并造成 34px 横向溢出。该组件不在 #79 白名单内（`src/components/` 属公共层），建议由基础层单独开单处理。

---

## 六、构建与静态自检

### 生产构建

```
$ npx vite build
✓ built in 830ms
dist/assets/AdminAuditLogs-BsdBDk8D.js   114.36 kB │ gzip: 31.58 kB
dist/assets/ProductDetail-ClqXKzIy.js     11.00 kB │ gzip:  4.51 kB
dist/assets/api-BXBThS1l.js              145.36 kB │ gzip: 54.39 kB
…
```

退出码 0，11 个目标页 chunk 全部产出：`AdminUsers-DIVlGOLa.js` 5.89 kB、`AdminShops-Byzxm50k.js` 5.35 kB、`AdminProducts-BXizVo2A.js` 3.53 kB、`AdminEvaluations-BD3vj-io.js` 5.57 kB、`AdminRefunds-Bvo7bKGV.js` 5.79 kB、`AdminAfterSales-DCZnz4fK.js` 5.01 kB、`AdminAuditLogs-BsdBDk8D.js` 111.68 kB、`AdminConfig-CHy-sMtB.js` 4.79 kB、`AdminFinance-Cvj3iSm_.js` 6.43 kB、`AdminRisk-Bg6B8z8c.js` 5.77 kB、`ProductDetail-ClqXKzIy.js` 10.75 kB（均为未 gzip 体积；`AdminAuditLogs` 较大是因为内置了日期选择器依赖）。

构建会用 `unplugin-auto-import` / `unplugin-vue-components` 改写 `src/web/src/auto-imports.d.ts`、`src/web/src/components.d.ts`，这两个文件不在白名单内，每次构建后都已 `git checkout --` 还原，未进入提交。

说明：`pnpm build` 在本机会先尝试 `pnpm install` 并因沙箱无法打开 pnpm 全局 store 锁而告警（`ERR_PNPM_STORE_DIR_OPEN_OPERATION_LOCK`），随后 `$ vite build` 正常执行并通过。这是本机环境问题，与代码无关；CI / 正常开发机上 `pnpm build` 应可直接通过。

### 四条提 PR 前自检（针对 11 个白名单文件）

| 命令 | 结果 |
| --- | --- |
| `grep "from 'element-plus'"` | 无输出 |
| `grep "window.confirm\|window.alert"` | 无输出 |
| `grep "records"`（排除注释） | 无输出 |
| `grep "from '@/"` | 无输出（全部用相对路径） |

补充检查：`ElMessage` 无直接引用（统一走 `message.js`）；`UiTable` 用于 5 个表格页，裸 `el-table` 仅出现在任务书允许的 `AdminConfig` / `AdminFinance` / `AdminRisk`；`<h1>` 仅 `ProductDetail.vue:328` 的商品标题，改造前同位置已存在，属保留布局。

---

## 七、可访问性与交互抽查

- Tab 顺序：从页首连续 Tab 可依次到达筛选输入框、「查询」、「重置」、表格操作按钮与分页控件，焦点可见（Element Plus 默认 `:focus-visible` 描边）。
- Enter：关键词输入框内按 Enter 触发查询，实测发出 `GET /api/users?page=1&size=10&keyword=user0`，且页码重置为 1。
- Esc：确认框按 Esc 关闭等价于取消，返回 `false`，不写库（`confirmAction` 对 `action === 'close'` 返回 `false`）。
- 失败态与空态分离：失败显示 `el-result`（红色 error 图标）+ 重试按钮，空态显示表格自带 `empty-text` 或 `el-empty`，两者不会互相伪装。

---

## 八、未测项（如实列出）

1. **真实后端联调**：本机无 Docker，未在 #78 的 8 容器环境下跑过真实接口。请求字段、目标 ID、状态判断是从前端实际发出的 XHR 抓的，但服务端权限校验、事务边界、库存扣减、支付回调未验证。
2. **真实权限矩阵**：未用非管理员账号验证后端 403；前端侧只验证了「本人不可改自己角色」。
3. **浏览器兼容**：仅在 Edge（Chromium）1280×720 下验收，未测 Firefox / Safari。
4. **键鼠以外的辅助技术**：未跑屏幕阅读器，仅做了 Tab / Enter / Esc 抽查。
5. **分页大数据量**：mock 数据最多 30 条，未验证 1000+ 条时的性能与分页边界（如删末页最后一条的回退）。
6. **`pnpm build` 原样执行**：受本机 pnpm 锁目录权限限制，只验证到 `vite build` 通过（见第六节）。

---

## 九、结论

- Y01–Y11 共 11 个文件全部完成 Element Plus 改造，diff 仅限白名单文件与本人证据目录。
- 任务书要求保留的行为均已核对：配置整数边界与字符串 payload、审计日志日期字符串与分页起点、金额分转元、未知状态回退原值、卡片结构保留、`isOk` 判成功、敏感操作二次确认与防重。
- 生产构建通过，四条静态自检无输出。
- 遗留：360px 全局 Header 溢出（公共组件，不在本单范围）；真实后端联调需在 #78 环境下补验。
