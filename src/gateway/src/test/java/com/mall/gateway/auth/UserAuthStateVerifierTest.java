package com.mall.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UserAuthStateVerifierTest {
    Claims claims(Long version) {
        var b=Jwts.claims().add("userId","U1").add("role","ADMIN");if(version!=null)b.add("tokenVersion",version);return b.build();
    }
    UserAuthStateVerifier verifier(String state) {
        return new UserAuthStateVerifier(WebClient.builder().exchangeFunction(req->{
            assertEquals("internal-test",req.headers().getFirst("X-Internal-Token"));
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").body(state).build());
        }),"internal-test","http://unit");
    }
    @Test void validatesVersionRoleActiveStateAndAccountIdentity() {
        String good="{\"code\":0,\"data\":{\"userId\":\"U1\",\"role\":\"ADMIN\",\"status\":\"ACTIVE\",\"tokenVersion\":2}}";
        assertTrue(verifier(good).isCurrent(claims(2L)).block());
        assertFalse(verifier(good).isCurrent(claims(1L)).block());
        assertFalse(verifier(good.replace("ACTIVE","FROZEN")).isCurrent(claims(2L)).block());
        assertFalse(verifier(good.replace("ADMIN","BUYER")).isCurrent(claims(2L)).block());
        assertFalse(verifier(good.replace("U1","U2")).isCurrent(claims(2L)).block());
    }
    @Test void missingVersionIsRejectedBeforeRemoteLookup() {
        AtomicInteger calls=new AtomicInteger();
        var v=new UserAuthStateVerifier(WebClient.builder().exchangeFunction(req->{calls.incrementAndGet();return Mono.error(new AssertionError());}),"internal-test","http://unit");
        assertFalse(v.isCurrent(claims(null)).block());assertEquals(0,calls.get());
    }
}
