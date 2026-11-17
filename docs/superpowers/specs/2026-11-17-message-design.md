# 买卖双方站内消息（#106）

依据：https://github.com/TheCryGirl/-/issues/106 。用户已要求按该需求完成开发并提交，使用本地身份。

## 目标与范围

商品详情发起文字咨询，买卖双方查看会话、分页阅读、回复和清除未读。沿用 user-service / mall_user、同步 Feign、现有页面与鉴权；不增加服务、队列、Redis、全局依赖，不更改订单支付售后。

## 方案

采用需求给定的 conversation/message 表和四个 /api/messages 接口。独立微服务、WebSocket 不符合本需求边界。唯一买卖双方索引复用跨商品会话；事务中锁定会话行，使发送与已读更新保持计数一致。分页使用时间和 ID 作稳定排序。

首次发送提供 sellerId，可携带 contextProductId；商品上下文必须属于目标卖家。后续双方用 conversationId 回复。发送人从可信登录身份取得；拒绝自己、第三方、管理员、非卖家、冻结用户或停用店铺。需求中“没买过”解释为允许交易前咨询，不查询购买记录，符合“不改动订单链路”。正文 trim 后 1–500 字，非法返回 10001 中文提示；新增 20009/20010 常量。

## 接口数据

GET /api/messages/conversations?page=1&size=20；GET /api/messages/conversations/{id}?page=1&size=30；POST /api/messages；PUT /api/messages/conversations/{id}/read。网关 StripPrefix=1。

统一 Result 包装。分页使用 PageResponse {list,total,page,size}。
会话项：conversationId,buyerId,sellerId,buyerName,sellerName,lastMessageAt,lastPreview,buyerUnread,sellerUnread,unreadCount。
消息项与发送成功：messageId,conversationId,senderId,content,contextProductId,readAt,createdAt。
PUT read 返回成功空数据。不存在会话 20009，非参与人/管理员 10003。

## 页面

/messages 与 /seller/messages 共用 Messages.vue，左右会话/正文，窄屏上下布局；分别显示加载、空态和错误并可重试。商品按钮跳转 /messages?sellerId=...&contextProductId=...；发送成功后在地址保留 conversationId。用户和卖家菜单加入入口；复用现有 isOwnProduct 隐藏本人商品按钮。商品摘要通过已有 batch 接口取，失败时保留文字消息。

## 验证

H2 + HTTP 集成覆盖往返、角色权限、唯一会话、未读、正文边界、稳定分页、并发读写与入口令牌。全量 mvn -B test、前端现有测试与 pnpm build。使用两个本机测试账号完成实际 HTTP 往返，记录实际环境和结果，不编造全栈运行。
