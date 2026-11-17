package com.mall.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.user.client.ProductClient;
import com.mall.user.client.dto.ProductBriefDTO;
import com.mall.user.common.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "user.internal-token=message-test-internal-token")
@AutoConfigureMockMvc
class MessageIntegrationTest {
    private static final String BUYER = "MSG-B", SELLER = "MSG-S", OTHER_SELLER = "MSG-T";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean ProductClient products;

    @BeforeEach
    void fixtures() {
        // These fixtures are reserved for this class; other integration suites keep their users.
        if (jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='CONVERSATION'", Integer.class) > 0) {
            jdbc.update("DELETE FROM message WHERE conversation_id IN (SELECT conversation_id FROM conversation WHERE buyer_id LIKE 'MSG-%')");
            jdbc.update("DELETE FROM conversation WHERE buyer_id LIKE 'MSG-%'");
        }
        jdbc.update("DELETE FROM shop WHERE seller_id LIKE 'MSG-%'");
        jdbc.update("DELETE FROM `user` WHERE user_id LIKE 'MSG-%'");
        for (String[] user : new String[][]{{BUYER, "BUYER", "ACTIVE"}, {SELLER, "SELLER", "ACTIVE"},
                {OTHER_SELLER, "SELLER", "ACTIVE"}, {"MSG-X", "BUYER", "ACTIVE"},
                {"MSG-A", "ADMIN", "ACTIVE"}, {"MSG-F", "BUYER", "FROZEN"}}) {
            jdbc.update("INSERT INTO `user`(user_id,account,password_hash,nickname,role,status) VALUES(?,?, 'unused',?,?,?)",
                    user[0], user[0], user[0] + "昵称", user[1], user[2]);
        }
        jdbc.update("INSERT INTO shop(shop_id,seller_id,name) VALUES('MSG-SH',?,'卖家店铺'),('MSG-TH',?,'另一店铺')", SELLER, OTHER_SELLER);
        when(products.batchGet(anyString())).thenAnswer(invocation -> {
            List<ProductBriefDTO> result = new ArrayList<>();
            for (String id : invocation.getArgument(0, String.class).split(",")) {
                if (id.equals("missing")) continue;
                ProductBriefDTO product = new ProductBriefDTO();
                product.setProductId(id);
                product.setSellerId(id.equals("wrong-seller") ? OTHER_SELLER : SELLER);
                product.setTitle("测试商品"); product.setMainImage("/test.png");
                product.setPrice(1200L); product.setStatus("ON_SALE");
                result.add(product);
            }
            return Result.success(result);
        });
    }

    @Test
    void buyerAndSellerExchangeMessagesAndReadOnlyReceivedMessages() throws Exception {
        JsonNode sent = send(BUYER, Map.of("sellerId", SELLER, "contextProductId", "p1", "content", "  还有货吗  "));
        assertEquals(0, sent.path("code").asInt(), sent.toString());
        JsonNode first = sent.path("data");
        String id = first.path("conversationId").asText();
        assertFalse(id.isBlank());
        assertEquals(BUYER, first.path("senderId").asText());
        assertEquals("还有货吗", first.path("content").asText());
        assertEquals("p1", first.path("contextProductId").asText());
        assertTrue(first.path("readAt").isNull());
        assertFalse(first.path("messageId").asText().isBlank());
        assertFalse(first.path("createdAt").asText().isBlank());

        JsonNode conversation = conversation(SELLER, id);
        assertEquals(1, conversation.path("unreadCount").asInt());
        assertEquals(1, conversation.path("sellerUnread").asInt());
        assertEquals(0, conversation.path("buyerUnread").asInt());
        assertEquals(BUYER + "昵称", conversation.path("buyerName").asText());
        assertEquals(SELLER + "昵称", conversation.path("sellerName").asText());
        assertEquals("还有货吗", conversation.path("lastPreview").asText());

        assertEquals(0, send(SELLER, Map.of("conversationId", id, "content", "有货" )).path("code").asInt());
        assertEquals(1, conversation(BUYER, id).path("unreadCount").asInt());
        assertEquals(0, call(put("/messages/conversations/" + id + "/read"), SELLER).path("code").asInt());
        JsonNode messages = call(get("/messages/conversations/" + id), BUYER).path("data");
        assertEquals(30, messages.path("size").asInt());
        assertEquals(2, messages.path("total").asInt());
        assertEquals("还有货吗", messages.path("list").get(0).path("content").asText());
        assertFalse(messages.path("list").get(0).path("readAt").isNull());
        assertTrue(messages.path("list").get(1).path("readAt").isNull());
        assertEquals(0, conversation(SELLER, id).path("unreadCount").asInt());
        assertEquals(1, conversation(BUYER, id).path("unreadCount").asInt());
        assertEquals(0, call(put("/messages/conversations/" + id + "/read"), BUYER).path("code").asInt());
        assertEquals(0, conversation(BUYER, id).path("unreadCount").asInt());
    }

    @Test
    void allMessageOperationsRejectOutsidersAndAdministrators() throws Exception {
        String id = successfulSend(BUYER, Map.of("sellerId", SELLER, "content", "hello"));
        for (String actor : List.of("MSG-X", "MSG-A")) {
            assertCode(10003, call(get("/messages/conversations/" + id), actor));
            assertCode(10003, call(put("/messages/conversations/" + id + "/read"), actor));
            assertCode(10003, send(actor, Map.of("conversationId", id, "content", "intrusion")));
        }
        assertCode(10003, call(get("/messages/conversations"), "MSG-A"));
        assertCode(10003, send("MSG-A", Map.of("sellerId", SELLER, "content", "admin")));
        assertCode(10001, send(SELLER, Map.of("sellerId", SELLER, "content", "self")));
        assertCode(20009, call(get("/messages/conversations/absent"), BUYER));
        assertCode(20009, call(put("/messages/conversations/absent/read"), BUYER));
        assertCode(20009, send(BUYER, Map.of("conversationId", "absent", "content", "missing")));
    }

    @Test
    void targetMustBeActiveSellerWithActiveShopAndBothUsersMustBeActive() throws Exception {
        assertCode(20007, send(BUYER, Map.of("sellerId", "MSG-X", "content", "buyer target")));
        assertCode(10003, send("MSG-F", Map.of("sellerId", SELLER, "content", "frozen")));
        String id = successfulSend(BUYER, Map.of("sellerId", SELLER, "content", "before suspend"));
        jdbc.update("UPDATE shop SET status='SUSPENDED' WHERE seller_id=?", SELLER);
        assertCode(20007, send(BUYER, Map.of("sellerId", SELLER, "content", "suspended")));
        assertCode(20007, send(SELLER, Map.of("conversationId", id, "content", "reply")));
        jdbc.update("UPDATE shop SET status='ACTIVE' WHERE seller_id=?", SELLER);
        jdbc.update("UPDATE `user` SET status='FROZEN' WHERE user_id=?", BUYER);
        assertCode(10003, send(SELLER, Map.of("conversationId", id, "content", "frozen recipient")));
    }

    @Test
    void contentIsTrimmedAndBoundedAndFirstSendRequiresSeller() throws Exception {
        for (String content : List.of("", " \n\t ", "字".repeat(501))) {
            JsonNode result = send(BUYER, Map.of("sellerId", SELLER, "content", content));
            assertCode(10001, result);
            assertTrue(result.path("message").asText().matches(".*[\\p{IsHan}].*"));
        }
        assertCode(10001, send(BUYER, Map.of("sellerId", SELLER)));
        assertCode(10001, send(BUYER, Map.of("content", "missing seller")));
        JsonNode result = send(BUYER, Map.of("sellerId", SELLER, "content", " " + "字".repeat(500) + " ", "senderId", "MSG-X"));
        assertCode(0, result);
        assertEquals(500, result.path("data").path("content").asText().length());
        assertEquals(BUYER, result.path("data").path("senderId").asText());
    }

    @Test
    void productContextMustBelongToConversationSellerAndMultipleProductsReuseConversation() throws Exception {
        assertCode(10001, send(BUYER, Map.of("sellerId", SELLER, "contextProductId", "wrong-seller", "content", "bad")));
        assertCode(10001, send(BUYER, Map.of("sellerId", SELLER, "contextProductId", "missing", "content", "bad")));
        String id = successfulSend(BUYER, Map.of("sellerId", SELLER, "contextProductId", "p1", "content", "first"));
        assertEquals(id, successfulSend(BUYER, Map.of("sellerId", SELLER, "contextProductId", "p2", "content", "second")));
        assertCode(10001, send(SELLER, Map.of("conversationId", id, "contextProductId", "wrong-seller", "content", "bad")));
        assertEquals(id, successfulSend(SELLER, Map.of("conversationId", id, "contextProductId", "p2", "content", "reply")));
        assertEquals(3, call(get("/messages/conversations/" + id), BUYER).path("data").path("total").asInt());
    }

    @Test
    void sellerShoppingAsBuyerListsBothSidesAndOnlyParticipatingConversations() throws Exception {
        String asSeller = successfulSend(BUYER, Map.of("sellerId", SELLER, "content", "sell"));
        String asBuyer = successfulSend(SELLER, Map.of("sellerId", OTHER_SELLER, "content", "buy"));
        successfulSend("MSG-X", Map.of("sellerId", OTHER_SELLER, "content", "private"));
        JsonNode list = call(get("/messages/conversations"), SELLER).path("data");
        assertEquals(2, list.path("total").asInt());
        assertEquals(20, list.path("size").asInt());
        assertEquals(List.of(asBuyer, asSeller), List.of(list.path("list").get(0).path("conversationId").asText(),
                list.path("list").get(1).path("conversationId").asText()));
        assertEquals(1, conversation(SELLER, asSeller).path("unreadCount").asInt());
        assertEquals(0, conversation(SELLER, asBuyer).path("unreadCount").asInt());
    }

    @Test
    void paginationUsesChronologicalOrderAndIdTieBreaker() throws Exception {
        String id = successfulSend(BUYER, Map.of("sellerId", SELLER, "content", "one"));
        successfulSend(BUYER, Map.of("conversationId", id, "content", "two"));
        successfulSend(BUYER, Map.of("conversationId", id, "content", "three"));
        JsonNode chronological = call(get("/messages/conversations/" + id).param("size", "2"), BUYER).path("data");
        assertEquals("one", chronological.path("list").get(0).path("content").asText());
        assertEquals("two", chronological.path("list").get(1).path("content").asText());
        assertEquals("three", call(get("/messages/conversations/" + id).param("size", "2").param("page", "2"), BUYER)
                .path("data").path("list").get(0).path("content").asText());
        jdbc.update("UPDATE message SET created_at='2026-01-01 00:00:00' WHERE conversation_id=?", id);
        List<String> expected = jdbc.queryForList("SELECT message_id FROM message WHERE conversation_id=? ORDER BY message_id", String.class, id);
        List<String> actual = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            JsonNode data = call(get("/messages/conversations/" + id).param("size", "1").param("page", "" + page), BUYER).path("data");
            assertEquals(3, data.path("total").asInt());
            actual.add(data.path("list").get(0).path("messageId").asText());
        }
        assertEquals(expected, actual);
    }

    @Test
    void concurrentFirstSendsReuseOneConversationWithoutLosingMessagesOrUnread() throws Exception {
        List<Callable<JsonNode>> sends = new ArrayList<>();
        for (int i = 0; i < 12; i++) sends.add(() -> send(BUYER, Map.of("sellerId", SELLER, "content", "parallel")));
        List<JsonNode> results = parallel(sends);
        String id = results.get(0).path("data").path("conversationId").asText();
        for (JsonNode result : results) { assertCode(0, result); assertEquals(id, result.path("data").path("conversationId").asText()); }
        assertEquals(12, conversation(SELLER, id).path("unreadCount").asInt());
        assertEquals(12, call(get("/messages/conversations/" + id), BUYER).path("data").path("total").asInt());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM conversation WHERE buyer_id=? AND seller_id=?", Long.class, BUYER, SELLER));
    }

    @Test
    void concurrentSendAndReadKeepCounterEqualToUnreadRows() throws Exception {
        String id = successfulSend(BUYER, Map.of("sellerId", SELLER, "content", "initial"));
        for (int round = 0; round < 4; round++) {
            List<Callable<JsonNode>> operations = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                operations.add(() -> send(BUYER, Map.of("conversationId", id, "content", "racing")));
                operations.add(() -> call(put("/messages/conversations/" + id + "/read"), SELLER));
            }
            for (JsonNode result : parallel(operations)) assertCode(0, result);
            int unread = jdbc.queryForObject("SELECT COUNT(*) FROM message WHERE conversation_id=? AND sender_id=? AND read_at IS NULL", Integer.class, id, BUYER);
            assertEquals(unread, conversation(SELLER, id).path("unreadCount").asInt());
        }
        assertCode(0, call(put("/messages/conversations/" + id + "/read"), SELLER));
        assertEquals(0, conversation(SELLER, id).path("unreadCount").asInt());
    }

    @Test
    void untrustedIdentityHeadersAreRejectedBeforeMessageEndpoints() throws Exception {
        mvc.perform(get("/messages/conversations").header("X-User-Id", BUYER)).andExpect(status().isForbidden());
        assertCode(10002, call(get("/messages/conversations"), null));
    }

    private JsonNode send(String actor, Map<String, ?> body) throws Exception {
        return call(post("/messages").contentType("application/json").content(json.writeValueAsBytes(body)), actor);
    }
    private String successfulSend(String actor, Map<String, ?> body) throws Exception {
        JsonNode result = send(actor, body); assertCode(0, result);
        return result.path("data").path("conversationId").asText();
    }
    private JsonNode call(MockHttpServletRequestBuilder request, String actor) throws Exception {
        request.header("X-Internal-Token", "message-test-internal-token");
        if (actor != null) request.header("X-User-Id", actor).header("X-User-Role", actor.equals(SELLER) ? "SELLER" : "BUYER");
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode conversation(String actor, String id) throws Exception {
        JsonNode result = call(get("/messages/conversations"), actor); assertCode(0, result);
        for (JsonNode item : result.path("data").path("list")) if (id.equals(item.path("conversationId").asText())) return item;
        fail("Missing conversation " + id + " in " + result); return null;
    }
    private static void assertCode(int code, JsonNode result) { assertEquals(code, result.path("code").asInt(), result.toString()); }
    private static List<JsonNode> parallel(List<Callable<JsonNode>> operations) throws Exception {
        var executor = Executors.newFixedThreadPool(operations.size());
        CountDownLatch ready = new CountDownLatch(operations.size()), start = new CountDownLatch(1);
        try {
            var futures = operations.stream().map(operation -> executor.submit(() -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return operation.call();
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            List<JsonNode> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); executor.shutdownNow(); }
    }
}
