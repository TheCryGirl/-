# #145 Flash Sale Checkout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development or executing-plans to implement this plan task by task. Steps use checkbox syntax for tracking.

**Goal:** Turn the flash-sale purchase button into a real, payable order without changing ordinary checkout.

**Architecture:** Product-service owns activity quotes and atomically reserves activity and physical stock. Order-service persists the flash-sale quote on the ordinary order and uses the existing payment, cancellation, and refund lifecycle. Web reuses the existing checkout screen.

**Tech Stack:** Java 17, Spring Boot, MyBatis, MySQL/H2, Vue, Node test runner.

**Spec:** `docs/superpowers/specs/2026-11-18-issue-145-flash-checkout-design.md`

## Global Constraints

One activity and SKU per checkout. No coupon or promotion stacking; use existing freight. No real-money or live demo data mutations. Preserve historical standalone reservation records and all report files. Stop the legacy standalone route from writing. Publish only after all checks pass.

### 1. Product-side quote and inventory

- [ ] Extend `src/product-service/src/test/java/com/mall/product/FlashSaleIntegrationTest.java` with fixtures for read-only quote, a linked formal reservation, repeat of the same order, a second order by the same user, failure of SKU stock reservation, cancel/expiry, and confirmed refund. Assert actual stock values and uniqueness. Red tests before production changes.
- [ ] Add internal `GET /products/internal/flash-sales/{saleId}/quote` using ingress-token protection. Return authoritative productId, skuId, and unitPrice in cents after checking the activity and requested quantity. Move authoritative write to the normal reserve endpoint: optional `flashSale` metadata uses `saleId`, `buyerId`, `unitPrice`; reject partial metadata, mixed SKUs, ineligible buyer or changed price. Persist `order_id` in the activity row with a nullable unique key; old rows remain valid. Stop writes on the old public reserve route. Run focused product tests.
- [ ] Update `StockService` reservation and release transactions: same-order replay checks payload and never deducts again, a new reserve verifies and conditionally deducts both stocks in one transaction; release restores activity stock and deletes only the linked eligibility row; confirm and refund do not restore the activity. Use MySQL additive migration and H2 fixture schema. Test restocking and limits alongside existing SKU tests.

### 2. Formal order and payment

- [ ] In `order-service` integration tests, preview quantity 1 at 399 cents plus 100 freight = 499 cents. Assert no stock call before submit, formal order `payAmount=499`, detail snapshot at 399, identical retry returns the same order, and stale price or payable amount does not reserve. Assert normal orders use their original price and paths. Extend `payment-service` tests to prove MOCK payment takes the order total from the trusted order response.
- [ ] Extend preview/create to accept `flashSaleId`, `quantity`, and for submit `expectedUnitPrice` + `expectedPayAmount`. Keep existing cart API mutually exclusive and validate both branches. Resolve quote via ProductClient, reuse order snapshots and freight, bypass promotions/coupons, persist activity ID in order items and use it on recovery. Propagate formal reservation metadata from both fresh and recovered orders. Ensure payment-visible ready state for new flash-sale orders only after inventory is reserved; verify timeout/compensation continues to use the usual linked release. Add order migration and matching H2 columns. Run targeted order/payment tests.

### 3. Frontend and documentation

- [ ] In `src/web/src/views/ProductList.vue`, route authenticated flash activity to existing `OrderConfirm.vue` with the activity ID rather than pre-reserving. In checkout, fetch server preview for the selected address without cart, display its SKU and freight, hide coupons, and block submit if preview unavailable or stale. Submit the displayed expected unit/total and use the existing order payment route. Add Node behavior tests covering isolated payloads, stale quote handling, and UI navigation.
- [ ] Explain the sunset of the independent reserve route, formal checkout payload, eligibility error cases, and migration order in `docs/05-接口契约.md` and `docs/演示一键启动.md`. Do not rewrite historical evidence.

### 4. Release and issue

- [ ] Run `mvn -B verify` (if running live jars lock `target`, run in an isolated build directory without stopping unapproved processes), `node --test tests/*.mjs` and `pnpm build` from their module roots. Check the full diff, new SQL files, normal checkout regression and migration instructions. Verify no report, backup, token or demo data staged.
- [ ] Commit the validated changes, fast-forward local main, push without force, verify GitHub main matches the exact verified commit, then close #145 with the release ID and test evidence. Leave it open on any failed release check.
