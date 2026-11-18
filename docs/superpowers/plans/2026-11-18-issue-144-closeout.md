# Issue #144 Closeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development or executing-plans to implement task by task. Keep tests and production changes together.

**Goal:** Finish merchant publication eligibility and auditable local merchant settlement.

**Architecture:** User-service owns eligibility; product-service checks it before making a new product public. Order-service stores checkout-time category snapshots; payment-service owns monthly settlement, frozen payouts and refund adjustments. Keep current service boundaries and local MySQL deployment.

**Tech Stack:** Spring Boot 3, Java 17, MyBatis, MySQL 8, H2, Vue 3, Node test runner.

**Spec:** `docs/superpowers/specs/2026-11-18-issue-144-closeout-design.md`

## Global constraints

Store monetary values as integer cents; re-use authenticated service calls; no live money movement; no report or unrelated data changes. Apply additive, idempotent migrations to a backed-up database. Push the validated batch together.

### Task 1: Merchant eligibility

- [ ] Extend `src/user-service/src/test/java/com/mall/user/service/ShopApplyAuditIntegrationTest.java`: pending, suspended, or approved with `depositAmount=0` cannot publish; positive deposit enables publishing on an active approved shop; deposit registration does not reactivate a suspended shop. Preserve public-field redaction and existing-shop order access.
- [ ] Extend `src/product-service/src/test/java/com/mall/product/ProductServiceTest.java` and `ProductLifecycleRegressionTest.java`: reject create/re-list/approval without eligibility, existing on-sale item can be taken down, successful approved fixture still works. `src/web/tests/shop-apply.test.mjs` verifies separate deposit and suspended explanations. Run focused Maven tests and `node --test tests/shop-apply.test.mjs` and confirm the changed behavior fails first.
- [ ] Add `GET /internal/shops/{sellerId}/publication` returning `{publishable, reason}` in user-service; bind it through `ShopClient`, deny writes on missing/failed/ineligible responses. Separate eligibility from `getBySellerId`'s trading status; only restore suspended catalog entries when the store is eligible. Update admin deposit registration, seller actions, and focused page tests.
- [ ] Re-run all relevant module and frontend tests. Verify basic shop audit and visibility behavior did not regress.

### Task 2: Category snapshots and commission

- [ ] Extend `src/order-service/src/test/java/com/mall/order/Issue135MultiShopCheckoutTest.java` to prove line category is persisted; extend `src/payment-service/src/test/java/com/mall/payment/service/SettlementIntegrationTest.java` using a two-line 1:3 order, 399 cents paid, 5% and 10% category rates (35 cents commission), store-rate override and uncategorized platform fallback. Assert amount allocation sums to the paid amount and later repricing uses frozen rates.
- [ ] Add nullable `mall_order.order_item.category_id_snapshot`, bind `OrderItem` and `OrderItemMapper.xml`, write from `ProductDetailDTO.categoryId`, update MySQL and H2 schemas. Add integer allocation/commission logic under `payment/service`, with stable SKU/line ordering, store/category/platform rule precedence, and persist line weights/rates on settlement detail. Expose admin-only list/save rates through `SettlementController`, validate scope, target, and rate `0..10000`, and provide management in `AdminSettlements.vue`.
- [ ] Run order and payment tests plus the admin permission tests; verify old snapshots fall back without mutation and that changing a rule does not change already settled orders.

### Task 3: Refund and payout ledger

- [ ] Extend `SettlementIntegrationTest.java`: successful partial refund updates an unpaid detail, full refund produces zero, pending/rejected refunds do nothing, paid detail remains unchanged, adjustment is applied once to the next available period, excess carries, and concurrent generation keeps totals consistent. Add expired-month, stale expected amount, unique payout reference, and permission tests. A payout record is evidence of an external manual act only; the application never initiates it.
- [ ] Modify `SettlementService.java`, `OrderCompletionMapper.xml`, `SettlementMapper.xml`, and related types. Persist cumulative refund watermark and stable completion period. Add separate adjustment rows keyed by original order and cumulative refunded amount, and unique payout rows keyed by settlement and reference. Lock settlement rows consistently across generation and mark-paid; reconcile already-billed orders as well as new candidates and rebuild the statement from persisted details/adjustments. Preserve paid data. Require `expectedNetAmount` and `payoutReference` for status writes, return a business error if the month is open or the recomputed amount differs, and expose references in response and UI. Verify retry is safe after a stale-amount rejection.
- [ ] Run the ledger regression tests, including overlapping operations and pre-existing paid records with no snapshots; review SQL and calculate fixture amounts independently.

### Task 4: Run, release and maintain GitHub

- [ ] Review the staged range against `01cf471`; preserve report files. Run Maven full verification, Node tests and production bundle. Back up affected MySQL schemas, run the additive migrations, then start the local stack with approved process handling and interactive credentials.
- [ ] Verify six health endpoints, frontend proxy, and settlement and onboarding flows against isolated test data. Record counts, exit codes and smoke-test scope. Push the validated batch to the intended main and confirm the exact remote SHA.
- [ ] Update current verification and migration guidance in `README.md`, `src/README.md`, and `docs/05-接口契约.md`, without rewriting historical verification. Recheck issue #144 acceptance and #145 tracking; keep any unverified work open. Verify branch ancestry before considering deletion, and use only authorized remote changes.
