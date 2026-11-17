# Issue #135 多店铺合并结算 —— 测试与验收记录

日期：2026-11-17
分支：`feature/135-multi-shop-checkout`（基于 `origin/main` 65fcbc5）
环境：Windows / OpenJDK 21.0.11 / Maven 3.9.11 / Node 24.15.0 / pnpm 12.15.0；后端测试用 H2（MySQL 模式）隔离库 + Mock 下游 Feign，不连开发数据库。

## 一、合并前门禁（issue 通用约束）

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 后端全量测试 | `mvn -B test`（src 根，7 模块） | 全绿，见下表 |
| 前端构建 | `pnpm build`（src/web） | 退出码 0 |
| 前端测试 | `node --test src/web/tests/*.mjs` | 18 通过 / 0 失败（含新增 6 条） |

后端各模块测试数（最终一轮）：

| 模块 | Tests | Failures | Errors |
| --- | --- | --- | --- |
| gateway | 22 | 0 | 0 |
| user-service | 14 | 0 | 0 |
| product-service | 22 | 0 | 0 |
| cart-service | 30 | 0 | 0 |
| order-service | 54（含新增 Issue135 12 条） | 0 | 0 |
| payment-service | 109（含新增 Issue135 6 条） | 0 | 0 |

合计 251 条，全部通过（BUILD SUCCESS）。

## 二、验收清单逐条对应

| 验收项 | 证据 |
| --- | --- |
| 两家店各选 1 件，一次支付成功，生成 1 主订单 + 2 子订单 | `Issue135MultiShopCheckoutTest.crossShopCheckoutCreatesOneMainAndTwoSubOrders`（1 main_order + 2 order，预占按店 2 次）；`Issue135MainOrderPaymentTest.createsPaymentFromMainOrderAmount` + `callbackNotifiesEverySubOrderAndConfirmsStockPerSubOrder`（支付单金额=主订单 19000 分，回调逐店 markPaid + confirmStock） |
| 子订单可分别发货、分别退款，互不影响 | `subOrdersShipAndRefundIndependently`：A 发货/退款后 B 仍 PAID，主订单仍 PAID；B 随后发货确认 COMPLETED |
| 主订单取消时子订单都释放库存，stock_reservation 状态正确 | `cancelMainCancelsAllUnpaidSubOrdersAndReleasesInventory`：两子订单 CANCELLED 且 inventory_released=1，release 按店各 1 次；重复取消不重复释放。预占记录以子订单号为键（`ReserveStockRequestDTO.orderId`=子订单ID），释放走同一键，product-service 预占状态机随之 RELEASED |
| 只选一家店时不产生多余层级 | `singleShopCheckoutCreatesNoMainOrder`：mainOrderId/mainOrderNo/subOrders 均为 null，main_order 表 0 行 |
| 历史单店订单（main_order_id 为 null）列表与详情仍能打开 | `legacySingleShopOrderDetailAndListStillWork`：直接插入 main_order_id=NULL 的历史订单，detail/list 正常；前端 `multi-shop-checkout.test.mjs` 覆盖 null 主订单不聚合 |
| 同一购物车重复提交只产生一个主订单（沿用 order_idempotency） | `repeatedSubmitWithSameKeyReturnsSameMainOrder`（串行重放）+ `concurrentDuplicateKeyCreatesOnlyOneMainOrder`（并发） |

## 三、并发实测（issue 要求贴并发数与实测结果）

用例：`Issue135MultiShopCheckoutTest.concurrentDuplicateKeyCreatesOnlyOneMainOrder`

- 并发数：**30 个并发请求**（15 线程池，CountDownLatch 同时放行），同一 Idempotency-Key、同一跨店购物车（2 店）。
- 实测结果：30 个响应全部成功返回，`mainOrderId` 去重后 **恰好 1 个**；`main_order` 未删除行数 = 1，`order` 未删除行数 = 2；`productClient.reserve` 总计 **恰好 2 次**（每店 1 次，无重复预占）。
- 机制：沿用 `order_idempotency` 的 RESERVING 执行权抢占（条件 UPDATE），跨店时执行权保持到全部子订单预占成功才落 SUCCESS；等待方 `awaitOrderCreated` 重放首个结果。
- 回归：`Issue48OrderConcurrencyTest`（50 并发重复幂等键、买家取消 vs 超时取消竞态）在同一轮全量测试中保持全绿。

## 四、其他新增用例

order-service（`Issue135MultiShopCheckoutTest`，12 条）：
预览按店分组且金额服务端计算、主订单全部子订单 PAID 才置 PAID（含重复回调幂等）、
取消主订单拒绝非买家/已支付主订单、主订单超时整体取消（重复扫描幂等）、
第二家店预占失败时整单回滚（软删主订单+全部子订单、释放已预占店、幂等键退回 FAILED 可重试）。

payment-service（`Issue135MainOrderPaymentTest`，6 条）：
按主订单金额创建支付单（seller_id=null）、同幂等键复用不重复查单、
拒绝已支付主订单、拒绝他人主订单、回调逐子订单通知+按店确认库存+写 reconciled_at、
主订单已取消时整笔自动退款（不通知子订单、退款单 order_id 保留主订单ID）。

前端（`web/tests/multi-shop-checkout.test.mjs`，6 条）：
买家列表按主订单聚合（金额求和、件数、状态保守取值）、历史单店订单不聚合、
卖家视角不聚合、任一子订单未支付则主订单行待支付、全部取消才显示已取消、空输入安全。

## 五、已知边界

- 运费/优惠计价链不在本条范围（#136）：预览与下单 `payAmount = totalAmount`，契约 5.9 已注明。
- 跨店支付单 `seller_id` 为 null；按店售后退款通过卖家归属反查子订单落通知，整笔自动退款只记录资金退回（契约 5.9）。
- 老库升级需执行 `src/order-service/src/main/resources/migration-135-main-order.sql`（幂等，information_schema 判断后动态 ALTER）。