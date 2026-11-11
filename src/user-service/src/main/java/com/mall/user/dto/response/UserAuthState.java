package com.mall.user.dto.response;

/** Minimal internal auth snapshot. No account, password hash or personal fields. */
public record UserAuthState(String userId, String role, String status, long tokenVersion) { }
