package com.mall.user.service;

import com.mall.user.common.Role;
import com.mall.user.dto.request.*;
import com.mall.user.mapper.UserMapper;
import com.mall.user.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TokenRevocationIntegrationTest {
    @Autowired UserService users;
    @Autowired UserMapper mapper;
    @Autowired JwtUtil jwt;
    @Autowired PlatformTransactionManager transactions;

    String register(String prefix) {
        String name = prefix + Long.toUnsignedString(System.nanoTime(),36);
        RegisterRequest r = new RegisterRequest(); r.setAccount(name); r.setNickname(name); r.setPassword("test-password");
        return users.register(r);
    }
    String login(String id) {
        LoginRequest r = new LoginRequest();r.setAccount(mapper.selectById(id).getAccount());r.setPassword("test-password");
        return users.login(r).getToken();
    }
    String admin() { String id=register("a");mapper.updateRoleById(id,"ADMIN","TEST");return id; }
    long version(String id) { return mapper.selectAuthState(id).tokenVersion(); }

    @Test void freezeAndUnfreezeNeverRevivesTheOldToken() {
        String admin=admin(), id=register("f");
        String old=login(id);long first=((Number)jwt.parseToken(old).get("tokenVersion")).longValue();
        UpdateStatusRequest req=new UpdateStatusRequest();req.setStatus("FROZEN");users.updateStatus(admin,id,req);
        assertEquals("FROZEN",mapper.selectAuthState(id).status());assertTrue(version(id)>first);
        req.setStatus("ACTIVE");users.updateStatus(admin,id,req);
        assertNotEquals(first,version(id));
        assertEquals(version(id),((Number)jwt.parseToken(login(id)).get("tokenVersion")).longValue());
    }
    @Test void roleRoundTripRevokesOldPermissionsButProfileEditDoesNot() {
        String admin=admin(),id=register("r");
        RoleRequest role=new RoleRequest();role.setRole("ADMIN");users.updateRole(admin,id,role);
        String old=login(id);long v=version(id);
        role.setRole("BUYER");users.updateRole(admin,id,role);
        assertEquals("BUYER",mapper.selectAuthState(id).role());assertTrue(version(id)>v);
        role.setRole("ADMIN");users.updateRole(admin,id,role);
        assertNotEquals(((Number)jwt.parseToken(old).get("tokenVersion")).longValue(),version(id));
        long now=version(id);UpdateProfileRequest profile=new UpdateProfileRequest();profile.setPhone("13800000000");
        users.updateProfile(id,profile);assertEquals(now,version(id));
    }
    @Test void failedTransactionRollsBackRoleAndRevocationTogether() {
        String admin=admin(), id=register("b");long before=version(id);
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            RoleRequest req=new RoleRequest();req.setRole("SELLER");users.updateRole(admin,id,req);status.setRollbackOnly();
        });
        assertEquals(Role.BUYER,mapper.selectById(id).getRole());assertEquals(before,version(id));
    }
}
