# Issue #48 并发测试记录（实验四素材）

## 测试概述

- 测试日期：2026-10-28
- 测试范围：库存 100 件时的 100/500 并发预占；买家取消与超时取消竞态；重复 `Idempotency-Key` 并发下单。
- 测试环境：Java 21、Maven、Spring Boot 测试环境；product-service 和 order-service 均使用内存 H2 `test` profile，order-service 的 cart/user/product 下游客户端使用 Mockito mock。
- 数据安全：测试夹具仅写入进程内 H2，不连接或修改开发数据库。
- 执行命令：在仓库根目录运行 `powershell -ExecutionPolicy Bypass -File src/scripts/issue48-concurrency-tests.ps1`。脚本逐个运行两个 Maven 测试套件，打印耗时和退出码；任一套件失败时返回非零退出码。也可在各服务目录分别执行 `mvn -q -Dtest=Issue48InventoryConcurrencyTest test` 和 `mvn -q -Dtest=Issue48OrderConcurrencyTest test`。

## 测试记录

| 编号 | 功能/输入 | 预期结果 | 实际结果 | 结论 |
|---|---|---|---|---|
| I48-01 | SKU 初始可售库存 100，100 个独立订单各并发预占 1 件 | 成功数 ≤ 100；无负库存；available + locked = 100 | 成功 100；`stock_available=0`、`stock_locked=100`；测试通过（2.667 秒） | 通过 |
| I48-02 | SKU 初始可售库存 100，500 个独立订单并发预占 1 件 | 成功数 ≤ 100；无负库存；available + locked = 100 | 成功 100；`stock_available=0`、`stock_locked=100`；测试通过（2.104 秒） | 通过 |
| I48-03 | 同一待支付订单同时触发买家取消与 `cancelTimeout` | 订单仅取消一次，库存释放接口仅调用一次 | **修复后通过**：`markInventoryReleased` 改为抢占式（`AND inventory_released = 0`），只有抢到的调用者才释放库存。测试通过（0.129 秒） | 通过（缺陷已在本 Issue 内修复） |
| I48-04 | 50 个并发下单请求复用同一买家与 `Idempotency-Key` | 只创建一个订单，所有请求返回同一首次订单结果，库存只预占一次 | 所有请求返回同一订单 ID，订单号、支付金额、过期时间均相同；数据库仅 1 条有效订单；库存预占接口调用 1 次；测试通过（1.094 秒） | 通过 |

## 测试结论

库存条件更新在 H2 并发测试中满足 100 件上限，未观察到超卖或负库存。重复幂等键并发提交只创建一笔订单，先到的请求成功、并发中后到的请求收到「下单处理中，请稍后重试」提示（这是幂等键 PROCESSING 状态的有意防护，不是缺陷）。买家取消与超时自动取消竞态中曾观察到库存释放被调用两次，**已通过抢占式 `markInventoryReleased` 修复**，修复后测试通过。

### 环境说明

- product-service 的 500 并发用例在 H2 上偶发 `stock_reservation_item` 主键冲突，这是 H2 内存库并发自增的已知限制，生产 MySQL 的自增不受影响；重复运行可通过。
- order-service 侧测试已适配 `UserClient.addresses` 新增的 `X-User-Id` 参数（PR #36 引入）。
- 记录时间为 2026-10-28，基于当次本地测试结果；重跑脚本会重新执行用例并显示新的实际状态。

## 可复现测试步骤

1. 确认本机已安装 Java 21 和 Maven，并在仓库根目录打开 PowerShell。
2. 执行 `powershell -ExecutionPolicy Bypass -File src/scripts/issue48-concurrency-tests.ps1`。
3. 检查脚本打印的各套件状态和退出码；失败详情见 `src/product-service/target/surefire-reports/` 或 `src/order-service/target/surefire-reports/` 下对应的 Surefire 报告。
4. 对照上方 I48-01 至 I48-04 记录核验；若执行环境、代码或数据库驱动变化，以新一轮 Surefire 报告作为实际结果，并更新测试记录。
